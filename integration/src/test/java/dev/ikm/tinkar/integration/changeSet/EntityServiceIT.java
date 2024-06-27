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

import dev.ikm.tinkar.entity.EntityCountSummary;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.integration.TestConstants;
import dev.ikm.tinkar.integration.helper.TestHelper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class EntityServiceIT extends TestHelper {

    private static final File SAP_DATASTORE_ROOT = TestConstants.createFilePathInTargetFromClassName.apply(
            EntityServiceIT.class);
    @BeforeEach
    public void init() {
        loadSpinedArrayDataBase(SAP_DATASTORE_ROOT);
    }

    @Test
    @DisplayName("Test export entities in temporal range")
    void testExportEntitiesInSpecifiedTemporalRange() throws ExecutionException, InterruptedException {

        File file = new File("exportFile");
        String from = "2020-03-01T00:00:00";
        String to ="2024-10-21T00:00:00";

        // Parse the string to LocalDateTime
        LocalDateTime dateTimeFrom = LocalDateTime.parse(from);
        LocalDateTime dateTimeTo = LocalDateTime.parse(to);

        // Convert LocalDateTime to epoch milliseconds
        long fromEpoch = dateTimeFrom.toInstant(ZoneOffset.UTC).toEpochMilli();
        long toEpoch = dateTimeTo.toInstant(ZoneOffset.UTC).toEpochMilli();

        // Perform the temporal export operation
        EntityCountSummary summary = EntityService.get().temporalExport(file, fromEpoch, toEpoch).get();

        // Verify the summary
        assertNotNull(summary);
        // Add your assertions here based on the expected summary values
        // For example:
        assertEquals(295, summary.conceptsCount());
        assertEquals(3047, summary.semanticsCount());
        assertEquals(17, summary.patternsCount());
        assertEquals(2, summary.stampsCount());

        // Clean up (delete the file if it was created)
        file.delete();
    }

}


