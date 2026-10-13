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
package dev.ikm.tinkar.integration.changeSet;

import dev.ikm.tinkar.common.service.EntityCountSummary;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.concurrent.ExecutionException;

import static dev.ikm.tinkar.fixtures.TestConstants.createFilePathInTarget;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EntityServiceIT {

    private static final File SAP_DATASTORE_ROOT = TestConstants.createFilePathInTargetFromClassName.apply(EntityServiceIT.class);

    @BeforeEach
    void beforeEach() {
        TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, SAP_DATASTORE_ROOT);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
    }

    @Test
    @DisplayName("Test export entities in temporal range")
    void testExportEntitiesInSpecifiedTemporalRange() throws ExecutionException, InterruptedException {

        File exportFile = createFilePathInTarget.apply("data/testExportEntitiesInSpecifiedTemporalRange-pb.zip");
        exportFile.delete(); // Clean up previously created file

        // The IKE starter set is authored at 2026-01-01T00:00:00.777Z (1767225600777); the
        // classifier's versions come later, outside the range.
        long fromEpoch = 1767225600777L;
        long toEpoch = 1767225600778L;

        // Perform the temporal export operation
        EntityCountSummary summary = EntityService.get().temporalExport(exportFile, fromEpoch, toEpoch).get();

        // Verify the summary
        assertNotNull(summary);
        // The whole set is authored in the range, so every concept and pattern exports; their
        // counts come from the store as loaded, not hard-coded, since the set grows as it gains
        // content.
        int[] concepts = {0};
        EntityService.get().forEachConceptEntity(concept -> concepts[0]++);
        int[] patterns = {0};
        EntityService.get().forEachPatternEntity(pattern -> patterns[0]++);
        assertEquals(concepts[0], summary.conceptCount(), summary.toString());
        assertEquals(patterns[0], summary.patternCount(), summary.toString());
        // Not every semantic exports: the classifier's, written later, fall outside the range.
        // A semantic exports when any of its versions was written in the range, counted here
        // from the store through the entity service, independently of the export's own scan.
        int[] semanticsInRange = {0};
        EntityService.get().forEachSemanticEntity(semantic -> {
            if (semantic.versions().anySatisfy(
                    version -> fromEpoch <= version.time() && version.time() <= toEpoch)) {
                semanticsInRange[0]++;
            }
        });
        assertEquals(semanticsInRange[0], summary.semanticCount(), summary.toString());
        // Pinned from the starter set carrying the AsciiDoc document model
        // (IKE-Network/ike-issues#1295).
        assertEquals(3, summary.stampCount(), summary.toString());
        // To ensure exports are self-contained, when a Component has a version in the specified time range, all its versions
        // are exported with the specified stamp. Therefore, many stamps are expected to be exported here.
    }

}


