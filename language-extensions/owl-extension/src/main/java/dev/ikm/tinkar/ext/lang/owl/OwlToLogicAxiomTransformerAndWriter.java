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
package dev.ikm.tinkar.ext.lang.owl;


import dev.ikm.tinkar.common.id.IntIdSet;
import dev.ikm.tinkar.common.id.IntIds;
import dev.ikm.tinkar.common.id.impl.IntId1Set;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.coordinate.Coordinates;
import dev.ikm.tinkar.coordinate.logic.PremiseType;
import dev.ikm.tinkar.coordinate.stamp.StampBranchRecord;
import dev.ikm.tinkar.coordinate.stamp.StampCoordinateImmutable;
import dev.ikm.tinkar.coordinate.stamp.StampCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StampPosition;
import dev.ikm.tinkar.coordinate.stamp.StampPositionRecord;
import dev.ikm.tinkar.coordinate.stamp.StateSet;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.SemanticRecordBuilder;
import dev.ikm.tinkar.entity.SemanticVersionRecord;
import dev.ikm.tinkar.entity.SemanticVersionRecordBuilder;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiom;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpression;
import dev.ikm.tinkar.entity.graph.isomorphic.IsomorphicResults;
import dev.ikm.tinkar.entity.graph.isomorphic.IsomorphicResultsLeafHash;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.terms.State;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.factory.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;

public class OwlToLogicAxiomTransformerAndWriter extends TrackingCallable<Void> {

    private static final Logger LOG = LoggerFactory.getLogger(OwlToLogicAxiomTransformerAndWriter.class);

    /**
     * The never role group set.
     */
    private final IntIdSet neverRoleGroupSet = IntIds.set.of(
            TinkarTerm.PART_OF.nid(),
            TinkarTerm.LATERALITY.nid(),
            TinkarTerm.HAS_ACTIVE_INGREDIENT.nid(),
            TinkarTerm.HAS_DOSE_FORM.nid()
    );

    private final IntIdSet definingCharacteristicSet = IntIds.set.of(
            TinkarTerm.INFERRED_PREMISE_TYPE.nid(),
            TinkarTerm.STATED_PREMISE_TYPE.nid()
    );

    private final int destinationPatternNid;
    private final Semaphore writeSemaphore;
    private final List<TransformationGroup> transformationRecords;
    private Transaction transaction;
    private int authorNid = TinkarTerm.USER.nid();
    private int moduleNid = TinkarTerm.SOLOR_OVERLAY_MODULE.nid();
    private int pathNid = TinkarTerm.DEVELOPMENT_PATH.nid();

    private static final AtomicInteger foundWatchCount = new AtomicInteger(0);

    /**
	 * @param transaction           - if supplied, this does NOT commit the
	 *                              transaction. If not supplied, this creates (and
	 *                              commits) its own transaction.
     * @param transformationRecords
     * @param writeSemaphore
     */
    public OwlToLogicAxiomTransformerAndWriter(Transaction transaction, List<TransformationGroup> transformationRecords,
                                               int destinationPatternNid, Semaphore writeSemaphore) {

        this.transaction = transaction;
        this.transformationRecords = transformationRecords;
        this.destinationPatternNid = destinationPatternNid;
        this.writeSemaphore = writeSemaphore;
        this.writeSemaphore.acquireUninterruptibly();
        updateTitle("EL++ OWL transformation");
        updateMessage("");
        addToTotalWork(transformationRecords.size());
    }

    public OwlToLogicAxiomTransformerAndWriter(Transaction transaction, List<TransformationGroup> transformationRecords,
                                               int destinationPatternNid, Semaphore writeSemaphore,
                                               int authorNid, int moduleNid, int pathNid) {
        this(transaction, transformationRecords, destinationPatternNid, writeSemaphore);
        this.authorNid = authorNid;
        this.moduleNid = moduleNid;
        this.pathNid = pathNid;
    }

    @Override
    public Void compute() throws Exception {
        try {
            boolean commitTransaction = this.transaction == null;
            if (commitTransaction) {
                this.transaction = Transaction.make("OwlTransformerAndWriter");
            }
            int count = 0;

            LOG.debug("starting batch transform of {} records", transformationRecords.size());
            for (TransformationGroup transformationGroup : transformationRecords) {
                try {
                    transformOwlExpressions(transformationGroup.conceptNid, transformationGroup.semanticNids, transformationGroup.getPremiseType());
                } catch (Exception e) {
                    LOG.error("Error in Owl Transform: ", e);
                }
                if (count % 1000 == 0) {
                    updateMessage("Processing concept: " + PrimitiveData.text(transformationGroup.conceptNid));
                    LOG.trace("Processing concept: {}", PrimitiveData.text(transformationGroup.conceptNid));
                }
                count++;
                completedUnitOfWork();
            }
            if (commitTransaction) {
                transaction.commit();
            }
            LOG.debug("Finished processing batch of: {}", count);
            return null;
        } finally {
            this.writeSemaphore.release();
        }
    }

    private int[] getModulesInPriorityOrder(int moduleNid) {
        // placeholder method for retrieving module origins
        List<Integer> modulesInPriorityOrder = List.of(moduleNid);
        // add all the descendant modules here
        return modulesInPriorityOrder.stream().mapToInt(i->i).toArray();
    }

    /**
     * Transform relationships.
     *
     * @param premiseType the stated
     */
    private void transformOwlExpressions(int conceptNid, int[] owlNids, PremiseType premiseType) throws Exception {
        updateMessage("Converting " + premiseType + " Owl expressions");
//
        List<SemanticEntity<SemanticEntityVersion>> owlEntitiesForConcept = new ArrayList<>();
        Set<StampCoordinateRecord> stampCoordinates = new HashSet<>();

        for (int owlNid : owlNids) {
            SemanticEntity owlChronology = EntityService.get().getEntityFast(owlNid);
            owlEntitiesForConcept.add(owlChronology);
            for (int stampNid : owlChronology.stampNids().toArray()) {
                StampEntity stamp = EntityService.get().getStampFast(stampNid);
                int[] modulesInPriorityOrder = getModulesInPriorityOrder(stamp.moduleNid());
                StampPositionRecord stampPos = StampPositionRecord.make(stamp.time(), stamp.pathNid());
                StampCoordinateRecord stampCoordinate = StampCoordinateRecord.make(StateSet.ACTIVE, stampPos)
                        .withModuleNids(IntIds.set.of(modulesInPriorityOrder))
                        .withModulePriorityNidList(IntIds.list.of(modulesInPriorityOrder));
                stampCoordinates.add(stampCoordinate);
                stampCoordinate.modulePriorityNidList().get(0);
            }
        }

        for (StampCoordinateRecord stampCoordinate : stampCoordinates) {
            List<String> owlExpressionsToProcess = new ArrayList<>();
            for (SemanticEntity<SemanticEntityVersion> owlEntity : owlEntitiesForConcept) {
                stampCoordinate.stampCalculator().latest(owlEntity).ifPresent(latestVersion -> {
                    // TODO use pattern to get field?
                    owlExpressionsToProcess.add((String) latestVersion.fieldValues().get(0));
                });
            }

            LogicalExpression logicalExpression = null;
            OwlElExpressionToLogicalExpression transformer = new OwlElExpressionToLogicalExpression(
                    owlExpressionsToProcess, conceptNid);
            try {
                logicalExpression = transformer.build();
            } catch (Exception ex) {
                LOG.error("Error: ", ex);
            }
            if (logicalExpression != null) {
                if (logicalExpression.nodesOfType(LogicalAxiom.LogicalSet.NecessarySet.class).size() > 1) {
                    // Need to merge necessary sets.
                    LOG.warn("\n\n{} has expression with multiple necessary sets: {}\n\n",
                            PrimitiveData.text(conceptNid), logicalExpression);
                    DiTreeEntity.Builder diTreeEntityBuilder = DiTreeEntity.builder();

                }

                // See if a semantic already exists in this pattern referencing this concept...
                int[] destinationSemanticNids = EntityService.get().semanticNidsForComponentOfPattern(conceptNid, destinationPatternNid);
                switch (destinationSemanticNids.length) {
                    case 0 -> newSemanticWithVersion(conceptNid, logicalExpression, stampCoordinate);
                    case 1 -> addSemanticVersionIfAbsent(conceptNid, logicalExpression, stampCoordinate, destinationSemanticNids[0]);
                    default -> throw new IllegalStateException("To many graphs for component: " + PrimitiveData.text(conceptNid));
                }
            } // TODO: When the logical expression is null, write a version with the STAMP's original (likely Inactive) status
        }
    }

    private void newSemanticWithVersion(int conceptNid, LogicalExpression logicalExpression, StampCoordinateRecord stampCoordinate) {
        // Create UUID from seed and assign SemanticBuilder the value
        UUID generartedSemanticUuid = UuidT5Generator.singleSemanticUuid(EntityService.get().getEntityFast(destinationPatternNid),
                EntityService.get().getEntityFast(conceptNid));

        SemanticRecordBuilder newSemanticBuilder = SemanticRecordBuilder.builder()
                .mostSignificantBits(generartedSemanticUuid.getMostSignificantBits())
                .leastSignificantBits(generartedSemanticUuid.getLeastSignificantBits())
                .patternNid(destinationPatternNid)
                .referencedComponentNid(conceptNid)
                .nid(PrimitiveData.nid(generartedSemanticUuid))
                .versions(Lists.immutable.empty());

        addNewVersion(logicalExpression, newSemanticBuilder.build(), stampCoordinate);
    }

    private void addSemanticVersionIfAbsent(int conceptNid, LogicalExpression logicalExpression,
                                            StampCoordinateRecord stampCoordinate, int semanticNid) throws Exception {
        SemanticRecord existingSemantic = EntityService.get().getEntityFast(semanticNid);
        Latest<SemanticVersionRecord> latest = stampCoordinate.stampCalculator().latest(semanticNid);

        if (latest.isAbsent()) {
            addNewVersion(logicalExpression, SemanticRecordBuilder.builder(existingSemantic).build(), stampCoordinate);
        } else {
            DiTreeEntity latestExpression = (DiTreeEntity) latest.get().fieldValues().get(0);
            DiTreeEntity newExpression = (DiTreeEntity) logicalExpression.sourceGraph();

            IsomorphicResultsLeafHash isomorphicResultsComputer = new IsomorphicResultsLeafHash(latestExpression, newExpression, conceptNid);
            IsomorphicResults isomorphicResults = isomorphicResultsComputer.call();

            if (!isomorphicResults.equivalent()) {
                addNewVersion(logicalExpression, SemanticRecordBuilder.builder(existingSemantic).build(), stampCoordinate);
            }
        }
    }

    private void addNewVersion(LogicalExpression logicalExpression,
                               SemanticRecord semanticRecord, StampCoordinateRecord stampCoordinate) {
        // Allowed States should never have more than 1 element
        // TODO: Add check for StateSets with more than one element
        State status = stampCoordinate.allowedStates().toArray()[0];
        long time = stampCoordinate.time();
        // module from original stamp is the highest priority
        int moduleNid = stampCoordinate.modulePriorityNidList().get(0);
        int pathNid = stampCoordinate.pathNidForFilter();
        StampEntity transactionStamp = transaction.getStamp(status, time, authorNid, moduleNid, pathNid);

        SemanticVersionRecordBuilder semanticVersionBuilder = SemanticVersionRecordBuilder.builder()
                .fieldValues(Lists.immutable.of(logicalExpression.sourceGraph()))
                .stampNid(transactionStamp.nid())
                .chronology(semanticRecord);

        SemanticRecord newSemanticRecord = semanticRecord.analogueBuilder().with(semanticVersionBuilder.build()).build();
        EntityService.get().putEntity(newSemanticRecord);
    }
}
