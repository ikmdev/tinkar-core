package dev.ikm.tinkar.reasoner.service;

import org.eclipse.collections.api.list.primitive.LongList;
import org.eclipse.collections.api.list.primitive.MutableLongList;
import org.eclipse.collections.api.set.primitive.ImmutableLongSet;

import dev.ikm.tinkar.common.id.LongIdSet;
import dev.ikm.tinkar.common.id.LongIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.common.sets.ConcurrentHashSet;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.coordinate.logic.PremiseType;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.coordinate.view.ViewCoordinateRecord;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.DiTreeIsomorphism;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpression;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.terms.KernelPattern;
import dev.ikm.tinkar.terms.State;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;
import org.eclipse.collections.api.list.primitive.LongList;
import org.eclipse.collections.api.list.primitive.MutableLongList;
import org.eclipse.collections.api.set.primitive.ImmutableLongSet;
import org.eclipse.collections.api.set.primitive.LongSet;
import org.eclipse.collections.impl.factory.primitive.LongLists;
import org.eclipse.collections.impl.factory.primitive.LongSets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
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
	private static final ScopedValue<LongSet> CONCEPTS_TO_UPDATE = ScopedValue.newInstance();
	private static final ScopedValue<AtomicInteger> PROCESSED_COUNT = ScopedValue.newInstance();
	private static final ScopedValue<Integer> TOTAL_COUNT = ScopedValue.newInstance();

	private ReasonerService rs;

	private Transaction updateTransaction;

	private long updateStampNid;

	private PatternEntity<PatternEntityVersion> inferredPattern;
	private PatternEntity<PatternEntityVersion> inferredNavigationPattern;

	/** Absent on a store whose starter data predates the pattern; equivalences are then not written. */
	private PatternEntity<PatternEntityVersion> inferredEquivalencePattern;

	private ConcurrentHashSet<ImmutableLongList> equivalentSets;

	private AtomicInteger axiomDataNotFoundCounter;
	private AtomicInteger emptyParentCount;
	private AtomicInteger emptyChildCount;
	private long rootConceptNid;

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

	/**
	 * The failures of a scope's chunks. A failed chunk's concepts are written by no other, so a
	 * scope that had one fails the write, which cancels the transaction: a chunk that failed
	 * unreported left every concept in it without an inferred axiom semantic, and the
	 * classification reported success (IKE-Network/ike-issues#1301).
	 */
	private static final class ChunkFailures {
		private final List<Throwable> failures = new ArrayList<>();

		// Called under the joiner's lock.
		void add(Throwable chunkFailure) {
			failures.add(chunkFailure);
		}

		void throwIfAny() {
			if (failures.isEmpty()) {
				return;
			}
			IllegalStateException failure = new IllegalStateException(
					failures.size() + " chunk(s) of inferred results failed", failures.getFirst());
			failures.stream().skip(1).limit(9).forEach(failure::addSuppressed);
			throw failure;
		}
	}

	private StructuredTaskScope.Joiner<MutableLongList, Void, RuntimeException> createAccumulatingJoiner(MutableLongList accumulator) {
		return new StructuredTaskScope.Joiner<MutableLongList, Void, RuntimeException>() {
			private final ChunkFailures failures = new ChunkFailures();

			@Override
			public Void result() {
				failures.throwIfAny();
				return null;
			}

			@Override
			public Void timeout() {
				// The scope is opened without a timeout, so join() never calls this; if one is
				// ever configured, report it rather than return a partial accumulation.
				throw new IllegalStateException("inferred-results scope timed out",
						new StructuredTaskScope.CancelledByTimeoutException());
			}

			// Called from each subtask's thread, concurrently; the accumulator is unsynchronized.
			@Override
			public synchronized boolean onComplete(StructuredTaskScope.Subtask<MutableLongList> subtask) {
				if (subtask.state() == StructuredTaskScope.Subtask.State.SUCCESS) {
					accumulator.addAll(subtask.get());
				} else if (subtask.state() == StructuredTaskScope.Subtask.State.FAILED) {
					failures.add(subtask.exception());
				}
				return false; // Continue processing all tasks
			}

			@Override
			public boolean onFork(StructuredTaskScope.Subtask<MutableLongList> subtask) {
				return false; // Don't short-circuit on fork
			}
		};
	}

	private MutableLongList processEntitiesInScope(
			LongList nids,
			String logMessage,
			BiConsumer<Entity<? extends EntityVersion>, MutableLongList> processor,
			Semaphore permits
	) throws InterruptedException {
		MutableLongList changedConcepts = LongLists.mutable.empty();
		int chunkSize = calculateOptimalChunkSize(nids.size());
		LOG.info(logMessage, String.format("%,d", nids.size()), String.format("%,d", chunkSize));

		try (var scope = StructuredTaskScope.open(createAccumulatingJoiner(changedConcepts))) {
			for (int i = 0; i < nids.size(); i += chunkSize) {
				int start = i;
				int end = Math.min(i + chunkSize, nids.size());
				ImmutableLongList chunk = getSubList(nids, start, end);
				permits.acquire();
				scope.fork(() -> {
					try {
						MutableLongList localChanges = LongLists.mutable.empty();
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
		ImmutableLongList conceptsToUpdate = rs.getReasonerConceptSet();
		LOG.info("Reasoner concept set size: {}", conceptsToUpdate.size());
		MutableLongList conceptsWithInferredChanges = LongLists.mutable.withInitialCapacity(conceptsToUpdate.size());
		MutableLongList conceptsWithNavigationChanges = LongLists.mutable.withInitialCapacity(conceptsToUpdate.size());

		final int totalCount = conceptsToUpdate.size();
		final long startTime = System.currentTimeMillis();
		updateProgress(0, totalCount, startTime);
		progressUpdater.updateMessage("Categorizing inferred results.");
		final AtomicInteger processedCount = new AtomicInteger();
		updateTransaction = Transaction.make("Reasoner results transaction");
		EntityService.get().beginLoadPhase();
		try {
			StampEntity<?> updateStamp = updateTransaction.getStamp(State.ACTIVE,
					getViewCoordinateRecord().logicCoordinate().classifierNid() /* classifier authors inferred forms, not the user - ike-issues#753 */, getViewCoordinateRecord().getDefaultModuleNid(),
					getViewCoordinateRecord().getDefaultPathNid());
			updateStampNid = updateStamp.nid();


			inferredPattern = EntityHandle.getPatternOrThrow(getViewCoordinateRecord().logicCoordinate().inferredAxiomsPatternNid());
			inferredNavigationPattern = EntityHandle.getPatternOrThrow(KernelPattern.INFERRED_NAVIGATION_PATTERN.nid());
			// Asked by public id first: a Rocks store cannot give a nid to a pattern it does not hold.
			inferredEquivalencePattern = PrimitiveData.get().hasPublicId(KernelPattern.INFERRED_EQUIVALENCE_PATTERN.publicId())
					? EntityHandle.get(KernelPattern.INFERRED_EQUIVALENCE_PATTERN.nid()).asPattern()
							.map(pattern -> (PatternEntity<PatternEntityVersion>) pattern).orElse(null)
					: null;
			if (inferredEquivalencePattern == null) {
				LOG.info("The store has no {}; equivalences are reported but not written",
						KernelPattern.INFERRED_EQUIVALENCE_PATTERN);
			}
			equivalentSets = new ConcurrentHashSet<>();
			axiomDataNotFoundCounter = new AtomicInteger();
			emptyParentCount = new AtomicInteger();
			emptyChildCount = new AtomicInteger();
			rootConceptNid = getViewCoordinateRecord().logicCoordinate().rootNid();

			// Create more chunks than cores for work stealing
			int chunkSize = PrimitiveData.calculateOptimalChunkSize(conceptsToUpdate.size());

			AtomicInteger inferredSemanticsUpdated = new AtomicInteger();
			MutableLongList noInferredSemanticConcepts = LongLists.mutable.empty();
			AtomicInteger navigationSemanticsUpdated = new AtomicInteger();
			MutableLongList noNavigationSemanticConcepts = LongLists.mutable.empty();

			// Record to hold results from each task
			record ChunkResults(
					MutableLongList conceptsWithInferredChanges,
					MutableLongList conceptForNewInferredSemantic,
					MutableLongList conceptsWithNavigationChanges,
					MutableLongList conceptForNewNavSemantic) {

				ChunkResults() {
						this(LongLists.mutable.empty(),
								LongLists.mutable.empty(),
								LongLists.mutable.empty(),
								LongLists.mutable.empty());
					}
			}

			// Custom joiner that accumulates results as tasks complete
			StructuredTaskScope.Joiner<ChunkResults, Void, RuntimeException> accumulatingJoiner = new StructuredTaskScope.Joiner<>() {
				private final ChunkFailures failures = new ChunkFailures();

				@Override
				public Void result() {
					failures.throwIfAny();
					return null; // No final result needed
				}

				@Override
				public Void timeout() {
					// Opened without a timeout, so join() never calls this; if one is ever
					// configured, report it rather than return partial results.
					throw new IllegalStateException("inferred-results write scope timed out",
							new StructuredTaskScope.CancelledByTimeoutException());
				}

				// The scope calls onComplete from each subtask's own thread, concurrently, and the
				// master lists are unsynchronized: unserialized, concurrent addAll calls corrupted them,
				// losing chunks of results or failing with ArrayIndexOutOfBoundsException on a
				// knowledge base of SNOMED CT's size (IKE-Network/ike-issues#1293).
				@Override
				public synchronized boolean onComplete(StructuredTaskScope.Subtask<ChunkResults> subtask) {
					if (subtask.state() == StructuredTaskScope.Subtask.State.SUCCESS) {
						ChunkResults results = subtask.get();
						// Add to master lists immediately as each task completes
						conceptsWithInferredChanges.addAll(results.conceptsWithInferredChanges);
						noInferredSemanticConcepts.addAll(results.conceptForNewInferredSemantic);
						conceptsWithNavigationChanges.addAll(results.conceptsWithNavigationChanges);
						noNavigationSemanticConcepts.addAll(results.conceptForNewNavSemantic);
					} else if (subtask.state() == StructuredTaskScope.Subtask.State.FAILED) {
						failures.add(subtask.exception());
					}
					return false; // Continue processing remaining tasks
				}

				@Override
				public boolean onFork(StructuredTaskScope.Subtask<ChunkResults> subtask) {
					return false; // Don't short-circuit on fork
				}
			};
					LOG.info(String.format("Starting inferred results write. Total concepts: %,d, Chunk size: %,d",
							conceptsToUpdate.size(), chunkSize));


			Semaphore permits = new Semaphore(Runtime.getRuntime().availableProcessors() * 20);


			// Process all concepts in chunks: a concept's existing inferred and navigation semantics
			// are updated as soon as they are found, from the entity the lookup already read, rather
			// than collected and read again in a pass of their own; a concept lacking one is
			// collected for the passes below that create them.
			try (var scope = StructuredTaskScope.open(accumulatingJoiner)) {
				for (int i = 0; i < conceptsToUpdate.size(); i += chunkSize) {
					int start = i;
					int end = Math.min(i + chunkSize, conceptsToUpdate.size());
					ImmutableLongList conceptChunk = getSubList(conceptsToUpdate, start, end);

					permits.acquire();
					scope.fork(() -> {
                        try {
                            ChunkResults results = new ChunkResults();

                            EntityService.get().forEachEntity(conceptChunk, concept -> {
								progressUpdater.completedUnitOfWork();
								
								// Find canonical inferred semantic, handling duplicates gracefully
								Optional<SemanticEntity<SemanticEntityVersion>> inferredSemantic = findCanonicalSemantic(
										inferredPattern, concept);
								if (inferredSemantic.isPresent()) {
									progressUpdater.completedUnitOfWork();
									inferredSemanticsUpdated.incrementAndGet();
									updateNNF(inferredSemantic.get(), results.conceptsWithInferredChanges);
								} else {
									results.conceptForNewInferredSemantic.add(concept.nid());
								}

								// Find canonical navigation semantic, handling duplicates gracefully
								Optional<SemanticEntity<SemanticEntityVersion>> navigationSemantic = findCanonicalSemantic(
										inferredNavigationPattern, concept);
								if (navigationSemantic.isPresent()) {
									progressUpdater.completedUnitOfWork();
									navigationSemanticsUpdated.incrementAndGet();
									updateNavigation(navigationSemantic.get(), results.conceptsWithNavigationChanges);
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
			LOG.info("Inferred semantics updated: " + String.format("%,d", inferredSemanticsUpdated.get()));
			LOG.info("Concepts needing new inferred semantics: " + String.format("%,d", noInferredSemanticConcepts.size()));
			LOG.info("Navigation semantics updated: " + String.format("%,d", navigationSemanticsUpdated.get()));
			LOG.info("Concepts needing new navigation semantics: " + String.format("%,d", noNavigationSemanticConcepts.size()));
			logSampleConcepts("Concepts missing inferred semantics (sample)", noInferredSemanticConcepts, 10);
			logSampleConcepts("Concepts missing navigation semantics (sample)", noNavigationSemanticConcepts, 10);

			try {
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

				progressUpdater.updateMessage("Processing new navigation semantics");
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

				progressUpdater.updateMessage("Processing equivalences");
				MutableLongList conceptsWithEquivalenceChanges = processEntitiesInScope(
						conceptsToUpdate,
						"Processing equivalences of {} concepts in chunks of {}",
						(entity, changedConcepts) -> {
							if (entity instanceof ConceptEntity<?> conceptEntity) {
								writeEquivalence(conceptEntity, changedConcepts);
							} else {
								LOG.error("Unexpected entity type: {}", entity.getClass());
							}
						},
						permits
				);
				LOG.info("Equivalence changes: {}", String.format("%,d", conceptsWithEquivalenceChanges.size()));

			} catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
			updateTransaction.commit();

		} catch (RuntimeException | Error failure) {
			// Entities are staged into the transaction from the moment it is made until the
			// commit above, so anything thrown in between would otherwise leave uncommitted
			// stamps behind in a store other clients read. Cancelling marks them CANCELED so a
			// failed write leaves nothing half-applied.
			LOG.error("Writing inferred results failed — cancelling the transaction", failure);
			try {
				updateTransaction.cancel();
			} catch (RuntimeException cancelFailure) {
				// Report it, but let the original failure be the one that propagates.
				LOG.error("Could not cancel the reasoner results transaction", cancelFailure);
			}
			throw failure;
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

	private ImmutableLongList getSubList(LongList list, int start, int end) {
		if (start == end) {
			return LongLists.immutable.empty();
		}
		MutableLongList subList = LongLists.mutable.withInitialCapacity(end - start);
		for (int i = start; i < end; i++) {
			subList.add(list.get(i));
		}
		return subList.toImmutable();
	}


	private void updateEquivalentSets(long conceptNid) {
		ImmutableLongSet equivalentNids = rs.getEquivalent(conceptNid);
		if (equivalentNids == null) {
			LOG.error("Null node for: {} {} {} will be skipped in inferred results", conceptNid,
					PrimitiveData.publicId(conceptNid).idString(), PrimitiveData.text(conceptNid));
		} else if (equivalentNids.size() > 1) {
			equivalentSets.add(equivalentNids.toSortedList().toImmutable());
		}
	}

	/**
	 * Records the concept's equivalence class, and writes the concepts it is equivalent to, never
	 * itself, as its inferred equivalence semantic: created only when there are some, and updated
	 * (to an empty set when there are none now) whenever the latest one differs.
	 */
	private void writeEquivalence(ConceptEntity<?> concept, MutableLongList changedConcepts) {
		updateEquivalentSets(concept.nid());
		if (inferredEquivalencePattern == null) {
			return;
		}
		ImmutableLongSet equivalentNids = rs.getEquivalent(concept.nid());
		long[] others = equivalentNids == null ? new long[0]
				: equivalentNids.reject(nid -> nid == concept.nid()).toSortedArray();
		Optional<SemanticEntity<SemanticEntityVersion>> equivalenceSemantic =
				findCanonicalSemantic(inferredEquivalencePattern, concept);
		if (equivalenceSemantic.isPresent()) {
			SemanticEntity<SemanticEntityVersion> semanticEntity = equivalenceSemantic.get();
			Latest<SemanticEntityVersion> latest = rs.getViewCalculator().latest(semanticEntity);
			if (latest.isPresent() && LongSets.immutable.of(others)
					.equals(LongSets.immutable.of(((LongIdSet) latest.get().fieldValues().get(0)).toArray()))) {
				return;
			}
			processSemantic(rs.getViewCalculator().updateFields(semanticEntity.nid(),
					Lists.immutable.of(LongIds.set.of(others)), updateStampNid));
			changedConcepts.add(concept.nid());
		} else if (others.length > 0) {
			UUID equivalenceUuid = UuidT5Generator.singleSemanticUuid(inferredEquivalencePattern, concept.publicId());
			RecordListBuilder<SemanticVersionRecord> versionRecords = RecordListBuilder.make();
			long semanticNid = ScopedValue
					.where(SCOPED_PATTERN_PUBLICID_FOR_NID, inferredEquivalencePattern.publicId())
					.call(() -> PrimitiveData.nid(equivalenceUuid));
			SemanticRecord equivalenceRecord = SemanticRecordBuilder.builder()
					.nid(semanticNid)
					.referencedComponentNid(concept.nid())
					.leastSignificantBits(equivalenceUuid.getLeastSignificantBits())
					.mostSignificantBits(equivalenceUuid.getMostSignificantBits())
					.patternNid(inferredEquivalencePattern.nid()).versions(versionRecords).build();
			versionRecords.add(new SemanticVersionRecord(equivalenceRecord, updateStampNid,
					Lists.immutable.of(LongIds.set.of(others))));
			processSemantic(equivalenceRecord);
			changedConcepts.add(concept.nid());
		}
	}

	public void updateNNF(SemanticEntity<?> semanticEntity, MutableLongList changedConcepts) {
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
			DiTreeEntity latestInferredTree = (DiTreeEntity) latestInferredSemantic.get().fieldValues().get(0);
			// Written unless the normal form is the latest inferred form exactly. The isomorphic
			// analysis this replaced (a correlated tree, then equals) ran twice per concept and
			// found a form "the same" whenever the latest form mapped into it, so a normal form
			// that only added a parent or role was never written.
			same = DiTreeIsomorphism.isomorphic((DiTreeEntity) nnf.sourceGraph(), latestInferredTree);
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

		long semanticNid = ScopedValue
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

	private void updateNavigation(SemanticEntity<?> semanticEntity, MutableLongList changedConcepts) {
		ImmutableLongSet parentNids = rs.getParents(semanticEntity.referencedComponentNid());
		ImmutableLongSet childNids = rs.getChildren(semanticEntity.referencedComponentNid());
		if (parentNids == null) {
			parentNids = LongSets.immutable.of();
			childNids = LongSets.immutable.of();
			axiomDataNotFoundCounter.incrementAndGet();
		}
		logEmptyNavigationSets(semanticEntity.referencedComponentNid(), parentNids, childNids);
		Latest<SemanticEntityVersion> latestInferredNavigationSemantic = rs.getViewCalculator()
				.latest((Entity<SemanticEntityVersion>) semanticEntity);
		boolean navigationChanged = true;
		if (latestInferredNavigationSemantic.isPresent()) {
			ImmutableList<Object> latestInferredNavigationFields = latestInferredNavigationSemantic.get()
					.fieldValues();
			LongIdSet childIds = (LongIdSet) latestInferredNavigationFields.get(0);
			LongIdSet parentIds = (LongIdSet) latestInferredNavigationFields.get(1);
			if (parentNids.equals(LongSets.immutable.of(parentIds.toArray()))
					&& childNids.equals(LongSets.immutable.of(childIds.toArray()))) {
				navigationChanged = false;
			}
		}
		if (navigationChanged) {
			LongIdSet newParentIds = LongIds.set.of(parentNids.toArray());
			LongIdSet newChildIds = LongIds.set.of(childNids.toArray());
			processSemantic(rs.getViewCalculator().updateFields(semanticEntity.nid(),
					Lists.immutable.of(newChildIds, newParentIds), updateStampNid));
			changedConcepts.add(semanticEntity.referencedComponentNid());
		}
	}

	private void newNavigation(ConceptEntity concept) {
		UUID inferredNavigationUuid = UuidT5Generator.singleSemanticUuid(inferredNavigationPattern,
				concept.publicId());
		ImmutableLongSet parentNids = rs.getParents(concept.nid());
		ImmutableLongSet childNids = rs.getChildren(concept.nid());
		if (parentNids == null) {
			parentNids = LongSets.immutable.of();
			axiomDataNotFoundCounter.incrementAndGet();
		}
		if (childNids == null) {
			childNids = LongSets.immutable.of();
		}
		logEmptyNavigationSets(concept.nid(), parentNids, childNids);
		if (parentNids.notEmpty() || childNids.notEmpty()) {
			// Create a new semantic...
			RecordListBuilder<SemanticVersionRecord> versionRecords = RecordListBuilder.make();
			long semanticNid = ScopedValue
					.where(SCOPED_PATTERN_PUBLICID_FOR_NID, inferredNavigationPattern.publicId())
					.call(() -> PrimitiveData.nid(inferredNavigationUuid));

			SemanticRecord navigationRecord = SemanticRecordBuilder.builder()
					.nid(semanticNid)
					.referencedComponentNid(concept.nid())
					.leastSignificantBits(inferredNavigationUuid.getLeastSignificantBits())
					.mostSignificantBits(inferredNavigationUuid.getMostSignificantBits())
					.patternNid(inferredNavigationPattern.nid()).versions(versionRecords).build();
			LongIdSet parentIds = LongIds.set.of(parentNids.toArray());
			LongIdSet childrenIds = LongIds.set.of(childNids.toArray());
			versionRecords.add(new SemanticVersionRecord(navigationRecord, updateStampNid,
					Lists.immutable.of(childrenIds, parentIds)));
			processSemantic(navigationRecord);
		}
	}

	private LogicalExpression getNecessaryNormalFormOrStated(long conceptNid, String context) {
		LogicalExpression nnf = rs.getNecessaryNormalForm(conceptNid);
		if (nnf != null) {
			return nnf;
		}
		Latest<DiTreeEntity> statedTree = rs.getViewCalculator().getAxiomTreeForEntity(conceptNid, PremiseType.STATED);
		String recursiveEntity = EntityStringUtil.recursiveEntityToString(conceptNid);
		if (statedTree.isPresent()) {
			LOG.warn("No necessary normal form for concept nid={} ({}) when {}; using stated axioms fallback. Entity detail:\n{}",
					conceptNid, PrimitiveData.text(conceptNid), context, recursiveEntity);
			return new LogicalExpression(statedTree.get());
		}
		LOG.warn("No necessary normal form for concept nid={} ({}) when {}; stated axioms missing. Entity detail:\n{}",
				conceptNid, PrimitiveData.text(conceptNid), context, recursiveEntity);
		return null;
	}

	private void logEmptyNavigationSets(long conceptNid, ImmutableLongSet parentNids, ImmutableLongSet childNids) {
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
	 * @return Optional containing the correct semantic, or empty if none exists
	 */
	private Optional<SemanticEntity<SemanticEntityVersion>> findCanonicalSemantic(PatternEntity<?> patternEntity, Entity<?> referencedComponent) {
		// A semantic the index lists but the store does not hold is passed over; one the store
		// holds but cannot read throws, rather than reading as absent and being written again.
		List<SemanticEntity<SemanticEntityVersion>> semantics = EntityService.get().semanticsForComponentOfPattern(
				referencedComponent.nid(), patternEntity.nid()).toList();
		long[] semanticNids = semantics.stream().mapToLong(SemanticEntity::nid).toArray();
		
		if (semanticNids.length == 0) {
			return Optional.empty();
		}
		
		if (semanticNids.length == 1) {
			// Verify the single semantic belongs to the expected pattern
            SemanticEntity<SemanticEntityVersion> semantic = semantics.getFirst();
            if (semantic.patternNid() != patternEntity.nid()) {
				LOG.error("Pattern mismatch! Expected pattern {} but semantic {} has pattern {}. " +
						"Referenced component: {}. This indicates a data or indexing corruption.",
						PrimitiveData.textWithNid(patternEntity.nid()),
						PrimitiveData.textWithNid(semanticNids[0]),
						PrimitiveData.textWithNid(semantic.patternNid()),
						PrimitiveData.textWithNid(referencedComponent.nid()));
				return Optional.empty(); // Don't return mismatched semantic
			}
			return Optional.of(semantic);
		}
		
		// Multiple semantics found - need to determine which is canonical
		UUID canonicalUuid = UuidT5Generator.singleSemanticUuid(patternEntity, referencedComponent.publicId());
		
		SemanticEntity<SemanticEntityVersion> canonical = null;
		long canonicalNid = -1;
		MutableLongList duplicateNids = LongLists.mutable.empty();
		MutableLongList wrongPatternNids = LongLists.mutable.empty();
		
		for (SemanticEntity<SemanticEntityVersion> semantic : semantics) {
			long semanticNid = semantic.nid();
			
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
				canonical = semantic;
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
		
		// Residual duplicates after the pre-classification withdrawer ran. This branch
		// should be empty in steady state; if it fires, the withdrawer either failed
		// to run or new duplicates appeared mid-run — both are real surprises.
		if (duplicateNids.notEmpty()) {
			LOG.error("Residual duplicate semantic(s) after pre-classification withdraw: {} for pattern {} with component {}. " +
					"Canonical UUID: {}, Canonical NID: {}, Duplicate NIDs: {}.",
					duplicateNids.size(),
					PrimitiveData.text(patternEntity.nid()),
					PrimitiveData.text(referencedComponent.nid()),
					canonicalUuid,
					canonicalNid,
					duplicateNids.toList());
		}
		
		if (canonical != null) {
			return Optional.of(canonical);
		}
		
		// None matched the canonical UUID - this shouldn't happen but handle gracefully
		// Find first semantic with correct pattern
		for (SemanticEntity<SemanticEntityVersion> semantic : semantics) {
			long semanticNid = semantic.nid();
			if (!wrongPatternNids.contains(semanticNid)) {
				LOG.error("No semantic matched the canonical UUID {} for pattern {} and component {}. " +
						"Using first found semantic with correct pattern (nid: {}). All semantic NIDs: {}",
						canonicalUuid,
						PrimitiveData.text(patternEntity.nid()),
						PrimitiveData.text(referencedComponent.nid()),
						semanticNid,
						Arrays.toString(semanticNids));
				return Optional.of(semantic);
			}
		}
		
		// All semantics had wrong pattern - return empty
		LOG.error("All semantics had wrong pattern for pattern {} and component {}. Semantic NIDs: {}",
				PrimitiveData.text(patternEntity.nid()),
				PrimitiveData.text(referencedComponent.nid()),
				Arrays.toString(semanticNids));
		return Optional.empty();
	}

	private void logSampleConcepts(String label, MutableLongList nids, int maxSamples) {
		if (nids.isEmpty()) {
			return;
		}
		StringBuilder sample = new StringBuilder();
		int limit = Math.min(maxSamples, nids.size());
		for (int i = 0; i < limit; i++) {
			long nid = nids.get(i);
			if (sample.length() > 0) {
				sample.append(", ");
			}
			sample.append(nid).append(":").append(PrimitiveData.text(nid));
		}
		LOG.info("{}: {}", label, sample);
	}

}
