package dev.ikm.tinkar.reasoner.service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.entity.*;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.primitive.IntObjectMaps;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.map.primitive.ImmutableIntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.api.set.primitive.ImmutableIntSet;
import org.eclipse.collections.api.set.primitive.IntSet;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.ikm.tinkar.common.id.IntIdSet;
import dev.ikm.tinkar.common.id.IntIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.common.sets.ConcurrentHashSet;
import dev.ikm.tinkar.common.util.time.MultipleEndpointTimer;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.view.ViewCoordinateRecord;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpression;
import dev.ikm.tinkar.entity.graph.isomorphic.IsomorphicResults;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.terms.State;
import dev.ikm.tinkar.terms.TinkarTerm;

import static dev.ikm.tinkar.common.service.PrimitiveData.SCOPED_PATTERN_PUBLICID_FOR_NID;
import static dev.ikm.tinkar.common.service.PrimitiveData.calculateOptimalChunkSize;

public class InferredResultsWriter {

	private static final Logger LOG = LoggerFactory.getLogger(InferredResultsWriter.class);

	// Define scoped values for context propagation
	private static final ScopedValue<StampCalculator> STAMP_CALCULATOR = ScopedValue.newInstance();
	private static final ScopedValue<IntSet> CONCEPTS_TO_UPDATE = ScopedValue.newInstance();
	private static final ScopedValue<AtomicInteger> PROCESSED_COUNT = ScopedValue.newInstance();
	private static final ScopedValue<Integer> TOTAL_COUNT = ScopedValue.newInstance();

	private ReasonerService rs;

	private Transaction updateTransaction;

	private int updateStampNid;

	private PatternEntity<PatternEntityVersion> inferredPattern;
	private PatternEntity<PatternEntityVersion> inferredNavigationPattern;

	private MultipleEndpointTimer<IsomorphicResults.EndPoints> multipleEndpointTimer;

	private ConcurrentHashSet<ImmutableIntList> equivalentSets;

	private ConcurrentHashSet<Integer> conceptsWithInferredChanges;

	private ConcurrentHashSet<Integer> conceptsWithNavigationChanges;

	private AtomicInteger axiomDataNotFoundCounter;

	private final TrackingCallable<?> progressUpdater;

	public InferredResultsWriter(ReasonerService rs, TrackingCallable<?> progressUpdater) {
		super();
		this.rs = rs;
		this.progressUpdater = progressUpdater;
	}

	private ViewCoordinateRecord getViewCoordinateRecord() {
		return rs.getViewCalculator().viewCoordinateRecord();
	}

	private void updateProgress(int count, int total, long startTime) {
		if (count % 1000 == 0) {
			long now = System.currentTimeMillis();
			long elapsed = now - startTime;

			String msg = String.format("Processing inferred results: %,d/%,d", count, total);;

			if (elapsed > 0 && count > 0) {
				double rate = (double) count / elapsed;
				double remainingItems = total - count;
				long remainingMillis = (long) (remainingItems / rate);
				int percent = (int) ((count / (double) total) * 100);
				msg = String.format("Processing inferred results: %d/%d (%d%%) - ETA: %s",
						count, total, percent, formatDuration(remainingMillis));
			}

			if (progressUpdater != null) {
				progressUpdater.updateProgress(count, total);
				progressUpdater.updateMessage(msg);
			}

			// Log status every ~5% to avoid spamming the log
			int logInterval = Math.max(10000, total / 20);
			if (count % logInterval == 0) {
				LOG.info(msg);
			}
		}
	}

	private String formatDuration(long millis) {
		long seconds = millis / 1000;
		long minutes = seconds / 60;
		long hours = minutes / 60;
		if (hours > 0) {
			return String.format("%d:%02d:%02d", hours, minutes % 60, seconds % 60);
		}
		return String.format("%d:%02d", minutes, seconds % 60);
	}

	private void processSemantic(Entity<? extends EntityVersion> entity) {
		updateTransaction.addComponent(entity);
		Entity.provider().putEntity(entity);
	}

	private record SemanticToProcess(long semanticMsb, long semanticLsb, int semanticNid, int referencedComponentNid) {

		public SemanticToProcess(UUID semanticUuid, int semanticNid, int referencedComponentNid) {
			this(semanticUuid.getMostSignificantBits(), semanticUuid.getLeastSignificantBits(), semanticNid, referencedComponentNid);
		}

		public UUID semanticUuid() {
			return new UUID(semanticMsb, semanticLsb);
		}
	}
	public ClassifierResults write() {
		ImmutableIntList conceptsToUpdate = rs.getReasonerConceptSet();
		final int totalCount = conceptsToUpdate.size() * 2;
		final long startTime = System.currentTimeMillis();
		updateProgress(0, totalCount, startTime);
		final AtomicInteger processedCount = new AtomicInteger();
		updateTransaction = Transaction.make("Committing classification");
		EntityService.get().beginLoadPhase();
		try {
			StampEntity<?> updateStamp = updateTransaction.getStamp(State.ACTIVE,
					getViewCoordinateRecord().getAuthorNidForChanges(), getViewCoordinateRecord().getDefaultModuleNid(),
					getViewCoordinateRecord().getDefaultPathNid());
			updateStampNid = updateStamp.nid();
			inferredPattern = EntityHandle.getPatternOrThrow(getViewCoordinateRecord().logicCoordinate().inferredAxiomsPatternNid());
			inferredNavigationPattern = EntityHandle.getPatternOrThrow(TinkarTerm.INFERRED_NAVIGATION_PATTERN.nid());
			multipleEndpointTimer = new MultipleEndpointTimer<>(IsomorphicResults.EndPoints.class);
			equivalentSets = new ConcurrentHashSet<>();
			conceptsWithInferredChanges = new ConcurrentHashSet<>();
			conceptsWithNavigationChanges = new ConcurrentHashSet<>();
			axiomDataNotFoundCounter = new AtomicInteger();

			// Create more chunks than cores for work stealing
			int chunkSize = PrimitiveData.calculateOptimalChunkSize(conceptsToUpdate.size());

			LOG.info("Starting inferred results write. Total concepts: {}, Total tasks (NNF+Nav): {}, Chunk size: {}",
					conceptsToUpdate.size(), totalCount, chunkSize);

			// Use ForkJoinPool's work-stealing capabilities via StructuredTaskScope
			try (var scope = StructuredTaskScope.open()) {

				for (int i = 0; i < conceptsToUpdate.size(); i += chunkSize) {
					int start = i;
					int end = Math.min(i + chunkSize, conceptsToUpdate.size());
					ImmutableIntList chunk = getSubList(conceptsToUpdate, start, end);

					scope.fork(() -> {
						processChunk(chunk, inferredPattern.publicId(), semanticToProcess -> {
							writeNNF(semanticToProcess);
							updateProgress(processedCount.incrementAndGet(), totalCount, startTime);
						});
						return null;
					});
					scope.fork(() -> {
						processChunk(chunk, inferredNavigationPattern.publicId(), semanticToProcess -> {
							writeNavigation(semanticToProcess);
							updateProgress(processedCount.incrementAndGet(), totalCount, startTime);
						});
						return null;
					});
					scope.fork(() -> {
						chunk.forEach(this::updateEquivalentSets);
						return null;
					});
				}

				// Java 25: join() throws if any subtask failed (ShutdownOnFailure behavior implied by context/usage)
				scope.join();

			} catch (Exception ex) {
				if (ex instanceof InterruptedException) {
					Thread.currentThread().interrupt();
				}
				LOG.error("Error during concurrent inferred results writing", ex);
				throw new RuntimeException("Update interrupted or failed", ex);
			}
			updateTransaction.commit();
		} finally {
			EntityService.get().endLoadPhase();
		}
		LOG.info("Inferred changes: " + conceptsWithInferredChanges.size());
		LOG.info("Navigation changes: " + conceptsWithNavigationChanges.size());
		LOG.info("NavigationSemantics processed not in AxiomData: " + axiomDataNotFoundCounter.get());
		ViewCoordinateRecord commitCoordinate = getViewCoordinateRecord().withStampCoordinate(
				getViewCoordinateRecord().stampCoordinate().withStampPositionTime(updateTransaction.commitTime()));
		return new ClassifierResults(rs.getReasonerConceptSet(),
				IntLists.immutable.ofAll(conceptsWithInferredChanges.stream().sorted().mapToInt(Integer::intValue)),
				IntLists.immutable.ofAll(conceptsWithNavigationChanges.stream().sorted().mapToInt(Integer::intValue)),
				equivalentSets, commitCoordinate);
	}

	private void processChunk(ImmutableIntList chunk, PublicId patternPublicId, Consumer<SemanticToProcess> processor) {
		EntityService.get().forEachEntity(chunk, entity -> {
			UUID semanticUuid = UuidT5Generator.singleSemanticUuid(patternPublicId,
					entity.publicId());
			if (PrimitiveData.get().hasUuid(semanticUuid)) {
				processor.accept(new SemanticToProcess(semanticUuid, PrimitiveData.nid(semanticUuid),
						entity.nid()));
			} else {
				processor.accept(new SemanticToProcess(semanticUuid, Integer.MAX_VALUE,
						entity.nid()));
			}
		});
	}

	private ImmutableIntList getSubList(ImmutableIntList list, int start, int end) {
		if (start == end) {
			return IntLists.immutable.empty();
		}
		MutableIntList subList = IntLists.mutable.withInitialCapacity(end - start);
		for (int i = start; i < end; i++) {
			subList.add(list.get(i));
		}
		return subList.toImmutable();
	}


	private void updateEquivalentSets(int conceptNid) {
		ImmutableIntSet equivalentNids = rs.getEquivalent(conceptNid);
		if (equivalentNids == null) {
			LOG.error("Null node for: {} {} {} will be skipped in inferred results", conceptNid,
					PrimitiveData.publicId(conceptNid).idString(), PrimitiveData.text(conceptNid));
		} else if (equivalentNids.size() > 1) {
			equivalentSets.add(equivalentNids.toSortedList().toImmutable());
		}
	}

	private void writeNNF(SemanticToProcess semanticToProcess) {
		LogicalExpression nnf = rs.getNecessaryNormalForm(semanticToProcess.referencedComponentNid);
		if (nnf == null) {
			LOG.error("No NNF for " + EntityHandle.get(semanticToProcess.referencedComponentNid)
					+ " " + PrimitiveData.text(semanticToProcess.referencedComponentNid));
			return;
		}
		ImmutableList<Object> fields = Lists.immutable.of(nnf.sourceGraph());
		int[] inferredSemanticNids = PrimitiveData.get().semanticNidsForComponentOfPattern(semanticToProcess.referencedComponentNid,
				inferredPattern.nid());
		if (inferredSemanticNids.length == 0) {
			// Create new semantic...
			RecordListBuilder<SemanticVersionRecord> versionRecords = RecordListBuilder.make();

			int semanticNid = ScopedValue
					.where(SCOPED_PATTERN_PUBLICID_FOR_NID, inferredPattern.publicId())
					.call(() -> PrimitiveData.nid(semanticToProcess.semanticUuid()));

			SemanticRecord semanticRecord = SemanticRecordBuilder.builder()
					.nid(semanticNid)
					.referencedComponentNid(semanticToProcess.referencedComponentNid)
					.leastSignificantBits(semanticToProcess.semanticLsb)
					.mostSignificantBits(semanticToProcess.semanticMsb)
					.patternNid(inferredPattern.nid()).versions(versionRecords).build();
			versionRecords.add(new SemanticVersionRecord(semanticRecord, updateStampNid, fields));
			processSemantic(semanticRecord);
			conceptsWithInferredChanges.add(semanticToProcess.referencedComponentNid);
		} else if (inferredSemanticNids.length == 1) {
			Latest<SemanticEntityVersion> latestInferredSemantic = rs.getViewCalculator()
					.latest(inferredSemanticNids[0]);
			boolean changed = true;
			if (latestInferredSemantic.isPresent()) {
				ImmutableList<Object> latestInferredFields = latestInferredSemantic.get().fieldValues();
				DiTreeEntity latestInferredTree = (DiTreeEntity) latestInferredFields.get(0);
				DiTreeEntity correlatedTree = latestInferredTree.makeCorrelatedTree((DiTreeEntity) nnf.sourceGraph(),
						semanticToProcess.referencedComponentNid, multipleEndpointTimer.startNew());
				changed = correlatedTree != latestInferredTree;
			}
			if (changed) {
				processSemantic(rs.getViewCalculator().updateFields(inferredSemanticNids[0], fields, updateStampNid));
				conceptsWithInferredChanges.add(semanticToProcess.referencedComponentNid);
			}
		} else {
			throw new IllegalStateException("More than one inferred semantic of pattern "
					+ PrimitiveData.text(inferredPattern.nid()) + "for component: " + PrimitiveData.text(semanticToProcess.referencedComponentNid));
		}
	}

	private void writeNavigation(SemanticToProcess semanticToProcess) {
		ImmutableIntSet parentNids = rs.getParents(semanticToProcess.referencedComponentNid);
		ImmutableIntSet childNids = rs.getChildren(semanticToProcess.referencedComponentNid);
		if (parentNids == null) {
			parentNids = IntSets.immutable.of();
			childNids = IntSets.immutable.of();
			axiomDataNotFoundCounter.incrementAndGet();
		}
		int[] inferredNavigationNids = PrimitiveData.get().semanticNidsForComponentOfPattern(semanticToProcess.referencedComponentNid,
				inferredNavigationPattern.nid());
		if (inferredNavigationNids.length == 0) {
			if (parentNids.notEmpty() || childNids.notEmpty()) {
				// Create new semantic...
				RecordListBuilder<SemanticVersionRecord> versionRecords = RecordListBuilder.make();
				int semanticNid = ScopedValue
						.where(SCOPED_PATTERN_PUBLICID_FOR_NID, inferredNavigationPattern.publicId())
						.call(() -> PrimitiveData.nid(semanticToProcess.semanticUuid()));

				SemanticRecord navigationRecord = SemanticRecordBuilder.builder()
						.nid(semanticNid)
						.referencedComponentNid(semanticToProcess.referencedComponentNid)
						.leastSignificantBits(semanticToProcess.semanticLsb)
						.mostSignificantBits(semanticToProcess.semanticMsb)
						.patternNid(inferredNavigationPattern.nid()).versions(versionRecords).build();
				IntIdSet parentIds = IntIds.set.of(parentNids.toArray());
				IntIdSet childrenIds = IntIds.set.of(childNids.toArray());
				versionRecords.add(new SemanticVersionRecord(navigationRecord, updateStampNid,
						Lists.immutable.of(childrenIds, parentIds)));
				processSemantic(navigationRecord);
				conceptsWithNavigationChanges.add(semanticToProcess.referencedComponentNid);
			}
		} else if (inferredNavigationNids.length == 1) {
			Latest<SemanticEntityVersion> latestInferredNavigationSemantic = rs.getViewCalculator()
					.latest(inferredNavigationNids[0]);
			boolean navigationChanged = true;
			if (latestInferredNavigationSemantic.isPresent()) {
				ImmutableList<Object> latestInferredNavigationFields = latestInferredNavigationSemantic.get()
						.fieldValues();
				IntIdSet childIds = (IntIdSet) latestInferredNavigationFields.get(0);
				IntIdSet parentIds = (IntIdSet) latestInferredNavigationFields.get(1);
				if (parentNids.equals(IntSets.immutable.of(parentIds.toArray()))
						&& childNids.equals(IntSets.immutable.of(childIds.toArray()))) {
					navigationChanged = false;
				}
			}
			if (navigationChanged) {
				IntIdSet newParentIds = IntIds.set.of(parentNids.toArray());
				IntIdSet newChildIds = IntIds.set.of(childNids.toArray());
				processSemantic(rs.getViewCalculator().updateFields(inferredNavigationNids[0],
						Lists.immutable.of(newChildIds, newParentIds), updateStampNid));
				conceptsWithNavigationChanges.add(semanticToProcess.referencedComponentNid);
			}
		} else {
			throw new IllegalStateException(
					"More than one semantic of pattern " + PrimitiveData.text(inferredNavigationPattern.nid())
							+ "for component: " + PrimitiveData.text(semanticToProcess.referencedComponentNid));
		}
	}

}
