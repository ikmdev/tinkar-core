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


import dev.ikm.tinkar.common.id.IntIdList;
import dev.ikm.tinkar.common.id.IntIdSet;
import dev.ikm.tinkar.common.id.IntIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.coordinate.logic.PremiseType;
import dev.ikm.tinkar.coordinate.stamp.StampCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StampPositionRecord;
import dev.ikm.tinkar.coordinate.stamp.StateSet;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.SemanticRecordBuilder;
import dev.ikm.tinkar.entity.SemanticVersionRecordBuilder;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampEntityVersion;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiom;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpression;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.terms.State;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.factory.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
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

    private final Map<UUID, IntIdSet> modulePrecedenceSetMap = new HashMap<>();
    private final Map<UUID, IntIdList> modulePrecedenceListMap = new HashMap<>();
    private final Map<String, StampPositionRecord> stampPositionRecordMap = new HashMap<>();

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

    private void populateMapsWithModulePrecedence(int stampNid) {
        StampEntity<? extends StampEntityVersion> stamp = EntityService.get().getStampFast(stampNid);
        UUID moduleKey = modulePrecedenceKey(stamp);
        String stampPosKey = stampPositionKey(stamp);
        if (modulePrecedenceListMap.containsKey(moduleKey) &&
                modulePrecedenceSetMap.containsKey(moduleKey) &&
                stampPositionRecordMap.containsKey(stampPosKey)) {
            return;
        }
        List<Integer> modulesInPriorityOrder = new ArrayList<>();
        StampPositionRecord stampPos = StampPositionRecord.make(stamp.time(), stamp.pathNid());
        StampCoordinateRecord stampCoordinate = StampCoordinateRecord.make(StateSet.ACTIVE, stampPos);
        StampCalculator stampCalc = stampCoordinate.stampCalculator();
        // Aggregate Origin/Extended Modules
        Stack<Integer> stack = new Stack<>(); // TODO: Convert to more efficient data structure (i.e., Deque / LinkedList)
        stack.add(stamp.moduleNid());
        while (!stack.isEmpty()) {
            int currModuleNid = stack.pop();
            if (modulesInPriorityOrder.contains(currModuleNid)) {
                LOG.warn("Found Module_Origin cycle containing module: {}", EntityService.get().getEntityFast(currModuleNid).entityToString());
                continue;
            }
            modulesInPriorityOrder.add(currModuleNid);
            EntityService.get().forEachSemanticForComponentOfPattern(currModuleNid,
                    TinkarTerm.MODULE_ORIGINS_PATTERN.nid(), (moduleOriginSemantic) -> {
                        stampCalc.latest(moduleOriginSemantic).ifPresent(latestModuleOriginSemanticVersion -> {
                            IntIdSet moduleOrigins = (IntIdSet) latestModuleOriginSemanticVersion.fieldValues().get(0);
                            stack.addAll(moduleOrigins.mapToList(i -> i).reversed());
                        });
                    });
        }
        int[] modulesInPriorityOrderArr = modulesInPriorityOrder.stream().mapToInt(i -> i).toArray();
        modulePrecedenceListMap.put(moduleKey, IntIds.list.of(modulesInPriorityOrderArr));
        modulePrecedenceSetMap.put(moduleKey, IntIds.set.of(modulesInPriorityOrderArr));
        stampPositionRecordMap.put(stampPosKey, stampPos);
    }

    private UUID modulePrecedenceKey(StampEntity stamp) {
        // Only time, module, and path are necessary to determine Module Precedence
        String keyString = String.valueOf(stamp.time()) + stamp.moduleNid() + stamp.pathNid();
        return UUID.nameUUIDFromBytes(keyString.getBytes());
    }

    private String stampPositionKey(StampEntity stamp) {
        // Only time and path are necessary to determine Stamp Position
        return String.valueOf(stamp.time()) + stamp.pathNid();
    }

    /**
     * Transform relationships.
     *
     * @param premiseType the stated
     */
    private void transformOwlExpressions(int conceptNid, int[] owlNids, PremiseType premiseType) throws Exception {
        updateMessage("Converting " + premiseType + " Owl expressions");

        List<SemanticEntity> owlEntitiesForConcept = new ArrayList<>();
        Set<StampCoordinateRecord> stampCoordinates = new HashSet<>();

        for (int owlNid : owlNids) {
            EntityService.get().getEntity(owlNid).ifPresent(owlSemantic -> {
                owlEntitiesForConcept.add((SemanticEntity) owlSemantic);
                owlSemantic.stampNids().forEach(this::populateMapsWithModulePrecedence);
            });
        }

        for (SemanticEntity<? extends SemanticEntityVersion> owlChronology : owlEntitiesForConcept) {
            for (int stampNid : owlChronology.stampNids().toArray()) {
                StampEntity<? extends StampEntityVersion> stamp = EntityService.get().getStampFast(stampNid);
                StampPositionRecord stampPos = stampPositionRecordMap.get(stampPositionKey(stamp));
                StampCoordinateRecord stampCoordinate = StampCoordinateRecord.make(StateSet.ACTIVE, stampPos)
                        .withModuleNids(modulePrecedenceSetMap.get(modulePrecedenceKey(stamp)))
                        .withModulePriorityNidList(modulePrecedenceListMap.get(modulePrecedenceKey(stamp)));

                if (stampCoordinates.contains(stampCoordinate)) {
                    // Continue if the logical definition at this stamp has already been written
                    // Possible when a module has more than one owl axiom and both have been updated in the same release
                    continue;
                }
                stampCoordinates.add(stampCoordinate);

                LogicalExpression logicalExpression = generateLogicalExpression(conceptNid, owlEntitiesForConcept, stampCoordinate);
                if (logicalExpression == null) {
                    // When the logical expression is null, write a version with the STAMP's original (likely Inactive) status
                    stampCoordinate = StampCoordinateRecord.make(StateSet.of(stamp.state()), stampPos)
                            .withModuleNids(modulePrecedenceSetMap.get(modulePrecedenceKey(stamp)))
                            .withModulePriorityNidList(modulePrecedenceListMap.get(modulePrecedenceKey(stamp)));
                    logicalExpression = generateLogicalExpression(conceptNid, owlEntitiesForConcept, stampCoordinate);
                }

                if (logicalExpression != null) {
                    if (logicalExpression.nodesOfType(LogicalAxiom.LogicalSet.NecessarySet.class).size() > 1) {
                        // Need to merge necessary sets.
                        LOG.warn("\n\n{} has expression with multiple necessary sets: {}\n\n",
                                PrimitiveData.text(conceptNid), logicalExpression);
                    }

                    // See if a semantic already exists in this pattern referencing this concept...
                    int[] destinationSemanticNids = EntityService.get().semanticNidsForComponentOfPattern(conceptNid, destinationPatternNid);
                    switch (destinationSemanticNids.length) {
                        case 0 -> newSemanticWithVersion(conceptNid, logicalExpression, stampCoordinate, stamp.moduleNid());
                        case 1 -> addSemanticVersionIfAbsent(conceptNid, logicalExpression, stampCoordinate, stamp.moduleNid(), destinationSemanticNids[0]);
                        default -> throw new IllegalStateException("To many graphs for component: " + PrimitiveData.text(conceptNid));
                    }
                }
            }
        }
    }

    private LogicalExpression generateLogicalExpression(int ConceptNid, List<SemanticEntity> owlEntities, StampCoordinateRecord stampCoordinate) {
        List<String> owlExpressionsToProcess = new ArrayList<>();
        for (SemanticEntity<SemanticEntityVersion> owlEntity : owlEntities) {
            stampCoordinate.stampCalculator().latest(owlEntity).ifPresent(latestVersion -> {
                // TODO use pattern to get field?
                owlExpressionsToProcess.add((String) latestVersion.fieldValues().get(0));
            });
        }
        if (owlExpressionsToProcess.isEmpty()) {
            return null;
        }

        LogicalExpression logicalExpression = null;
        OwlElExpressionToLogicalExpression transformer = new OwlElExpressionToLogicalExpression(
                owlExpressionsToProcess, ConceptNid);
        try {
            logicalExpression = transformer.build();
        } catch (Exception ex) {
            LOG.error("Error: ", ex);
        }
        return logicalExpression;
    }

    private void newSemanticWithVersion(int conceptNid, LogicalExpression logicalExpression, StampCoordinateRecord stampCoordinate, int writeModuleNid) {
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

        addNewVersion(logicalExpression, newSemanticBuilder.build(), stampCoordinate, writeModuleNid);
    }

    private void addSemanticVersionIfAbsent(int conceptNid, LogicalExpression logicalExpression,
                                            StampCoordinateRecord stampCoordinate, int writeModuleNid, int semanticNid) {
        SemanticRecord existingSemantic = EntityService.get().getEntityFast(semanticNid);
        Latest<SemanticEntityVersion> latestSemanticVersion = stampCoordinate.stampCalculator().latest(semanticNid);
        if (latestSemanticVersion.isPresent()) {
            StampEntity existingStamp = latestSemanticVersion.get().stamp();
            if (existingStamp.time() == stampCoordinate.time() &&
                    existingStamp.moduleNid() == writeModuleNid &&
                    existingStamp.authorNid() == authorNid &&
                    existingStamp.stateNid() == stampCoordinate.allowedStates().toArray()[0].nid() &&
                    existingStamp.pathNid() == stampCoordinate.pathNidForFilter()) {
                DiTreeEntity latestExpression = (DiTreeEntity) latestSemanticVersion.get().fieldValues().get(0);
                LOG.warn("Skipping write of new version: Logical Definition Semantic Version with this STAMP already exists for Concept: {}\nExisting STAMP: {}\nExisting: {}\nNew STAMP: {}\nNew: {}",
                        EntityService.get().getEntityFast(conceptNid).publicId().idString(), existingStamp.describe(), stampCoordinate, latestExpression, logicalExpression);
            } else {
                addNewVersion(logicalExpression, SemanticRecordBuilder.builder(existingSemantic).build(), stampCoordinate, writeModuleNid);
            }
        } else {
            // Add a new version if no version exists at or before the current STAMP - this case already guarantees there is no conflicting stamp / version
            // Possible when a Semantic Version with a later timestamp has already been written
            addNewVersion(logicalExpression, SemanticRecordBuilder.builder(existingSemantic).build(), stampCoordinate, writeModuleNid);
        }
    }

    private void addNewVersion(LogicalExpression logicalExpression,
                               SemanticRecord semanticRecord, StampCoordinateRecord stampCoordinate, int writeModuleNid) {
        // Allowed States should not have more than 1 element
        State status = stampCoordinate.allowedStates().toArray()[0];
        if (stampCoordinate.allowedStates().size() != 1) {
            LOG.warn("\n\nMore than one state when writing Logical Definition Semantic below. " +
                    "Using first available state {}\n{}\n\n", status, logicalExpression);
        }
        long time = stampCoordinate.time();
        int pathNid = stampCoordinate.pathNidForFilter();
        StampEntity transactionStamp = transaction.getStamp(status, time, authorNid, writeModuleNid, pathNid);

        SemanticVersionRecordBuilder semanticVersionBuilder = SemanticVersionRecordBuilder.builder()
                .fieldValues(Lists.immutable.of(logicalExpression.sourceGraph()))
                .stampNid(transactionStamp.nid())
                .chronology(semanticRecord);

        SemanticRecord newSemanticRecord = semanticRecord.analogueBuilder().with(semanticVersionBuilder.build()).build();
        EntityService.get().putEntity(newSemanticRecord);
    }
}
