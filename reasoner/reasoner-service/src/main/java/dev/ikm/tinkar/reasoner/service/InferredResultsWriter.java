package dev.ikm.tinkar.reasoner.service;

import dev.ikm.tinkar.common.id.IntIdSet;
import dev.ikm.tinkar.common.id.IntIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.common.sets.ConcurrentHashSet;
import dev.ikm.tinkar.common.util.time.MultipleEndpointTimer;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.coordinate.logic.PremiseType;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.coordinate.view.ViewCoordinateRecord;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpression;
import dev.ikm.tinkar.entity.graph.isomorphic.IsomorphicResults;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.terms.State;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.set.primitive.ImmutableIntSet;
import org.eclipse.collections.api.set.primitive.IntSet;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

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
	private AtomicInteger emptyParentCount;
	private AtomicInteger emptyChildCount;
	private int rootConceptNid;

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
		Entity.provider().putEntityNoCache(entity);
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
		LOG.info(logMessage, String.format("%,d", nids.size()), String.format("%,d", chunkSize));

		try (var scope = StructuredTaskScope.open(createAccumulatingJoiner(changedConcepts))) {
			for (int i = 0; i < nids.size(); i += chunkSize) {
				int start = i;
				int end = Math.min(i + chunkSize, nids.size());
				ImmutableIntList chunk = getSubList(nids, start, end);
				permits.acquire();
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
		LOG.info("Reasoner concept set size: {}", conceptsToUpdate.size());
		MutableIntList conceptsWithInferredChanges = IntLists.mutable.withInitialCapacity(conceptsToUpdate.size());
		MutableIntList conceptsWithNavigationChanges = IntLists.mutable.withInitialCapacity(conceptsToUpdate.size());

		final int totalCount = conceptsToUpdate.size();
		final long startTime = System.currentTimeMillis();
		updateProgress(0, totalCount, startTime);
		progressUpdater.updateMessage("Categorizing inferred results.");
		final AtomicInteger processedCount = new AtomicInteger();
		updateTransaction = Transaction.make("Reasoner results transaction");
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
			emptyParentCount = new AtomicInteger();
			emptyChildCount = new AtomicInteger();
			rootConceptNid = getViewCoordinateRecord().logicCoordinate().rootNid();

			// Create more chunks than cores for work stealing
			int chunkSize = PrimitiveData.calculateOptimalChunkSize(conceptsToUpdate.size());

			MutableIntList inferredSemanticNids = IntLists.mutable.empty();
			MutableIntList noInferredSemanticConcepts = IntLists.mutable.empty();
			MutableIntList navigationSemanticNids = IntLists.mutable.empty();
			MutableIntList noNavigationSemanticConcepts = IntLists.mutable.empty();

			// Record to hold results from each task
			record ChunkResults(
					MutableIntList inferredSemanticToUpdate,
					MutableIntList conceptForNewInferredSemantic,
					MutableIntList navSemanticToUpdate,
					MutableIntList conceptForNewNavSemantic) {

				ChunkResults(int chunkCount) {
						this(IntLists.mutable.withInitialCapacity(chunkCount),
								IntLists.mutable.withInitialCapacity(chunkCount),
								IntLists.mutable.withInitialCapacity(chunkCount),
								IntLists.mutable.withInitialCapacity(chunkCount));
					}
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
						inferredSemanticNids.addAll(results.inferredSemanticToUpdate);
						noInferredSemanticConcepts.addAll(results.conceptForNewInferredSemantic);
						navigationSemanticNids.addAll(results.navSemanticToUpdate);
						noNavigationSemanticConcepts.addAll(results.conceptForNewNavSemantic);
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
			final int totalWork = inferredSemanticNids.size() + noInferredSemanticConcepts.size() + navigationSemanticNids.size() + noNavigationSemanticConcepts.size();
					LOG.info(String.format("Starting inferred results write. Total concepts: %,d, Total tasks (NNF+Nav): %,d, Chunk size: %,d",
							conceptsToUpdate.size(), totalWork, chunkSize));


			Semaphore permits = new Semaphore(Runtime.getRuntime().availableProcessors() * 20);


// Process all concepts in chunks to categorize them
			try (var scope = StructuredTaskScope.open(accumulatingJoiner)) {
				for (int i = 0; i < conceptsToUpdate.size(); i += chunkSize) {
					int start = i;
					int end = Math.min(i + chunkSize, conceptsToUpdate.size());
					ImmutableIntList conceptChunk = getSubList(conceptsToUpdate, start, end);

					permits.acquire();
					scope.fork(() -> {
                        try {
                            ChunkResults results = new ChunkResults(chunkSize);

                            EntityService.get().forEachEntity(conceptChunk, concept -> {
								progressUpdater.completedUnitOfWork();
								
								// Find canonical inferred semantic, handling duplicates gracefully
								Optional<Integer> inferredSemanticNid = findCanonicalSemanticNid(
										inferredPattern, concept);
								if (inferredSemanticNid.isPresent()) {
									results.inferredSemanticToUpdate.add(inferredSemanticNid.get());
								} else {
									results.conceptForNewInferredSemantic.add(concept.nid());
								}

								// Find canonical navigation semantic, handling duplicates gracefully
								Optional<Integer> navigationSemanticNid = findCanonicalSemanticNid(
										inferredNavigationPattern, concept);
								if (navigationSemanticNid.isPresent()) {
									results.navSemanticToUpdate.add(navigationSemanticNid.get());
								} else {
									results.conceptForNewNavSemantic.add(concept.nid());
								}
                            });

                            return results;
                        } finally {
							permits.release();
                        }
                    });
				}
				scope.join();
			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			LOG.info("Inferred semantics to update: " + String.format("%,d", inferredSemanticNids.size()));
			LOG.info("Concepts needing new inferred semantics: " + String.format("%,d", noInferredSemanticConcepts.size()));
			LOG.info("Navigation semantics to update: " + String.format("%,d", navigationSemanticNids.size()));
			LOG.info("Concepts needing new navigation semantics: " + String.format("%,d", noNavigationSemanticConcepts.size()));
			logSampleConcepts("Concepts missing inferred semantics (sample)", noInferredSemanticConcepts, 10);
			logSampleConcepts("Concepts missing navigation semantics (sample)", noNavigationSemanticConcepts, 10);

			try {
				progressUpdater.updateMessage("Processing updates to inferred semantics");

				conceptsWithInferredChanges.addAll(
						processEntitiesInScope(
								inferredSemanticNids,
								"Processing updates to {} inferred semantic nids in chunks of {}",
								(entity, changedConcepts) -> {
									if (entity instanceof SemanticEntity<?> semanticEntity) {
										progressUpdater.completedUnitOfWork();
										updateNNF(semanticEntity, changedConcepts);
									} else {
										LOG.error("Unexpected entity type: {}", entity.getClass());
									}
								},
								permits
						)
				);
				LOG.info("Updated inferred semantics.");

				progressUpdater.updateMessage("Processing new inferred semantics");
				conceptsWithInferredChanges.addAll(
						processEntitiesInScope(
								noInferredSemanticConcepts,
								"Processing {} new inferred semantics in chunks of {}",
								(entity, changedConcepts) -> {
									changedConcepts.add(entity.nid());
									if (entity instanceof ConceptEntity<?> conceptEntity) {
										progressUpdater.completedUnitOfWork();
										newNNF(conceptEntity);
									} else {
										LOG.error("Unexpected entity type: {}", entity.getClass());
									}
								},
								permits
						)
				);
				LOG.info("Created new inferred semantics.");

				progressUpdater.updateMessage("Processing updates to navigation semantics");
				conceptsWithNavigationChanges.addAll(
						processEntitiesInScope(
								navigationSemanticNids,
								"Processing updates to {} navigation semantic nids in chunks of {}",
								(entity, changedConcepts) -> {
									progressUpdater.completedUnitOfWork();
									if (entity instanceof SemanticEntity<?> semanticEntity) {
										updateNavigation(semanticEntity, changedConcepts);
									} else {
										LOG.error("Unexpected entity type: {}", entity.getClass());
									}
								},
								permits
						)
				);
				LOG.info("Updated navigation semantics.");

				progressUpdater.updateMessage("Processing updates to navigation semantics");
				conceptsWithNavigationChanges.addAll(
						processEntitiesInScope(
								noNavigationSemanticConcepts,
								"Processing {} new navigation semantics in chunks of {}",
								(entity, changedConcepts) -> {
									changedConcepts.add(entity.nid());
									if (entity instanceof ConceptEntity<?> conceptEntity) {
										progressUpdater.completedUnitOfWork();
										newNavigation(conceptEntity);
									} else {
										LOG.error("Unexpected entity type: {}", entity.getClass());
									}
								},
								permits
						)
				);
				LOG.info("Created new navigation semantics.");

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
		if (emptyChildCount != null) {
			LOG.info("Empty child sets observed: {}", emptyChildCount.get());
		}
		ViewCoordinateRecord commitCoordinate = getViewCoordinateRecord().withStampCoordinate(
				getViewCoordinateRecord().stampCoordinate().withStampPositionTime(updateTransaction.commitTime()));

		progressUpdater.updateMessage("Wrote inferred results in " + progressUpdater.durationString());
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
		LogicalExpression nnf = getNecessaryNormalFormOrStated(semanticEntity.referencedComponentNid(),
				"updating inferred semantic nid=" + semanticEntity.nid());
		if (nnf == null) {
			return;
		}
		boolean same = true;
		if (latestInferredSemantic.isPresent()) {
			ImmutableList<Object> latestInferredFields = latestInferredSemantic.get().fieldValues();
			DiTreeEntity latestInferredTree = (DiTreeEntity) latestInferredFields.get(0);
			DiTreeEntity correlatedTree = latestInferredTree.makeCorrelatedTree((DiTreeEntity) nnf.sourceGraph(),
					semanticEntity.referencedComponentNid(), multipleEndpointTimer.startNew());
            same = correlatedTree.equals(latestInferredTree);
		}
		if (!same) {
		    ImmutableList<Object> fields = Lists.immutable.of(nnf.sourceGraph());
			    processSemantic(rs.getViewCalculator().updateFields(semanticEntity.nid(), fields, updateStampNid));
			    changedConcepts.add(semanticEntity.referencedComponentNid());
		}
	}

	public void newNNF(ConceptEntity concept) {
		LogicalExpression nnf = getNecessaryNormalFormOrStated(concept.nid(), "creating inferred semantic");
		if (nnf == null) {
			return;
		}
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
		logEmptyNavigationSets(semanticEntity.referencedComponentNid(), parentNids, childNids);
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

	private void newNavigation(ConceptEntity concept) {
		UUID inferredNavigationUuid = UuidT5Generator.singleSemanticUuid(inferredNavigationPattern,
				concept.publicId());
		ImmutableIntSet parentNids = rs.getParents(concept.nid());
		ImmutableIntSet childNids = rs.getChildren(concept.nid());
		if (parentNids == null) {
			parentNids = IntSets.immutable.of();
			axiomDataNotFoundCounter.incrementAndGet();
		}
		if (childNids == null) {
			childNids = IntSets.immutable.of();
		}
		logEmptyNavigationSets(concept.nid(), parentNids, childNids);
		if (parentNids.notEmpty() || childNids.notEmpty()) {
			// Create a new semantic...
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

	private LogicalExpression getNecessaryNormalFormOrStated(int conceptNid, String context) {
		LogicalExpression nnf = rs.getNecessaryNormalForm(conceptNid);
		if (nnf != null) {
			return nnf;
		}
		Latest<DiTreeEntity> statedTree = rs.getViewCalculator().getAxiomTreeForEntity(conceptNid, PremiseType.STATED);
		if (statedTree.isPresent()) {
			LOG.warn("No necessary normal form for concept nid={} ({}) when {}; using stated axioms fallback",
					conceptNid, PrimitiveData.text(conceptNid), context);
			return new LogicalExpression(statedTree.get());
		}
		LOG.warn("No necessary normal form for concept nid={} ({}) when {}; stated axioms missing",
				conceptNid, PrimitiveData.text(conceptNid), context);
		return null;
	}

	private void logEmptyNavigationSets(int conceptNid, ImmutableIntSet parentNids, ImmutableIntSet childNids) {
		if (parentNids.isEmpty()) {
			int count = emptyParentCount.incrementAndGet();
			if (conceptNid == rootConceptNid) {
				LOG.warn("Empty parent set for root concept nid={} ({})", conceptNid, PrimitiveData.text(conceptNid));
			} else if (count > 1) {
				LOG.error("Multiple concepts with empty parent sets. Count={}, latest nid={} ({}), rootNid={} ({})",
						count, conceptNid, PrimitiveData.text(conceptNid), rootConceptNid,
						PrimitiveData.text(rootConceptNid));
			} else {
				LOG.error("Empty parent set for non-root concept nid={} ({}), rootNid={} ({})",
						conceptNid, PrimitiveData.text(conceptNid), rootConceptNid,
						PrimitiveData.text(rootConceptNid));
			}
		}
		if (childNids.isEmpty()) {
			int count = emptyChildCount.incrementAndGet();
			if (count <= 5 && LOG.isDebugEnabled()) {
				LOG.debug("Empty child set for concept nid={} ({}). Empty child count={}",
						conceptNid, PrimitiveData.text(conceptNid), count);
			}
		}
	}

	/**
	 * Finds the canonical semantic for a single-semantic pattern, handling duplicates gracefully.
	 * If duplicates exist due to a past race condition, this method identifies the correct one
	 * based on the reproducible UUID generation and logs a warning about the duplicates.
	 *
	 * @param patternEntity the pattern entity
	 * @param referencedComponent the referenced component (concept)
	 * @return Optional containing the nid of the correct semantic, or empty if none exists
	 */
	private Optional<Integer> findCanonicalSemanticNid(PatternEntity<?> patternEntity, Entity<?> referencedComponent) {
		int[] semanticNids = PrimitiveData.get().semanticNidsForComponentOfPattern(
				referencedComponent.nid(), patternEntity.nid());
		
		if (semanticNids.length == 0) {
			return Optional.empty();
		}
		
		if (semanticNids.length == 1) {
			// Verify the single semantic belongs to the expected pattern
			SemanticEntity<?> semantic = EntityHandle.getSemanticOrThrow(semanticNids[0]);
			if (semantic.patternNid() != patternEntity.nid()) {
				LOG.error("Pattern mismatch! Expected pattern {} but semantic {} has pattern {}. " +
						"Referenced component: {}. This indicates a data or indexing corruption.",
						PrimitiveData.textWithNid(patternEntity.nid()),
						PrimitiveData.textWithNid(semanticNids[0]),
						PrimitiveData.textWithNid(semantic.patternNid()),
						PrimitiveData.textWithNid(referencedComponent.nid()));
				return Optional.empty(); // Don't return mismatched semantic
			}
			return Optional.of(semanticNids[0]);
		}
		
		// Multiple semantics found - need to determine which is canonical
		UUID canonicalUuid = UuidT5Generator.singleSemanticUuid(patternEntity, referencedComponent.publicId());
		
		int canonicalNid = -1;
		MutableIntList duplicateNids = IntLists.mutable.empty();
		MutableIntList wrongPatternNids = IntLists.mutable.empty();
		
		for (int semanticNid : semanticNids) {
			SemanticEntity<?> semantic = EntityHandle.getSemanticOrThrow(semanticNid);
			
			// Verify semantic belongs to the expected pattern
			if (semantic.patternNid() != patternEntity.nid()) {
				wrongPatternNids.add(semanticNid);
				LOG.error("Pattern mismatch in multi-semantic lookup! Expected pattern {} but semantic {} has pattern {}. " +
						"Referenced component: {}",
						PrimitiveData.textWithNid(patternEntity.nid()),
						PrimitiveData.textWithNid(semanticNid),
						PrimitiveData.textWithNid(semantic.patternNid()),
						PrimitiveData.textWithNid(referencedComponent.nid()));
				continue; // Skip this semantic - wrong pattern
			}
			
			if (semantic.publicId().contains(canonicalUuid)) {
				canonicalNid = semanticNid;
			} else {
				duplicateNids.add(semanticNid);
			}
		}
		
		// Log if we found semantics with wrong patterns
		if (wrongPatternNids.notEmpty()) {
			LOG.error("Found {} semantic(s) with WRONG PATTERN for expected pattern {} with component {}. " +
					"Wrong pattern NIDs: {}. This indicates data or indexing corruption.",
					wrongPatternNids.size(),
					PrimitiveData.textWithNid(patternEntity.nid()),
					PrimitiveData.textWithNid(referencedComponent.nid()),
					wrongPatternNids.toList());
		}
		
		// Log the warning about duplicates (same pattern, different UUIDs)
		if (duplicateNids.notEmpty()) {
			LOG.warn("Found {} duplicate semantic(s) for pattern {} with component {}. " +
					"Canonical UUID: {}, Canonical NID: {}, Duplicate NIDs: {}. " +
					"These duplicates were likely created by a past race condition in UUID generation. " +
					"Consider running a cleanup task to remove the duplicates.",
					duplicateNids.size(),
					PrimitiveData.text(patternEntity.nid()),
					PrimitiveData.text(referencedComponent.nid()),
					canonicalUuid,
					canonicalNid,
					duplicateNids.toList());
		}
		
		if (canonicalNid != -1) {
			return Optional.of(canonicalNid);
		}
		
		// None matched the canonical UUID - this shouldn't happen but handle gracefully
		// Find first semantic with correct pattern
		for (int semanticNid : semanticNids) {
			if (!wrongPatternNids.contains(semanticNid)) {
				LOG.error("No semantic matched the canonical UUID {} for pattern {} and component {}. " +
						"Using first found semantic with correct pattern (nid: {}). All semantic NIDs: {}",
						canonicalUuid,
						PrimitiveData.text(patternEntity.nid()),
						PrimitiveData.text(referencedComponent.nid()),
						semanticNid,
						Arrays.toString(semanticNids));
				return Optional.of(semanticNid);
			}
		}
		
		// All semantics had wrong pattern - return empty
		LOG.error("All semantics had wrong pattern for pattern {} and component {}. Semantic NIDs: {}",
				PrimitiveData.text(patternEntity.nid()),
				PrimitiveData.text(referencedComponent.nid()),
				Arrays.toString(semanticNids));
		return Optional.empty();
	}

	private void logSampleConcepts(String label, MutableIntList nids, int maxSamples) {
		if (nids.isEmpty()) {
			return;
		}
		StringBuilder sample = new StringBuilder();
		int limit = Math.min(maxSamples, nids.size());
		for (int i = 0; i < limit; i++) {
			int nid = nids.get(i);
			if (sample.length() > 0) {
				sample.append(", ");
			}
			sample.append(nid).append(":").append(PrimitiveData.text(nid));
		}
		LOG.info("{}: {}", label, sample);
	}

}
