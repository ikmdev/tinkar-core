/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.reasoner.service;

import dev.ikm.tinkar.common.id.IntIdSet;
import dev.ikm.tinkar.common.id.IntIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.common.sets.ConcurrentHashSet;
import dev.ikm.tinkar.common.util.time.MultipleEndpointTimer;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
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
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.atomic.AtomicInteger;

import static dev.ikm.tinkar.common.service.PrimitiveData.SCOPED_PATTERN_PUBLICID_FOR_NID;
import static dev.ikm.tinkar.common.service.PrimitiveData.calculateOptimalChunkSize;

/**
 * Writes inferred classification results (NNF axioms and navigation semantics) to the database.
 * <p>
 * This class uses a <b>fault-tolerant</b> processing model: individual concept failures are logged
 * but do not halt processing of other concepts. This allows gathering comprehensive error data
 * and ensures that all processable concepts are written even when some have issues.
 * <p>
 * Processing phases:
 * <ol>
 *   <li>Categorize concepts into those needing new vs updated inferred semantics</li>
 *   <li>Create or update NNF (Necessary Normal Form) axioms for each concept</li>
 *   <li>Create or update navigation semantics (parent/child relationships)</li>
 * </ol>
 */
public class InferredResultsWriter {

	private static final Logger LOG = LoggerFactory.getLogger(InferredResultsWriter.class);

	// Regression test watch concept: Albumin (c06f68e8-9700-4992-9277-e0cc2ea23b4d)
	private final int watchNid = PrimitiveData.nid(UUID.fromString("c06f68e8-9700-4992-9277-e0cc2ea23b4d"));

	private final ReasonerService rs;
	private final TrackingCallable<?> progressUpdater;

	// Initialized during write()
	private Transaction updateTransaction;
	private int updateStampNid;
	private PatternEntity<PatternEntityVersion> inferredPattern;
	private PatternEntity<PatternEntityVersion> inferredNavigationPattern;
	private MultipleEndpointTimer<IsomorphicResults.EndPoints> multipleEndpointTimer;
	private ConcurrentHashSet<ImmutableIntList> equivalentSets;

	// Error counters for fault-tolerant processing
	private AtomicInteger axiomDataNotFoundCounter;
	private AtomicInteger nnfCreationFailureCounter;
	private AtomicInteger nnfUpdateFailureCounter;
	private AtomicInteger navCreationFailureCounter;
	private AtomicInteger navUpdateFailureCounter;

	/**
	 * Record to hold categorization results from each parallel task.
	 */
	record ChunkResults(
			MutableIntList inferredSemanticToUpdate,
			MutableIntList conceptForNewInferredSemantic,
			MutableIntList navSemanticToUpdate,
			MutableIntList conceptForNewNavSemantic) {

		ChunkResults(int initialCapacity) {
			this(IntLists.mutable.withInitialCapacity(initialCapacity),
					IntLists.mutable.withInitialCapacity(initialCapacity),
					IntLists.mutable.withInitialCapacity(initialCapacity),
					IntLists.mutable.withInitialCapacity(initialCapacity));
		}
	}

	/**
	 * Result of categorizing concepts into update vs new paths.
	 */
	record CategorizationResult(
			MutableIntList inferredSemanticNids,
			MutableIntList noInferredSemanticConcepts,
			MutableIntList navigationSemanticNids,
			MutableIntList noNavigationSemanticConcepts) {
	}

	public InferredResultsWriter(ReasonerService rs, TrackingCallable<?> progressUpdater) {
		this.rs = Objects.requireNonNull(rs, "ReasonerService cannot be null");
		this.progressUpdater = Objects.requireNonNull(progressUpdater, "progressUpdater cannot be null");
	}

	private ViewCoordinateRecord getViewCoordinateRecord() {
		return rs.getViewCalculator().viewCoordinateRecord();
	}

	/**
	 * Main entry point: writes all inferred results to the database.
	 * <p>
	 * Uses fault-tolerant processing - individual failures are logged but don't stop
	 * processing of other concepts.
	 */
	public ClassifierResults write() {
		ImmutableIntList conceptsToUpdate = rs.getReasonerConceptSet();

		logWatchConceptStatus("Initial check", conceptsToUpdate.contains(watchNid), "reasoner concept set");

		MutableIntList conceptsWithInferredChanges = IntLists.mutable.withInitialCapacity(conceptsToUpdate.size());
		MutableIntList conceptsWithNavigationChanges = IntLists.mutable.withInitialCapacity(conceptsToUpdate.size());

		final long startTime = System.currentTimeMillis();
		updateProgress(0, conceptsToUpdate.size(), startTime);
		progressUpdater.updateMessage("Categorizing inferred results.");

		updateTransaction = Transaction.make("Reasoner results transaction");
		EntityService.get().beginLoadPhase();

		try {
			initializeWriteContext();

			int chunkSize = calculateOptimalChunkSize(conceptsToUpdate.size());
			Semaphore permits = new Semaphore(Runtime.getRuntime().availableProcessors() * 20);

			// Phase 1: Categorize all concepts
			LOG.info("=== PHASE 1: Categorizing {} concepts ===", String.format("%,d", conceptsToUpdate.size()));
			CategorizationResult categorization = categorizeConceptsInParallel(conceptsToUpdate, chunkSize, permits);

			logCategorizationResults(categorization);
			verifyWatchConceptCategorization(categorization);

			// Phase 2: Process inferred semantics (update existing)
			LOG.info("=== PHASE 2: Updating existing inferred semantics ===");
			progressUpdater.updateMessage("Processing updates to inferred semantics");
			MutableIntList inferredUpdates = processUpdateInferredSemantics(
					categorization.inferredSemanticNids(), chunkSize, permits);
			conceptsWithInferredChanges.addAll(inferredUpdates);
			LOG.info("Updated {} inferred semantics.", inferredUpdates.size());

			// Phase 3: Process inferred semantics (create new)
			LOG.info("=== PHASE 3: Creating new inferred semantics ===");
			progressUpdater.updateMessage("Processing new inferred semantics");
			MutableIntList inferredNew = processNewInferredSemantics(
					categorization.noInferredSemanticConcepts(), chunkSize, permits);
			conceptsWithInferredChanges.addAll(inferredNew);
			LOG.info("Created {} new inferred semantics.", inferredNew.size());

			logWatchConceptStatus("After inferred processing",
					conceptsWithInferredChanges.contains(watchNid), "conceptsWithInferredChanges");

			// Phase 4: Process navigation semantics (update existing)
			LOG.info("=== PHASE 4: Updating existing navigation semantics ===");
			progressUpdater.updateMessage("Processing updates to navigation semantics");
			MutableIntList navUpdates = processUpdateNavigationSemantics(
					categorization.navigationSemanticNids(), chunkSize, permits);
			conceptsWithNavigationChanges.addAll(navUpdates);
			LOG.info("Updated {} navigation semantics.", navUpdates.size());

			// Phase 5: Process navigation semantics (create new)
			LOG.info("=== PHASE 5: Creating new navigation semantics ===");
			progressUpdater.updateMessage("Processing new navigation semantics");
			MutableIntList navNew = processNewNavigationSemantics(
					categorization.noNavigationSemanticConcepts(), chunkSize, permits);
			conceptsWithNavigationChanges.addAll(navNew);
			LOG.info("Created {} new navigation semantics.", navNew.size());

			logWatchConceptStatus("After navigation processing",
					conceptsWithNavigationChanges.contains(watchNid), "conceptsWithNavigationChanges");

			updateTransaction.commit();

		} catch (Exception e) {
			LOG.error("Fatal error during inferred results write - transaction will be rolled back", e);
			throw new RuntimeException("Failed to write inferred results", e);
		} finally {
			EntityService.get().endLoadPhase();
		}

		logFinalResults(conceptsWithInferredChanges, conceptsWithNavigationChanges);

		ViewCoordinateRecord commitCoordinate = getViewCoordinateRecord().withStampCoordinate(
				getViewCoordinateRecord().stampCoordinate().withStampPositionTime(updateTransaction.commitTime()));

		progressUpdater.updateMessage("Wrote inferred results in " + progressUpdater.durationString());

		return new ClassifierResults(
				rs.getReasonerConceptSet(),
				conceptsWithInferredChanges.toImmutable(),
				conceptsWithNavigationChanges.toImmutable(),
				equivalentSets,
				commitCoordinate
		);
	}

	private void initializeWriteContext() {
		StampEntity<?> updateStamp = updateTransaction.getStamp(
				State.ACTIVE,
				getViewCoordinateRecord().getAuthorNidForChanges(),
				getViewCoordinateRecord().getDefaultModuleNid(),
				getViewCoordinateRecord().getDefaultPathNid()
		);
		updateStampNid = updateStamp.nid();
		inferredPattern = EntityHandle.getPatternOrThrow(
				getViewCoordinateRecord().logicCoordinate().inferredAxiomsPatternNid());
		inferredNavigationPattern = EntityHandle.getPatternOrThrow(TinkarTerm.INFERRED_NAVIGATION_PATTERN.nid());
		multipleEndpointTimer = new MultipleEndpointTimer<>(IsomorphicResults.EndPoints.class);
		equivalentSets = new ConcurrentHashSet<>();

		// Initialize error counters
		axiomDataNotFoundCounter = new AtomicInteger();
		nnfCreationFailureCounter = new AtomicInteger();
		nnfUpdateFailureCounter = new AtomicInteger();
		navCreationFailureCounter = new AtomicInteger();
		navUpdateFailureCounter = new AtomicInteger();
	}

	// ========== CATEGORIZATION ==========

	private CategorizationResult categorizeConceptsInParallel(
			ImmutableIntList conceptsToUpdate,
			int chunkSize,
			Semaphore permits) {

		MutableIntList inferredSemanticNids = IntLists.mutable.empty();
		MutableIntList noInferredSemanticConcepts = IntLists.mutable.empty();
		MutableIntList navigationSemanticNids = IntLists.mutable.empty();
		MutableIntList noNavigationSemanticConcepts = IntLists.mutable.empty();

		LOG.info("Categorizing {} concepts in chunks of {}",
				String.format("%,d", conceptsToUpdate.size()),
				String.format("%,d", chunkSize));

		try (var scope = StructuredTaskScope.open(
				createCategorizationJoiner(
						inferredSemanticNids,
						noInferredSemanticConcepts,
						navigationSemanticNids,
						noNavigationSemanticConcepts))) {

			for (int i = 0; i < conceptsToUpdate.size(); i += chunkSize) {
				int start = i;
				int end = Math.min(i + chunkSize, conceptsToUpdate.size());
				ImmutableIntList chunk = getSubList(conceptsToUpdate, start, end);

				permits.acquire();
				scope.fork(() -> {
					try {
						return categorizeChunk(chunk);
					} finally {
						permits.release();
					}
				});
			}
			scope.join();

		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Categorization interrupted", e);
		}

		return new CategorizationResult(
				inferredSemanticNids,
				noInferredSemanticConcepts,
				navigationSemanticNids,
				noNavigationSemanticConcepts
		);
	}

	private ChunkResults categorizeChunk(ImmutableIntList conceptNids) {
		ChunkResults results = new ChunkResults(conceptNids.size());

		conceptNids.forEach(conceptNid -> {
			try {
				byte[] conceptBytes = PrimitiveData.get().getBytes(conceptNid);
				if (conceptBytes == null || conceptBytes.length == 0) {
					LOG.warn("CATEGORIZE: No bytes found for concept nid {} ({})",
							conceptNid, PrimitiveData.text(conceptNid));
					return;
				}

				Entity<EntityVersion> concept = EntityRecordFactory.make(conceptBytes);
				progressUpdater.completedUnitOfWork();

				final boolean isWatchConcept = concept.nid() == watchNid;
				if (isWatchConcept) {
					LOG.info("CATEGORIZE: Processing watch concept {}", PrimitiveData.text(concept.nid()));
				}

				categorizeForInferredSemantic(concept, results, isWatchConcept);
				categorizeForNavigationSemantic(concept, results, isWatchConcept);

			} catch (Exception e) {
				LOG.error("CATEGORIZE: Error processing concept nid {} ({})",
						conceptNid, PrimitiveData.text(conceptNid), e);
			}
		});

		return results;
	}

	private void categorizeForInferredSemantic(Entity<?> concept, ChunkResults results, boolean logDetails) {
		UUID inferredSemanticUuid = UuidT5Generator.singleSemanticUuid(inferredPattern, concept.publicId());

		if (logDetails) {
			LOG.info("CATEGORIZE: Concept {} inferred semantic UUID: {}",
					PrimitiveData.text(concept.nid()), inferredSemanticUuid);
		}

		if (PrimitiveData.get().hasUuid(inferredSemanticUuid)) {
			int semanticNid = PrimitiveData.nid(inferredSemanticUuid);
			byte[] semanticBytes = PrimitiveData.get().getBytes(semanticNid);

			if (semanticBytes != null && semanticBytes.length > 0) {
				if (logDetails) {
					LOG.info("CATEGORIZE: Concept {} -> UPDATE path (semantic exists, {} bytes)",
							PrimitiveData.text(concept.nid()), semanticBytes.length);
				}
				results.inferredSemanticToUpdate.add(semanticNid);
			} else {
				if (logDetails) {
					LOG.info("CATEGORIZE: Concept {} -> NEW path (UUID exists but no bytes)",
							PrimitiveData.text(concept.nid()));
				}
				results.conceptForNewInferredSemantic.add(concept.nid());
			}
		} else {
			if (logDetails) {
				LOG.info("CATEGORIZE: Concept {} -> NEW path (no UUID mapping)",
						PrimitiveData.text(concept.nid()));
			}
			results.conceptForNewInferredSemantic.add(concept.nid());
		}
	}

	private void categorizeForNavigationSemantic(Entity<?> concept, ChunkResults results, boolean logDetails) {
		UUID navSemanticUuid = UuidT5Generator.singleSemanticUuid(inferredNavigationPattern, concept.publicId());

		if (logDetails) {
			LOG.info("CATEGORIZE: Concept {} navigation semantic UUID: {}",
					PrimitiveData.text(concept.nid()), navSemanticUuid);
		}

		if (PrimitiveData.get().hasUuid(navSemanticUuid)) {
			int navSemanticNid = PrimitiveData.nid(navSemanticUuid);
			byte[] navBytes = PrimitiveData.get().getBytes(navSemanticNid);

			if (navBytes != null && navBytes.length > 0) {
				if (logDetails) {
					LOG.info("CATEGORIZE: Concept {} nav -> UPDATE path (semantic exists, {} bytes)",
							PrimitiveData.text(concept.nid()), navBytes.length);
				}
				results.navSemanticToUpdate.add(navSemanticNid);
			} else {
				if (logDetails) {
					LOG.info("CATEGORIZE: Concept {} nav -> NEW path (UUID exists but no bytes)",
							PrimitiveData.text(concept.nid()));
				}
				results.conceptForNewNavSemantic.add(concept.nid());
			}
		} else {
			if (logDetails) {
				LOG.info("CATEGORIZE: Concept {} nav -> NEW path (no UUID mapping)",
						PrimitiveData.text(concept.nid()));
			}
			results.conceptForNewNavSemantic.add(concept.nid());
		}
	}

	private StructuredTaskScope.Joiner<ChunkResults, Void> createCategorizationJoiner(
			MutableIntList inferredSemanticNids,
			MutableIntList noInferredSemanticConcepts,
			MutableIntList navigationSemanticNids,
			MutableIntList noNavigationSemanticConcepts) {

		return new StructuredTaskScope.Joiner<>() {
			@Override
			public Void result() {
				return null;
			}

			@Override
			public boolean onComplete(StructuredTaskScope.Subtask<? extends ChunkResults> subtask) {
				if (subtask.state() == StructuredTaskScope.Subtask.State.SUCCESS) {
					ChunkResults results = subtask.get();
					synchronized (inferredSemanticNids) {
						inferredSemanticNids.addAll(results.inferredSemanticToUpdate);
					}
					synchronized (noInferredSemanticConcepts) {
						noInferredSemanticConcepts.addAll(results.conceptForNewInferredSemantic);
					}
					synchronized (navigationSemanticNids) {
						navigationSemanticNids.addAll(results.navSemanticToUpdate);
					}
					synchronized (noNavigationSemanticConcepts) {
						noNavigationSemanticConcepts.addAll(results.conceptForNewNavSemantic);
					}
				} else if (subtask.state() == StructuredTaskScope.Subtask.State.FAILED) {
					LOG.error("Categorization subtask failed", subtask.exception());
				}
				return false; // Continue processing all tasks
			}

			@Override
			public boolean onFork(StructuredTaskScope.Subtask<? extends ChunkResults> subtask) {
				return false;
			}
		};
	}

	// ========== INFERRED SEMANTIC PROCESSING ==========

	private MutableIntList processUpdateInferredSemantics(IntList semanticNids, int chunkSize, Semaphore permits) {
		MutableIntList changedConcepts = IntLists.mutable.empty();

		LOG.info("Processing updates to {} inferred semantic nids in chunks of {}",
				String.format("%,d", semanticNids.size()), String.format("%,d", chunkSize));

		if (semanticNids.isEmpty()) {
			LOG.info("No inferred semantics to update.");
			return changedConcepts;
		}

		try (var scope = StructuredTaskScope.open(createProcessingJoiner(changedConcepts))) {
			for (int i = 0; i < semanticNids.size(); i += chunkSize) {
				int start = i;
				int end = Math.min(i + chunkSize, semanticNids.size());
				ImmutableIntList chunk = getSubList(semanticNids, start, end);

				permits.acquire();
				scope.fork(() -> {
					try {
						return processUpdateInferredChunk(chunk);
					} finally {
						permits.release();
					}
				});
			}
			scope.join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Inferred semantic update interrupted", e);
		}

		return changedConcepts;
	}

	private MutableIntList processUpdateInferredChunk(ImmutableIntList semanticNids) {
		MutableIntList localChanges = IntLists.mutable.empty();

		semanticNids.forEach(semanticNid -> {
			try {
				byte[] bytes = PrimitiveData.get().getBytes(semanticNid);
				if (bytes == null || bytes.length == 0) {
					LOG.warn("UPDATE_INFERRED: No bytes for semantic nid {}", semanticNid);
					return;
				}

				Entity<EntityVersion> entity = EntityRecordFactory.make(bytes);
				if (!(entity instanceof SemanticEntity<?> semanticEntity)) {
					LOG.error("UPDATE_INFERRED: Expected SemanticEntity but got {}", entity.getClass().getSimpleName());
					return;
				}

				final boolean isWatch = semanticEntity.referencedComponentNid() == watchNid;
				if (isWatch) {
					LOG.info("UPDATE_INFERRED: ENTERING for concept {}", PrimitiveData.text(semanticEntity.referencedComponentNid()));
				}

				progressUpdater.completedUnitOfWork();
				boolean success = tryUpdateNNF(semanticEntity, localChanges);

				if (isWatch) {
					LOG.info("UPDATE_INFERRED: EXITED for concept {}, success={}, inChanges={}",
							PrimitiveData.text(semanticEntity.referencedComponentNid()),
							success, localChanges.contains(watchNid));
				}

			} catch (Exception e) {
				LOG.error("UPDATE_INFERRED: Error processing semantic nid {}", semanticNid, e);
				nnfUpdateFailureCounter.incrementAndGet();
			}
		});

		return localChanges;
	}

	private MutableIntList processNewInferredSemantics(IntList conceptNids, int chunkSize, Semaphore permits) {
		MutableIntList changedConcepts = IntLists.mutable.empty();

		LOG.info("Processing {} new inferred semantics in chunks of {}",
				String.format("%,d", conceptNids.size()), String.format("%,d", chunkSize));

		if (conceptNids.isEmpty()) {
			LOG.info("No new inferred semantics to create.");
			return changedConcepts;
		}

		try (var scope = StructuredTaskScope.open(createProcessingJoiner(changedConcepts))) {
			for (int i = 0; i < conceptNids.size(); i += chunkSize) {
				int start = i;
				int end = Math.min(i + chunkSize, conceptNids.size());
				ImmutableIntList chunk = getSubList(conceptNids, start, end);

				permits.acquire();
				scope.fork(() -> {
					try {
						return processNewInferredChunk(chunk);
					} finally {
						permits.release();
					}
				});
			}
			scope.join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Inferred semantic creation interrupted", e);
		}

		return changedConcepts;
	}

	private MutableIntList processNewInferredChunk(ImmutableIntList conceptNids) {
		MutableIntList localChanges = IntLists.mutable.empty();

		conceptNids.forEach(conceptNid -> {
			try {
				byte[] bytes = PrimitiveData.get().getBytes(conceptNid);
				if (bytes == null || bytes.length == 0) {
					LOG.warn("NEW_INFERRED: No bytes for concept nid {} ({})", conceptNid, PrimitiveData.text(conceptNid));
					return;
				}

				Entity<EntityVersion> entity = EntityRecordFactory.make(bytes);
				if (!(entity instanceof ConceptEntity<?> conceptEntity)) {
					LOG.error("NEW_INFERRED: Expected ConceptEntity but got {}", entity.getClass().getSimpleName());
					return;
				}

				final boolean isWatch = conceptNid == watchNid;
				if (isWatch) {
					LOG.info("NEW_INFERRED: ENTERING for concept {}", PrimitiveData.text(conceptNid));
				}

				progressUpdater.completedUnitOfWork();
				boolean success = tryNewNNF(conceptEntity);

				if (success) {
					localChanges.add(conceptNid);
					if (isWatch) {
						LOG.info("NEW_INFERRED: SUCCESS for concept {}, added to changes",
								PrimitiveData.text(conceptNid));
					}
				} else {
					if (isWatch) {
						LOG.error("NEW_INFERRED: FAILED for concept {} - NNF creation failed",
								PrimitiveData.text(conceptNid));
					}
				}

			} catch (Exception e) {
				LOG.error("NEW_INFERRED: Error processing concept nid {} ({})",
						conceptNid, PrimitiveData.text(conceptNid), e);
				nnfCreationFailureCounter.incrementAndGet();
			}
		});

		return localChanges;
	}

	// ========== NAVIGATION SEMANTIC PROCESSING ==========

	private MutableIntList processUpdateNavigationSemantics(IntList semanticNids, int chunkSize, Semaphore permits) {
		MutableIntList changedConcepts = IntLists.mutable.empty();

		LOG.info("Processing updates to {} navigation semantic nids in chunks of {}",
				String.format("%,d", semanticNids.size()), String.format("%,d", chunkSize));

		if (semanticNids.isEmpty()) {
			LOG.info("No navigation semantics to update.");
			return changedConcepts;
		}

		try (var scope = StructuredTaskScope.open(createProcessingJoiner(changedConcepts))) {
			for (int i = 0; i < semanticNids.size(); i += chunkSize) {
				int start = i;
				int end = Math.min(i + chunkSize, semanticNids.size());
				ImmutableIntList chunk = getSubList(semanticNids, start, end);

				permits.acquire();
				scope.fork(() -> {
					try {
						return processUpdateNavigationChunk(chunk);
					} finally {
						permits.release();
					}
				});
			}
			scope.join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Navigation semantic update interrupted", e);
		}

		return changedConcepts;
	}

	private MutableIntList processUpdateNavigationChunk(ImmutableIntList semanticNids) {
		MutableIntList localChanges = IntLists.mutable.empty();

		semanticNids.forEach(semanticNid -> {
			try {
				byte[] bytes = PrimitiveData.get().getBytes(semanticNid);
				if (bytes == null || bytes.length == 0) {
					LOG.warn("UPDATE_NAV: No bytes for semantic nid {}", semanticNid);
					return;
				}

				Entity<EntityVersion> entity = EntityRecordFactory.make(bytes);
				if (!(entity instanceof SemanticEntity<?> semanticEntity)) {
					LOG.error("UPDATE_NAV: Expected SemanticEntity but got {}", entity.getClass().getSimpleName());
					return;
				}

				final boolean isWatch = semanticEntity.referencedComponentNid() == watchNid;
				if (isWatch) {
					LOG.info("UPDATE_NAV: ENTERING for concept {}", PrimitiveData.text(semanticEntity.referencedComponentNid()));
				}

				progressUpdater.completedUnitOfWork();
				boolean success = tryUpdateNavigation(semanticEntity, localChanges);

				if (isWatch) {
					LOG.info("UPDATE_NAV: EXITED for concept {}, success={}, inChanges={}",
							PrimitiveData.text(semanticEntity.referencedComponentNid()),
							success, localChanges.contains(watchNid));
				}

			} catch (Exception e) {
				LOG.error("UPDATE_NAV: Error processing semantic nid {}", semanticNid, e);
				navUpdateFailureCounter.incrementAndGet();
			}
		});

		return localChanges;
	}

	private MutableIntList processNewNavigationSemantics(IntList conceptNids, int chunkSize, Semaphore permits) {
		MutableIntList changedConcepts = IntLists.mutable.empty();

		LOG.info("Processing {} new navigation semantics in chunks of {}",
				String.format("%,d", conceptNids.size()), String.format("%,d", chunkSize));

		if (conceptNids.isEmpty()) {
			LOG.info("No new navigation semantics to create.");
			return changedConcepts;
		}

		try (var scope = StructuredTaskScope.open(createProcessingJoiner(changedConcepts))) {
			for (int i = 0; i < conceptNids.size(); i += chunkSize) {
				int start = i;
				int end = Math.min(i + chunkSize, conceptNids.size());
				ImmutableIntList chunk = getSubList(conceptNids, start, end);

				permits.acquire();
				scope.fork(() -> {
					try {
						return processNewNavigationChunk(chunk);
					} finally {
						permits.release();
					}
				});
			}
			scope.join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new RuntimeException("Navigation semantic creation interrupted", e);
		}

		return changedConcepts;
	}

	private MutableIntList processNewNavigationChunk(ImmutableIntList conceptNids) {
		MutableIntList localChanges = IntLists.mutable.empty();

		conceptNids.forEach(conceptNid -> {
			try {
				byte[] bytes = PrimitiveData.get().getBytes(conceptNid);
				if (bytes == null || bytes.length == 0) {
					LOG.warn("NEW_NAV: No bytes for concept nid {} ({})", conceptNid, PrimitiveData.text(conceptNid));
					return;
				}

				Entity<EntityVersion> entity = EntityRecordFactory.make(bytes);
				if (!(entity instanceof ConceptEntity<?> conceptEntity)) {
					LOG.error("NEW_NAV: Expected ConceptEntity but got {}", entity.getClass().getSimpleName());
					return;
				}

				final boolean isWatch = conceptNid == watchNid;
				if (isWatch) {
					LOG.info("NEW_NAV: ENTERING for concept {}", PrimitiveData.text(conceptNid));
				}

				progressUpdater.completedUnitOfWork();
				boolean success = tryNewNavigation(conceptEntity);

				if (success) {
					localChanges.add(conceptNid);
					if (isWatch) {
						LOG.info("NEW_NAV: SUCCESS for concept {}, added to changes",
								PrimitiveData.text(conceptNid));
					}
				} else {
					// Not an error - concept may have no parents/children
					if (isWatch) {
						LOG.info("NEW_NAV: No navigation created for concept {} (no parents/children)",
								PrimitiveData.text(conceptNid));
					}
				}

			} catch (Exception e) {
				LOG.error("NEW_NAV: Error processing concept nid {} ({})",
						conceptNid, PrimitiveData.text(conceptNid), e);
				navCreationFailureCounter.incrementAndGet();
			}
		});

		return localChanges;
	}

	// ========== JOINER FOR PROCESSING ==========

	private StructuredTaskScope.Joiner<MutableIntList, MutableIntList> createProcessingJoiner(
			MutableIntList accumulator) {

		return new StructuredTaskScope.Joiner<>() {
			@Override
			public MutableIntList result() {
				return accumulator;
			}

			@Override
			public boolean onComplete(StructuredTaskScope.Subtask<? extends MutableIntList> subtask) {
				if (subtask.state() == StructuredTaskScope.Subtask.State.SUCCESS) {
					synchronized (accumulator) {
						accumulator.addAll(subtask.get());
					}
				} else if (subtask.state() == StructuredTaskScope.Subtask.State.FAILED) {
					// Log but don't fail - fault tolerant processing
					LOG.error("Processing subtask failed (continuing with other tasks)", subtask.exception());
				}
				return false; // Continue processing all tasks
			}

			@Override
			public boolean onFork(StructuredTaskScope.Subtask<? extends MutableIntList> subtask) {
				return false;
			}
		};
	}

	// ========== NNF OPERATIONS ==========

	/**
	 * Attempts to update the NNF for an existing semantic.
	 * Returns true if update was performed (or no update needed), false on error.
	 */
	private boolean tryUpdateNNF(SemanticEntity<?> semanticEntity, MutableIntList changedConcepts) {
		final boolean isWatch = semanticEntity.referencedComponentNid() == watchNid;
		if (isWatch) {
			LOG.info("tryUpdateNNF: Processing concept {}", PrimitiveData.text(semanticEntity.referencedComponentNid()));
		}

		try {
			Latest<SemanticEntityVersion> latestInferredSemantic =
					(Latest<SemanticEntityVersion>) rs.getViewCalculator().latest(semanticEntity);

			LogicalExpression nnf = rs.getNecessaryNormalForm(semanticEntity.referencedComponentNid());
			if (nnf == null) {
				LOG.error("tryUpdateNNF: No NNF found for concept {} (nid={})",
						PrimitiveData.text(semanticEntity.referencedComponentNid()),
						semanticEntity.referencedComponentNid());
				nnfUpdateFailureCounter.incrementAndGet();
				return false;
			}

			boolean same = true;
			if (latestInferredSemantic.isPresent()) {
				ImmutableList<Object> latestInferredFields = latestInferredSemantic.get().fieldValues();
				DiTreeEntity latestInferredTree = (DiTreeEntity) latestInferredFields.get(0);
				DiTreeEntity correlatedTree = latestInferredTree.makeCorrelatedTree(
						(DiTreeEntity) nnf.sourceGraph(),
						semanticEntity.referencedComponentNid(),
						multipleEndpointTimer.startNew()
				);
				same = correlatedTree.equals(latestInferredTree);
			}

			if (!same) {
				ImmutableList<Object> fields = Lists.immutable.of(nnf.sourceGraph());
				processSemantic(rs.getViewCalculator().updateFields(semanticEntity.nid(), fields, updateStampNid));
				changedConcepts.add(semanticEntity.referencedComponentNid());

				if (isWatch) {
					LOG.info("tryUpdateNNF: Updated NNF for concept {} (was different)",
							PrimitiveData.text(semanticEntity.referencedComponentNid()));
				}
			} else if (isWatch) {
				LOG.info("tryUpdateNNF: NNF unchanged for concept {}",
						PrimitiveData.text(semanticEntity.referencedComponentNid()));
			}

			return true;

		} catch (Exception e) {
			LOG.error("tryUpdateNNF: Exception updating NNF for concept {} (nid={})",
					PrimitiveData.text(semanticEntity.referencedComponentNid()),
					semanticEntity.referencedComponentNid(), e);
			nnfUpdateFailureCounter.incrementAndGet();
			return false;
		}
	}

	/**
	 * Attempts to create a new NNF for a concept.
	 * Returns true on success, false on failure (logs the error).
	 */
	private boolean tryNewNNF(ConceptEntity<?> concept) {
		final boolean isWatch = concept.nid() == watchNid;
		if (isWatch) {
			LOG.info("tryNewNNF: Attempting to create new NNF for concept {}", PrimitiveData.text(concept.nid()));
		}

		LogicalExpression nnf = rs.getNecessaryNormalForm(concept.nid());
		if (nnf == null) {
			LOG.error("tryNewNNF: Required NNF missing for concept: {} (nid={})",
					PrimitiveData.text(concept.nid()), concept.nid());
			nnfCreationFailureCounter.incrementAndGet();
			return false;
		}

		try {
			ImmutableList<Object> fields = Lists.immutable.of(nnf.sourceGraph());
			UUID inferredSemanticUuid = UuidT5Generator.singleSemanticUuid(inferredPattern, concept.publicId());

			RecordListBuilder<SemanticVersionRecord> versionRecords = RecordListBuilder.make();

			int semanticNid = ScopedValue
					.where(SCOPED_PATTERN_PUBLICID_FOR_NID, inferredPattern.publicId())
					.call(() -> PrimitiveData.nid(inferredSemanticUuid));

			SemanticRecord semanticRecord = SemanticRecordBuilder.builder()
					.nid(semanticNid)
					.referencedComponentNid(concept.nid())
					.leastSignificantBits(inferredSemanticUuid.getLeastSignificantBits())
					.mostSignificantBits(inferredSemanticUuid.getMostSignificantBits())
					.patternNid(inferredPattern.nid())
					.versions(versionRecords)
					.build();

			versionRecords.add(new SemanticVersionRecord(semanticRecord, updateStampNid, fields));
			processSemantic(semanticRecord);

			if (isWatch) {
				LOG.info("tryNewNNF: Created new inferred semantic {} for concept {}",
						inferredSemanticUuid, PrimitiveData.text(concept.nid()));
			}
			return true;

		} catch (Exception e) {
			LOG.error("tryNewNNF: Exception creating NNF for concept: {} (nid={})",
					PrimitiveData.text(concept.nid()), concept.nid(), e);
			nnfCreationFailureCounter.incrementAndGet();
			return false;
		}
	}

	// ========== NAVIGATION OPERATIONS ==========

	/**
	 * Attempts to update navigation for an existing semantic.
	 * Returns true if update was performed (or no update needed), false on error.
	 */
	private boolean tryUpdateNavigation(SemanticEntity<?> semanticEntity, MutableIntList changedConcepts) {
		try {
			ImmutableIntSet parentNids = rs.getParents(semanticEntity.referencedComponentNid());
			ImmutableIntSet childNids = rs.getChildren(semanticEntity.referencedComponentNid());

			if (parentNids == null) {
				parentNids = IntSets.immutable.of();
				childNids = IntSets.immutable.of();
				axiomDataNotFoundCounter.incrementAndGet();
			}

			Latest<SemanticEntityVersion> latestNav = rs.getViewCalculator()
					.latest((Entity<SemanticEntityVersion>) semanticEntity);

			boolean navigationChanged = true;
			if (latestNav.isPresent()) {
				ImmutableList<Object> latestFields = latestNav.get().fieldValues();
				IntIdSet childIds = (IntIdSet) latestFields.get(0);
				IntIdSet parentIds = (IntIdSet) latestFields.get(1);

				if (parentNids.equals(IntSets.immutable.of(parentIds.toArray()))
						&& childNids.equals(IntSets.immutable.of(childIds.toArray()))) {
					navigationChanged = false;
				}
			}

			if (navigationChanged) {
				IntIdSet newParentIds = IntIds.set.of(parentNids.toArray());
				IntIdSet newChildIds = IntIds.set.of(childNids.toArray());
				processSemantic(rs.getViewCalculator().updateFields(
						semanticEntity.nid(),
						Lists.immutable.of(newChildIds, newParentIds),
						updateStampNid
				));
				changedConcepts.add(semanticEntity.referencedComponentNid());
			}

			return true;

		} catch (Exception e) {
			LOG.error("tryUpdateNavigation: Exception updating navigation for concept {} (nid={})",
					PrimitiveData.text(semanticEntity.referencedComponentNid()),
					semanticEntity.referencedComponentNid(), e);
			navUpdateFailureCounter.incrementAndGet();
			return false;
		}
	}

	/**
	 * Attempts to create new navigation for a concept.
	 * Returns true if navigation was created, false if not created (may be normal if no parents/children).
	 */
	private boolean tryNewNavigation(ConceptEntity<?> concept) {
		try {
			UUID inferredNavigationUuid = UuidT5Generator.singleSemanticUuid(inferredNavigationPattern, concept.publicId());

			ImmutableIntSet parentNids = rs.getParents(concept.nid());
			ImmutableIntSet childNids = rs.getChildren(concept.nid());

			if (parentNids == null) {
				parentNids = IntSets.immutable.of();
				axiomDataNotFoundCounter.incrementAndGet();
			}
			if (childNids == null) {
				childNids = IntSets.immutable.of();
			}

			if (parentNids.notEmpty() || childNids.notEmpty()) {
				RecordListBuilder<SemanticVersionRecord> versionRecords = RecordListBuilder.make();

				int semanticNid = ScopedValue
						.where(SCOPED_PATTERN_PUBLICID_FOR_NID, inferredNavigationPattern.publicId())
						.call(() -> PrimitiveData.nid(inferredNavigationUuid));

				SemanticRecord navigationRecord = SemanticRecordBuilder.builder()
						.nid(semanticNid)
						.referencedComponentNid(concept.nid())
						.leastSignificantBits(inferredNavigationUuid.getLeastSignificantBits())
						.mostSignificantBits(inferredNavigationUuid.getMostSignificantBits())
						.patternNid(inferredNavigationPattern.nid())
						.versions(versionRecords)
						.build();

				IntIdSet parentIds = IntIds.set.of(parentNids.toArray());
				IntIdSet childrenIds = IntIds.set.of(childNids.toArray());

				versionRecords.add(new SemanticVersionRecord(
						navigationRecord,
						updateStampNid,
						Lists.immutable.of(childrenIds, parentIds)
				));

				processSemantic(navigationRecord);
				return true;
			}

			// No parents/children - nothing to create (not an error)
			return false;

		} catch (Exception e) {
			LOG.error("tryNewNavigation: Exception creating navigation for concept: {} (nid={})",
					PrimitiveData.text(concept.nid()), concept.nid(), e);
			navCreationFailureCounter.incrementAndGet();
			return false;
		}
	}

	// ========== UTILITY METHODS ==========

	private void processSemantic(Entity<? extends EntityVersion> entity) {
		updateTransaction.addComponent(entity);
		Entity.provider().putEntityNoCache(entity);
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

	private void updateProgress(int count, int total, long startTime) {
		if (count % 1000 == 0) {
			long elapsed = System.currentTimeMillis() - startTime;
			String msg = String.format("Processing %,d inferred result items", total);

			if (elapsed > 0 && count > 0) {
				double rate = (double) count / elapsed;
				double remainingItems = total - count;
				long remainingMillis = (long) (remainingItems / rate);
				double percent = (count / (double) total) * 100;
				msg = String.format("Processing inferred results: %,.2f%% - ETA: %s",
						percent, formatDuration(remainingMillis));
			}

			progressUpdater.updateProgress(count, total);
			progressUpdater.updateMessage(msg);
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

	// ========== LOGGING HELPERS ==========

	private void logWatchConceptStatus(String phase, boolean present, String collectionName) {
		if (present) {
			LOG.info("[{}] Watch concept {} IS in {}", phase, PrimitiveData.text(watchNid), collectionName);
		} else {
			LOG.info("[{}] Watch concept {} is NOT in {}", phase, PrimitiveData.text(watchNid), collectionName);
		}
	}

	private void logCategorizationResults(CategorizationResult cat) {
		LOG.info("=== Categorization Results ===");
		LOG.info("  Inferred semantics to UPDATE: {}", String.format("%,d", cat.inferredSemanticNids().size()));
		LOG.info("  Concepts needing NEW inferred: {}", String.format("%,d", cat.noInferredSemanticConcepts().size()));
		LOG.info("  Navigation semantics to UPDATE: {}", String.format("%,d", cat.navigationSemanticNids().size()));
		LOG.info("  Concepts needing NEW navigation: {}", String.format("%,d", cat.noNavigationSemanticConcepts().size()));
	}

	private void verifyWatchConceptCategorization(CategorizationResult cat) {
		boolean inNewInferred = cat.noInferredSemanticConcepts().contains(watchNid);
		boolean inUpdateInferred = cat.inferredSemanticNids().anySatisfy(nid -> {
			byte[] bytes = PrimitiveData.get().getBytes(nid);
			if (bytes != null) {
				Entity<?> entity = EntityRecordFactory.make(bytes);
				if (entity instanceof SemanticEntity<?> sem) {
					return sem.referencedComponentNid() == watchNid;
				}
			}
			return false;
		});

		LOG.info("=== Watch Concept Verification ===");
		LOG.info("  Watch concept {} in noInferredSemanticConcepts: {}",
				PrimitiveData.text(watchNid), inNewInferred);
		LOG.info("  Watch concept {} has semantic in inferredSemanticNids: {}",
				PrimitiveData.text(watchNid), inUpdateInferred);

		if (!inNewInferred && !inUpdateInferred) {
			LOG.error("VERIFICATION FAILED: Watch concept {} not found in ANY categorization list!",
					PrimitiveData.text(watchNid));
		}
	}

	private void logFinalResults(MutableIntList inferredChanges, MutableIntList navChanges) {
		LOG.info("=== Final Results ===");
		LOG.info("  Total inferred changes: {}", String.format("%,d", inferredChanges.size()));
		LOG.info("  Total navigation changes: {}", String.format("%,d", navChanges.size()));
		LOG.info("  Navigation semantics not in AxiomData: {}", axiomDataNotFoundCounter.get());
		LOG.info("=== Error Summary ===");
		LOG.info("  NNF creation failures: {}", nnfCreationFailureCounter.get());
		LOG.info("  NNF update failures: {}", nnfUpdateFailureCounter.get());
		LOG.info("  Navigation creation failures: {}", navCreationFailureCounter.get());
		LOG.info("  Navigation update failures: {}", navUpdateFailureCounter.get());

		int totalFailures = nnfCreationFailureCounter.get() + nnfUpdateFailureCounter.get()
				+ navCreationFailureCounter.get() + navUpdateFailureCounter.get();
		if (totalFailures > 0) {
			LOG.warn("WARNING: {} total failures occurred during processing - check logs above for details", totalFailures);
		}
	}
}