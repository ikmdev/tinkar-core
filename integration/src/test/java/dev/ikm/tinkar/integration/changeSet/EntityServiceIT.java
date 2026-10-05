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
        // Add your assertions here based on the expected summary values
        // For example:
        assertEquals(1295, summary.conceptCount(), summary.toString());
        // 10916 since the 63 expression-language concepts regained their FQN description and its
        // US-dialect acceptability (126 semantics), which a generic keyword description had
        // suppressed.
        assertEquals(10916, summary.semanticCount(), summary.toString());
        assertEquals(64, summary.patternCount(), summary.toString());
        assertEquals(2, summary.stampCount(), summary.toString());
        // To ensure exports are self-contained, when a Component has a version in the specified time range, all its versions
        // are exported with the specified stamp. Therefore, many stamps are expected to be exported here.
    }

}


