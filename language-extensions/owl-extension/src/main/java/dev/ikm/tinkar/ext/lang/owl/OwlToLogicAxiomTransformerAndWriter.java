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
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.coordinate.logic.PremiseType;
import dev.ikm.tinkar.coordinate.stamp.StampCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StampPosition;
import dev.ikm.tinkar.coordinate.stamp.StampPositionRecord;
import dev.ikm.tinkar.coordinate.stamp.StateSet;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.SemanticRecordBuilder;
import dev.ikm.tinkar.entity.SemanticVersionRecord;
import dev.ikm.tinkar.entity.SemanticVersionRecordBuilder;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpression;
import dev.ikm.tinkar.entity.graph.isomorphic.IsomorphicResults;
import dev.ikm.tinkar.entity.graph.isomorphic.IsomorphicResultsLeafHash;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.terms.State;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
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
    private final int authorNid = TinkarTerm.USER.nid();
    private final int developmentPathNid = TinkarTerm.DEVELOPMENT_PATH.nid();

    private final Semaphore writeSemaphore;
    private final List<TransformationGroup> transformationRecords;
    private Transaction transaction;

    private static final AtomicInteger foundWatchCount = new AtomicInteger(0);

    /**
     * @param transaction           - if supplied, this does NOT commit the transaction.  If not supplied, this creates (and commits) its own transaction.
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
                    transformOwlExpressions(transaction, transformationGroup.conceptNid, transformationGroup.semanticNids, transformationGroup.getPremiseType());
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

    /**
     * Transform relationships.
     *
     * @param premiseType the stated
     */
    private void transformOwlExpressions(Transaction transaction, int conceptNid, int[] owlNids, PremiseType premiseType) throws Exception {
        updateMessage("Converting " + premiseType + " Owl expressions");

        List<SemanticEntity> owlEntitiesForConcept = new ArrayList<>();
        TreeSet<StampPosition> stampPositions = new TreeSet<>();

        for (int owlNid : owlNids) {
            SemanticEntity owlChronology = EntityService.get().getEntityFast(owlNid);
            owlEntitiesForConcept.add(owlChronology);
            for (int stampNid : owlChronology.stampNids().toArray()) {
                StampEntity stamp = EntityService.get().getStampFast(stampNid);
                stampPositions.add(StampPositionRecord.make(stamp.time(), stamp.pathNid()));
            }
        }

        for (StampPosition stampPosition : stampPositions) {
            StampCoordinateRecord stampCoordinateForPosition = StampCoordinateRecord.make(StateSet.ACTIVE, stampPosition);
            List<String> owlExpressionsToProcess = new ArrayList<>();
            for (SemanticEntity owlEntity : owlEntitiesForConcept) {
                Latest<SemanticEntityVersion> latestVersion = stampCoordinateForPosition.stampCalculator().latest(owlEntity);
                if (latestVersion.isPresent() && latestVersion.get().active()) {
                    SemanticEntityVersion semanticEntityVersion = latestVersion.get();
                    // TODO use pattern to get field?
                    owlExpressionsToProcess.add((String) semanticEntityVersion.fieldValues().get(0));
                }
            }
            StringBuilder propertyBuilder = new StringBuilder();
            StringBuilder classBuilder = new StringBuilder();

            for (String owlExpression : owlExpressionsToProcess) {
                if (owlExpression.toLowerCase().contains("property")) {
                    propertyBuilder.append(" ").append(owlExpression);
                    if (!owlExpression.toLowerCase().contains("objectpropertychain")) {
                        //TODO ask Michael Lawley if this is ok...
                        String tempExpression = owlExpression.toLowerCase().replace("subobjectpropertyof", " subclassof");
                        classBuilder.append(" ").append(tempExpression);
                    }
                } else {
                    classBuilder.append(" ").append(owlExpression);
                }

            }
            String owlClassExpressionsToProcess = classBuilder.toString();
            String owlPropertyExpressionsToProcess = propertyBuilder.toString();


            LogicalExpression expression = SctOwlUtilities.sctToLogicalExpression(
                    owlClassExpressionsToProcess,
                    owlPropertyExpressionsToProcess);

            addLogicalExpression(transaction, conceptNid,
                    expression,
                    stampPosition.time(),
                    TinkarTerm.SOLOR_OVERLAY_MODULE.nid(), stampCoordinateForPosition);
        }

    }

    /**
     * Adds the relationship graph.
     *
     * @param conceptNid        the conceptNid
     * @param logicalExpression the logical expression
     * @param time              the time
     * @param moduleNid         the module
     * @param stampCoordinate   for determining current version if a graph already
     */
    private void addLogicalExpression(Transaction transaction, int conceptNid,
                                      LogicalExpression logicalExpression,
                                      long time,
                                      int moduleNid, StampCoordinateRecord stampCoordinate) throws Exception {

        // See if a semantic already exists in this pattern referencing this concept...
        int[] semanticNidsForComponentOfPattern = EntityService.get().semanticNidsForComponentOfPattern(conceptNid, destinationPatternNid);
        if (semanticNidsForComponentOfPattern.length > 0) {
            if (semanticNidsForComponentOfPattern.length != 1) {
                throw new IllegalStateException("To many graphs for component: " + PrimitiveData.text(conceptNid));
            }
            SemanticRecord existingSemantic = EntityService.get().getEntityFast(semanticNidsForComponentOfPattern[0]);
            Latest<SemanticVersionRecord> latest = stampCoordinate.stampCalculator().latest(existingSemantic);

            if (latest.isPresent()) {
                SemanticEntityVersion logicalExpressionSemanticVersion = latest.get();
                DiTreeEntity latestExpression = (DiTreeEntity) logicalExpressionSemanticVersion.fieldValues().get(0);
                DiTreeEntity newExpression = (DiTreeEntity) logicalExpression.sourceGraph();

                IsomorphicResultsLeafHash isomorphicResultsComputer = new IsomorphicResultsLeafHash(latestExpression, newExpression, conceptNid);
                IsomorphicResults isomorphicResults = isomorphicResultsComputer.call();

                if (!isomorphicResults.equivalent()) {
                    addNewVersion(transaction, logicalExpression, time, moduleNid, SemanticRecordBuilder.builder(existingSemantic));
                }
            } else {
                // Latest is inactive or non-existent, need to add new.
                addNewVersion(transaction, logicalExpression, time, moduleNid, SemanticRecordBuilder.builder(existingSemantic));
            }
        } else {
// Create UUID from seed and assign SemanticBuilder the value
            UUID generartedSemanticUuid = UuidT5Generator.singleSemanticUuid(EntityService.get().getEntityFast(destinationPatternNid),
                    EntityService.get().getEntityFast(conceptNid));

            SemanticRecordBuilder newSemanticBuilder = SemanticRecordBuilder.builder();
            newSemanticBuilder.mostSignificantBits(generartedSemanticUuid.getMostSignificantBits());
            newSemanticBuilder.leastSignificantBits(generartedSemanticUuid.getLeastSignificantBits());
            newSemanticBuilder.patternNid(destinationPatternNid);
            newSemanticBuilder.referencedComponentNid(conceptNid);
            newSemanticBuilder.nid(PrimitiveData.nid(generartedSemanticUuid));

            addNewVersion(transaction, logicalExpression, time, moduleNid, newSemanticBuilder);
        }
    }

    private void addNewVersion(Transaction transaction, LogicalExpression logicalExpression,
                               long time, int moduleNid, SemanticRecordBuilder newSemanticBuilder) {

        ImmutableList<SemanticVersionRecord> oldSemanticVersions = newSemanticBuilder.versions();
        RecordListBuilder<SemanticVersionRecord> versionListBuilder = new RecordListBuilder<>();
        newSemanticBuilder.versions(versionListBuilder);
        SemanticRecord newSemantic = newSemanticBuilder.build();

        if (oldSemanticVersions != null) {
            oldSemanticVersions.forEach(version -> {
                versionListBuilder.add(SemanticVersionRecordBuilder.builder(version).chronology(newSemantic).build());
            });
        }

        SemanticVersionRecordBuilder semanticVersionBuilder = SemanticVersionRecordBuilder.builder();
        semanticVersionBuilder.fieldValues(Lists.immutable.of(logicalExpression.sourceGraph()));
        StampEntity transactionStamp = transaction.getStamp(State.ACTIVE, time, authorNid, moduleNid, developmentPathNid);
        semanticVersionBuilder.stampNid(transactionStamp.nid());
        semanticVersionBuilder.chronology(newSemantic);
        versionListBuilder.add(semanticVersionBuilder.build());
        versionListBuilder.build();

        EntityService.get().putEntity(newSemantic);
    }
}
