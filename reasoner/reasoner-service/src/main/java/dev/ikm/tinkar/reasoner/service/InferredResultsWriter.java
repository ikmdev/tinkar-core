package dev.ikm.tinkar.reasoner.service;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import dev.ikm.tinkar.entity.*;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.set.primitive.ImmutableIntSet;
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

public class InferredResultsWriter {

	private static final Logger LOG = LoggerFactory.getLogger(InferredResultsWriter.class);

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

	private TrackingCallable<?> progressUpdater = null;

	public InferredResultsWriter(ReasonerService rs) {
		super();
		this.rs = rs;
	}

	private ViewCoordinateRecord getViewCoordinateRecord() {
		return rs.getViewCalculator().viewCoordinateRecord();
	}

	public void setProgressUpdater(TrackingCallable<?> progressUpdater) {
		this.progressUpdater = progressUpdater;
	}

	private void updateProgress(int count, int total) {
		if (progressUpdater != null && count % 100 == 0)
			progressUpdater.updateProgress(count, total);
	}

	private void processSemantic(Entity<? extends EntityVersion> entity) {
		updateTransaction.addComponent(entity);
		Entity.provider().putEntity(entity);
	}

	public ClassifierResults write() {
		final int totalCount = rs.getReasonerConceptSet().size();
		updateProgress(0, totalCount);
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
			rs.getReasonerConceptSet().primitiveParallelStream().forEach(conceptNid -> {
				ConceptEntity<ConceptEntityVersion> concept = EntityHandle.getConceptOrThrow(conceptNid);
				updateEquivalentSets(conceptNid);
				writeNNF(concept);
				writeNavigation(concept);
				updateProgress(processedCount.incrementAndGet(), totalCount);
			});
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

	private void updateEquivalentSets(int conceptNid) {
		ImmutableIntSet equivalentNids = rs.getEquivalent(conceptNid);
		if (equivalentNids == null) {
			LOG.error("Null node for: {} {} {} will be skipped in inferred results", conceptNid,
					PrimitiveData.publicId(conceptNid).idString(), PrimitiveData.text(conceptNid));
		} else if (equivalentNids.size() > 1) {
			equivalentSets.add(equivalentNids.toSortedList().toImmutable());
		}
	}

	private void writeNNF(ConceptEntity<ConceptEntityVersion> concept) {
		LogicalExpression nnf = rs.getNecessaryNormalForm(concept.nid());
		if (nnf == null) {
			LOG.error("No NNF for " + concept
					+ " " + PrimitiveData.text(concept.nid()));
			return;
		}
		ImmutableList<Object> fields = Lists.immutable.of(nnf.sourceGraph());
		int[] inferredSemanticNids = PrimitiveData.get().semanticNidsForComponentOfPattern(concept.nid(),
				inferredPattern.nid());
		if (inferredSemanticNids.length == 0) {
			UUID uuidForSemantic = UuidT5Generator.singleSemanticUuid(inferredPattern.publicId(),
					concept.publicId());
			// Create new semantic...
			RecordListBuilder<SemanticVersionRecord> versionRecords = RecordListBuilder.make();

			int semanticNid = ScopedValue
					.where(SCOPED_PATTERN_PUBLICID_FOR_NID, inferredPattern.publicId())
					.call(() -> PrimitiveData.nid(uuidForSemantic));

			SemanticRecord semanticRecord = SemanticRecordBuilder.builder()
					.nid(semanticNid)
					.referencedComponentNid(concept.nid())
					.leastSignificantBits(uuidForSemantic.getLeastSignificantBits())
					.mostSignificantBits(uuidForSemantic.getMostSignificantBits())
					.patternNid(inferredPattern.nid()).versions(versionRecords).build();
			versionRecords.add(new SemanticVersionRecord(semanticRecord, updateStampNid, fields));
			processSemantic(semanticRecord);
			conceptsWithInferredChanges.add(concept.nid());
		} else if (inferredSemanticNids.length == 1) {
			Latest<SemanticEntityVersion> latestInferredSemantic = rs.getViewCalculator()
					.latest(inferredSemanticNids[0]);
			boolean changed = true;
			if (latestInferredSemantic.isPresent()) {
				ImmutableList<Object> latestInferredFields = latestInferredSemantic.get().fieldValues();
				DiTreeEntity latestInferredTree = (DiTreeEntity) latestInferredFields.get(0);
				DiTreeEntity correlatedTree = latestInferredTree.makeCorrelatedTree((DiTreeEntity) nnf.sourceGraph(),
						concept.nid(), multipleEndpointTimer.startNew());
				changed = correlatedTree != latestInferredTree;
			}
			if (changed) {
				processSemantic(rs.getViewCalculator().updateFields(inferredSemanticNids[0], fields, updateStampNid));
				conceptsWithInferredChanges.add(concept.nid());
			}
		} else {
			throw new IllegalStateException("More than one inferred semantic of pattern "
					+ PrimitiveData.text(inferredPattern.nid()) + "for component: " + PrimitiveData.text(concept.nid()));
		}
	}

	private void writeNavigation(ConceptEntity<ConceptEntityVersion> concept) {
		ImmutableIntSet parentNids = rs.getParents(concept.nid());
		ImmutableIntSet childNids = rs.getChildren(concept.nid());
		if (parentNids == null) {
			parentNids = IntSets.immutable.of();
			childNids = IntSets.immutable.of();
			axiomDataNotFoundCounter.incrementAndGet();
		}
		int[] inferredNavigationNids = PrimitiveData.get().semanticNidsForComponentOfPattern(concept.nid(),
				inferredNavigationPattern.nid());
		if (inferredNavigationNids.length == 0) {
			if (parentNids.notEmpty() || childNids.notEmpty()) {
				UUID uuidForSemantic = UuidT5Generator.singleSemanticUuid(inferredNavigationPattern.publicId(),
						concept.publicId());
				// Create new semantic...
				RecordListBuilder<SemanticVersionRecord> versionRecords = RecordListBuilder.make();
				int semanticNid = ScopedValue
						.where(SCOPED_PATTERN_PUBLICID_FOR_NID, inferredNavigationPattern.publicId())
						.call(() -> PrimitiveData.nid(uuidForSemantic));

				SemanticRecord navigationRecord = SemanticRecordBuilder.builder()
						.nid(semanticNid)
						.referencedComponentNid(concept.nid())
						.leastSignificantBits(uuidForSemantic.getLeastSignificantBits())
						.mostSignificantBits(uuidForSemantic.getMostSignificantBits())
						.patternNid(inferredNavigationPattern.nid()).versions(versionRecords).build();
				IntIdSet parentIds = IntIds.set.of(parentNids.toArray());
				IntIdSet childrenIds = IntIds.set.of(childNids.toArray());
				versionRecords.add(new SemanticVersionRecord(navigationRecord, updateStampNid,
						Lists.immutable.of(childrenIds, parentIds)));
				processSemantic(navigationRecord);
				conceptsWithNavigationChanges.add(concept.nid());
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
				conceptsWithNavigationChanges.add(concept.nid());
			}
		} else {
			throw new IllegalStateException(
					"More than one semantic of pattern " + PrimitiveData.text(inferredNavigationPattern.nid())
							+ "for component: " + PrimitiveData.text(concept.nid()));
		}
	}

}
