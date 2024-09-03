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
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.aggregator.TemporalEntityAggregator;
import dev.ikm.tinkar.fhir.transformers.FhirCodeSystemTransform;
import dev.ikm.tinkar.integration.TestConstants;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class FhirTransformAPIIT extends TestHelper {

    private static final Logger LOG = LoggerFactory.getLogger(FhirTransformAPIIT.class);
    private static final File SAP_DATASTORE_ROOT = TestConstants.createFilePathInTargetFromClassName.apply(FhirTransformAPIIT.class);

    @BeforeAll
    public void setup() {
        loadSpinedArrayDataBase(SAP_DATASTORE_ROOT);
    }

    @Test
    @DisplayName("Test the agregator for this data")
    @Disabled("The agregator is returning zero concepts. Need to enable the test after the from and to time stamps are figured out.")
    public void testFhirCallWithAgregator() {
        long fromTimeStamp = Long.MAX_VALUE;
        long toTimeStamp = Long.MIN_VALUE;

        Map<String, ConceptEntity<? extends ConceptEntityVersion>> concepts = getConceptEntities(fromTimeStamp, toTimeStamp);

        LOG.info("Total Concepts : " + concepts.size());

        StampCalculator stampCalculator = initStampCalculator(toTimeStamp);
        FhirCodeSystemTransform fhirCodeSystemTransform = new FhirCodeSystemTransform(fromTimeStamp, toTimeStamp, stampCalculator, concepts.values().stream(), (fhirProvenanceString) -> {
            Assertions.assertNotNull(fhirProvenanceString);
            Assertions.assertFalse(fhirProvenanceString.isEmpty());
        });
        try {
            fhirCodeSystemTransform.compute();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Map<String, ConceptEntity<? extends ConceptEntityVersion>> getConceptEntities(long fromTimeStamp, long toTimeStamp) {
        AtomicInteger counter = new AtomicInteger(0);
        Map<String, ConceptEntity<? extends ConceptEntityVersion>> concepts = new HashMap<>();
        TemporalEntityAggregator temporalEntityAggregator = new TemporalEntityAggregator(fromTimeStamp, toTimeStamp);
        temporalEntityAggregator.aggregate(nid -> {
            Entity<EntityVersion> entity = EntityService.get().getEntityFast(nid);
            if (entity instanceof ConceptEntity conceptEntity) {
                LOG.debug(counter.getAndIncrement() + " : " + conceptEntity);
                concepts.putIfAbsent(conceptEntity.publicId().idString(), conceptEntity);
            } else if (entity instanceof SemanticEntity semanticEntity) {
                Entity<EntityVersion> referencedConcept = semanticEntity.referencedComponent();
                if (referencedConcept instanceof ConceptEntity concept) {
                    concepts.putIfAbsent(concept.publicId().idString(), concept);
                }
            }
        });
        return concepts;
    }

    private StampCalculator initStampCalculator(long toTime) {
        return initStampCalculator(TinkarTerm.DEVELOPMENT_PATH.nid(), toTime);
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
