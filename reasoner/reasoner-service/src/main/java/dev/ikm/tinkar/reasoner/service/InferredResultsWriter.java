package dev.ikm.tinkar.reasoner.service;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.entity.*;
import org.eclipse.collections.api.IntIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.primitive.IntObjectMaps;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.IntList;
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

			String msg = String.format("Processing  %,d inferred result items", total);
			;

			if (elapsed > 0 && count > 0) {
				double rate = (double) count / elapsed;
				double remainingItems = total - count;
				long remainingMillis = (long) (remainingItems / rate);
				double percent = (count / (double) total) * 100;
				msg = String.format("Processing inferred results: %,.2f%% - ETA: %s",
						percent, formatDuration(remainingMillis));
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

	private StructuredTaskScope.Joiner<MutableIntList, Void> createAccumulatingJoiner(MutableIntList accumulator) {
		return new StructuredTaskScope.Joiner<MutableIntList, Void>() {
			@Override
			public Void result() {
				return null;
			}

			@Override
			public boolean onComplete(StructuredTaskScope.Subtask<? extends MutableIntList> subtask) {
				if (subtask.state() == StructuredTaskScope.Subtask.State.SUCCESS) {
					accumulator.addAll(subtask.get());
				}
				return false; // Continue processing all tasks
			}

			@Override
			public boolean onFork(StructuredTaskScope.Subtask<? extends MutableIntList> subtask) {
				return false; // Don't short-circuit on fork
			}
		};
	}

	private MutableIntList processEntitiesInScope(
			IntList nids,
			String logMessage,
			BiConsumer<Entity<? extends EntityVersion>, MutableIntList> processor,
			Semaphore permits
	) throws InterruptedException {
		MutableIntList changedConcepts = IntLists.mutable.empty();
		int chunkSize = calculateOptimalChunkSize(nids.size());
		LOG.info(logMessage, nids.size(), chunkSize);

		try (var scope = StructuredTaskScope.open(createAccumulatingJoiner(changedConcepts))) {
			for (int i = 0; i < nids.size(); i += chunkSize) {
				int start = i;
				int end = Math.min(i + chunkSize, nids.size());
				ImmutableIntList chunk = getSubList(nids, start, end);
				permits.acquireUninterruptibly();
				scope.fork(() -> {
					try {
						MutableIntList localChanges = IntLists.mutable.empty();
						EntityService.get().forEachEntity(chunk, entity -> processor.accept(entity, localChanges));
						return localChanges;
					} finally {
						permits.release();
					}
				});
			}
			scope.join();
		}
		return changedConcepts;
	}
	public ClassifierResults write() {
		ImmutableIntList conceptsToUpdate = rs.getReasonerConceptSet();
		MutableIntList conceptsWithInferredChanges = IntLists.mutable.withInitialCapacity(conceptsToUpdate.size());
		MutableIntList conceptsWithNavigationChanges = IntLists.mutable.withInitialCapacity(conceptsToUpdate.size());

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
			axiomDataNotFoundCounter = new AtomicInteger();

			// Create more chunks than cores for work stealing
			int chunkSize = PrimitiveData.calculateOptimalChunkSize(conceptsToUpdate.size());

			LOG.info("Starting inferred results write. Total concepts: {}, Total tasks (NNF+Nav): {}, Chunk size: {}",
					conceptsToUpdate.size(), totalCount, chunkSize);

			MutableIntList inferredSemanticNids = IntLists.mutable.empty();
			MutableIntList noInferredSemanticConcepts = IntLists.mutable.empty();
			MutableIntList navigationSemanticNids = IntLists.mutable.empty();
			MutableIntList noNavigationSemanticConcepts = IntLists.mutable.empty();

			// Record to hold results from each task
			record ChunkResults(
					MutableIntList inferredExists,
					MutableIntList inferredMissing,
					MutableIntList navExists,
					MutableIntList navMissing
			) {
			}

			// Custom joiner that accumulates results as tasks complete
			StructuredTaskScope.Joiner<ChunkResults, Void> accumulatingJoiner = new StructuredTaskScope.Joiner<>() {
				@Override
				public Void result() {
					return null; // No final result needed
				}

				@Override
				public boolean onComplete(StructuredTaskScope.Subtask<? extends ChunkResults> subtask) {
					if (subtask.state() == StructuredTaskScope.Subtask.State.SUCCESS) {
						ChunkResults results = subtask.get();
						// Add to master lists immediately as each task completes
						inferredSemanticNids.addAll(results.inferredExists);
						noInferredSemanticConcepts.addAll(results.inferredMissing);
						navigationSemanticNids.addAll(results.navExists);
						noNavigationSemanticConcepts.addAll(results.navMissing);
					} else if (subtask.state() == StructuredTaskScope.Subtask.State.FAILED) {
						LOG.error("Task failed", subtask.exception());
					}
					return false; // Continue processing remaining tasks
				}

				@Override
				public boolean onFork(StructuredTaskScope.Subtask<? extends ChunkResults> subtask) {
					return false; // Don't short-circuit on fork
				}
			};
			try {

				Semaphore permits = new Semaphore(Runtime.getRuntime().availableProcessors() * 20);

				conceptsWithInferredChanges.addAll(
						processEntitiesInScope(
								inferredSemanticNids,
								"Processing updates to {} inferred semantic nids in chunks of {}",
								(entity, changedConcepts) -> {
									if (entity instanceof SemanticEntity<?> semanticEntity) {
										updateNNF(semanticEntity, changedConcepts);
									} else {
										LOG.error("Unexpected entity type: {}", entity.getClass());
									}
								},
								permits
						)
				);

				conceptsWithNavigationChanges.addAll(
						processEntitiesInScope(
								navigationSemanticNids,
								"Processing updates to {} navigation semantic nids in chunks of {}",
								(entity, changedConcepts) -> {
									if (entity instanceof SemanticEntity<?> semanticEntity) {
										updateNavigation(semanticEntity, changedConcepts);
									} else {
										LOG.error("Unexpected entity type: {}", entity.getClass());
									}
								},
								permits
						)
				);

				conceptsWithNavigationChanges.addAll(
						processEntitiesInScope(
								navigationSemanticNids,
								"Processing updates to {} navigation semantic nids in chunks of {}",
								(entity, changedConcepts) -> {
									if (entity instanceof SemanticEntity<?> semanticEntity) {
										updateNavigation(semanticEntity, changedConcepts);
									} else {
										LOG.error("Unexpected entity type: {}", entity.getClass());
									}
								},
								permits
						)
				);

				conceptsWithInferredChanges.addAll(
						processEntitiesInScope(
								noInferredSemanticConcepts,
								"Processing {} new inferred semantic nids in chunks of {}",
								(entity, changedConcepts) -> {
									changedConcepts.add(entity.nid());
									if (entity instanceof ConceptEntity<?> conceptEntity) {
										newNNF(conceptEntity);
									} else {
										LOG.error("Unexpected entity type: {}", entity.getClass());
									}
								},
								permits
						)
				);
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
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
				conceptsWithInferredChanges.toImmutable(),
				conceptsWithNavigationChanges.toImmutable(),
				equivalentSets, commitCoordinate);
	}

	private ImmutableIntList getSubList(IntList list, int start, int end) {
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

	public void updateNNF(SemanticEntity<?> semanticEntity, MutableIntList changedConcepts) {
		Objects.nonNull(semanticEntity);
		Latest<SemanticEntityVersion> latestInferredSemantic = (Latest<SemanticEntityVersion>) rs.getViewCalculator()
				.latest(semanticEntity);
		LogicalExpression nnf = rs.getNecessaryNormalForm(semanticEntity.referencedComponentNid());
		boolean changed = true;
		if (latestInferredSemantic.isPresent()) {
			ImmutableList<Object> latestInferredFields = latestInferredSemantic.get().fieldValues();
			DiTreeEntity latestInferredTree = (DiTreeEntity) latestInferredFields.get(0);
			DiTreeEntity correlatedTree = latestInferredTree.makeCorrelatedTree((DiTreeEntity) nnf.sourceGraph(),
					semanticEntity.referencedComponentNid(), multipleEndpointTimer.startNew());
			changed = correlatedTree.equals(latestInferredTree);
		}
		if (changed) {
		ImmutableList<Object> fields = Lists.immutable.of(nnf.sourceGraph());
			processSemantic(rs.getViewCalculator().updateFields(semanticEntity.nid(), fields, updateStampNid));
			changedConcepts.add(semanticEntity.referencedComponentNid());
		}
	}

	public void newNNF(ConceptEntity concept) {
		LogicalExpression nnf = rs.getNecessaryNormalForm(concept.nid());
		ImmutableList<Object> fields = Lists.immutable.of(nnf.sourceGraph());
		UUID inferredSemanticUuid = UuidT5Generator.singleSemanticUuid(inferredPattern,
				concept.publicId());
		// Create a new semantic...
		RecordListBuilder<SemanticVersionRecord> versionRecords = RecordListBuilder.make();

		int semanticNid = ScopedValue
				.where(SCOPED_PATTERN_PUBLICID_FOR_NID, inferredPattern.publicId())
				.call(() -> PrimitiveData.nid(inferredSemanticUuid));

		SemanticRecord semanticRecord = SemanticRecordBuilder.builder()
				.nid(semanticNid)
				.referencedComponentNid(concept.nid())
				.leastSignificantBits(inferredSemanticUuid.getLeastSignificantBits())
				.mostSignificantBits(inferredSemanticUuid.getMostSignificantBits())
				.patternNid(inferredPattern.nid()).versions(versionRecords).build();
		versionRecords.add(new SemanticVersionRecord(semanticRecord, updateStampNid, fields));
		processSemantic(semanticRecord);
	}

	private void updateNavigation(SemanticEntity<?> semanticEntity, MutableIntList changedConcepts) {
		ImmutableIntSet parentNids = rs.getParents(semanticEntity.referencedComponentNid());
		ImmutableIntSet childNids = rs.getChildren(semanticEntity.referencedComponentNid());
		if (parentNids == null) {
			parentNids = IntSets.immutable.of();
			childNids = IntSets.immutable.of();
			axiomDataNotFoundCounter.incrementAndGet();
		}
		Latest<SemanticEntityVersion> latestInferredNavigationSemantic = rs.getViewCalculator()
				.latest((Entity<SemanticEntityVersion>) semanticEntity);
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
			processSemantic(rs.getViewCalculator().updateFields(semanticEntity.nid(),
					Lists.immutable.of(newChildIds, newParentIds), updateStampNid));
			changedConcepts.add(semanticEntity.referencedComponentNid());
		}
	}

	private void writeNavigation(ConceptEntity concept) {
		UUID inferredNavigationUuid = UuidT5Generator.singleSemanticUuid(inferredNavigationPattern,
				concept.publicId());
		ImmutableIntSet parentNids = rs.getParents(concept.nid());
		ImmutableIntSet childNids = rs.getChildren(concept.nid());
		if (parentNids == null) {
			parentNids = IntSets.immutable.of();
			childNids = IntSets.immutable.of();
			axiomDataNotFoundCounter.incrementAndGet();
		}
		if (parentNids.notEmpty() || childNids.notEmpty()) {
			// Create new semantic...
			RecordListBuilder<SemanticVersionRecord> versionRecords = RecordListBuilder.make();
			int semanticNid = ScopedValue
					.where(SCOPED_PATTERN_PUBLICID_FOR_NID, inferredNavigationPattern.publicId())
					.call(() -> PrimitiveData.nid(inferredNavigationUuid));

			SemanticRecord navigationRecord = SemanticRecordBuilder.builder()
					.nid(semanticNid)
					.referencedComponentNid(concept.nid())
					.leastSignificantBits(inferredNavigationUuid.getLeastSignificantBits())
					.mostSignificantBits(inferredNavigationUuid.getMostSignificantBits())
					.patternNid(inferredNavigationPattern.nid()).versions(versionRecords).build();
			IntIdSet parentIds = IntIds.set.of(parentNids.toArray());
			IntIdSet childrenIds = IntIds.set.of(childNids.toArray());
			versionRecords.add(new SemanticVersionRecord(navigationRecord, updateStampNid,
					Lists.immutable.of(childrenIds, parentIds)));
			processSemantic(navigationRecord);
		}
	}

}
