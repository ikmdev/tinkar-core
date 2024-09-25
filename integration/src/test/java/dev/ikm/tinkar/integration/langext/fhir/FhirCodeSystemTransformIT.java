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
package dev.ikm.tinkar.integration.langext.fhir;

import dev.ikm.tinkar.common.id.IntIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.coordinate.stamp.StampCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StampCoordinateRecordBuilder;
import dev.ikm.tinkar.coordinate.stamp.StampPositionRecord;
import dev.ikm.tinkar.coordinate.stamp.StampPositionRecordBuilder;
import dev.ikm.tinkar.coordinate.stamp.StateSet;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.ConceptEntityVersion;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampEntityVersion;
import dev.ikm.tinkar.fhir.transformers.FhirCodeSystemTransform;
import dev.ikm.tinkar.fhir.transformers.FhirStatedDefinitionTransformer;
import dev.ikm.tinkar.integration.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.hl7.fhir.r4.model.CodeSystem;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class FhirCodeSystemTransformIT {

    private static final Logger LOG = LoggerFactory.getLogger(FhirCodeSystemTransformIT.class);
    private static final File DATASTORE_ROOT = TestConstants.createFilePathInTargetFromClassName.apply(
            FhirCodeSystemTransformIT.class);

    FhirCodeSystemTransform fhirCodeSystemTransform;
    FhirStatedDefinitionTransformer fhirStatedDefinitionTransformer;
    StampCalculator stampCalculator;
    final int transformSize = 1000;

    @BeforeAll
    public void beforeAll() {
        TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, DATASTORE_ROOT);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
    }

    @Test
    @DisplayName("Test Fhir transform for entire file" )
    public void testFhirCodeSystemTransformation() {
        int patternNid = TinkarTerm.DESCRIPTION_PATTERN.nid();
        Set<Integer> pathNids = new HashSet<>();
        EntityService.get().forEachSemanticOfPattern(patternNid,patternEntity1 ->
                patternEntity1.stampNids().forEach(stampNid -> {
                    StampEntity<? extends StampEntityVersion> stamp = EntityService.get().getStampFast(stampNid);
                    pathNids.add(stamp.pathNid());
                })
        );

        AtomicInteger counter = new AtomicInteger();
        AtomicInteger totalConcepts = new AtomicInteger();
        pathNids.forEach(pathNid -> {
            if(TinkarTerm.DEVELOPMENT_PATH.nid() == pathNid){

                StampCalculator stampCalculatorWithCache =   initStampCalculator(pathNid);
                List<ConceptEntity<? extends  ConceptEntityVersion>> concepts = new ArrayList<>();
                PrimitiveData.get().forEachConceptNid((conceptnid) -> {
                    if(counter.get() == transformSize){
                       totalConcepts.set(totalConcepts.get()+transformSize);
                       processConceptBatch(concepts, stampCalculatorWithCache, totalConcepts.get());
                       concepts.clear();
                       counter.set(0);
                    }
                    if(counter.get() < transformSize) {
                        Entity<EntityVersion> entity = EntityService.get().getEntityFast(conceptnid);
                        if (entity instanceof ConceptEntity conceptEntity) {
                            concepts.add(conceptEntity);
                            counter.getAndIncrement();
                        }
                    }
                });
                if(counter.get() < transformSize && counter.get() > 0){
                    totalConcepts.set(totalConcepts.get()+counter.get());
                    processConceptBatch(concepts, stampCalculatorWithCache, totalConcepts.get());
                }
            }
        });

    }

    private void processConceptBatch(List<ConceptEntity<? extends ConceptEntityVersion>> concepts, StampCalculator stampCalculatorWithCache, int conceptCount) {
        LOG.debug("Processing Concepts : " + concepts.size() + "   TOTAL CONCEPTS : " + conceptCount);
        fhirCodeSystemTransform= new FhirCodeSystemTransform(stampCalculatorWithCache, concepts, (fhirProvenanceString) -> {
            Assertions.assertNotNull(fhirProvenanceString);
            Assertions.assertFalse(fhirProvenanceString.isEmpty());
        });
        try {
            fhirCodeSystemTransform.compute();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Test transformation of axiom semantics.")
    public void testTransformAxiomSemantics() {

        String toTime = "2019-10-22T12:31:04";
        LocalDateTime toLocalDateTime = LocalDateTime.parse(toTime, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        long toTimeStamp = toLocalDateTime.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();

        int elPlusPlusPatternNId = TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid();

        AtomicInteger counter = new AtomicInteger(0);
        if(counter.getAndIncrement() < 1){
            EntityService.get().forEachSemanticOfPattern(elPlusPlusPatternNId, semanticEntity -> {
                stampCalculator=initStampCalculator(TinkarTerm.DEVELOPMENT_PATH.nid(), toTimeStamp);

                fhirStatedDefinitionTransformer = new FhirStatedDefinitionTransformer(initStampCalculator());
                List<CodeSystem.ConceptPropertyComponent> statedAxiomPropertiesResult =
                        fhirStatedDefinitionTransformer.transfromAxiomSemantics(semanticEntity, TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN);
                Assertions.assertNotNull(statedAxiomPropertiesResult);
                Assertions.assertFalse(statedAxiomPropertiesResult.isEmpty());
            });
        }
    }

    private StampCalculator initStampCalculator(Integer pathNid) {
        return initStampCalculator(pathNid, Long.MAX_VALUE);
    }

    private StampCalculatorWithCache initStampCalculator(Integer pathNid, long toTimeStamp) {
        StampPositionRecord stampPositionRecord = StampPositionRecordBuilder.builder().time(toTimeStamp).pathForPositionNid(pathNid).build();
        StampCoordinateRecord stampCoordinateRecord = StampCoordinateRecordBuilder.builder()
                .allowedStates(StateSet.ACTIVE_AND_INACTIVE)
                .stampPosition(stampPositionRecord)
                .moduleNids(IntIds.set.empty())
                .build().withStampPositionTime(toTimeStamp);
        return stampCoordinateRecord.stampCalculator();
    }

    private StampCalculatorWithCache initStampCalculator() {
        return initStampCalculator(TinkarTerm.DEVELOPMENT_PATH.nid(), Long.MAX_VALUE);
    }

}
