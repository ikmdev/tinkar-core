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

import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ServiceKeys;
import dev.ikm.tinkar.common.service.ServiceProperties;
import dev.ikm.tinkar.entity.EntityCountSummary;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.integration.TestConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

//    @ExtendWith(MockitoExtension.class)
    class EntityServiceIT {
    private EntityService entityService;
    @BeforeEach
    public void init() {
        //am i creating a new/copy dataStore or using an existing one
        File dataStore = new File(System.getProperty("user.home") + "/Solor/starter-data-export");
        //make sure it can run as eds
        String controllerName = TestConstants.SA_STORE_OPEN_NAME;
        CachingService.clearAll();
        ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, dataStore);
        PrimitiveData.selectControllerByName(controllerName);
        PrimitiveData.start();
    }

    @Test
    @Disabled
    void temporalExport_shouldExportEntitiesInSpecifiedTemporalRange() throws ExecutionException, InterruptedException {

        File file = new File(String.valueOf(TestConstants.PB_TEST_FILE));

        String from = "2024-03-01T00:00:00";
        String to ="2024-03-21T00:00:00";

        // Parse the string to LocalDateTime
        LocalDateTime dateTimeFrom = LocalDateTime.parse(from);
        LocalDateTime dateTimeTo = LocalDateTime.parse(to);

        // Convert LocalDateTime to epoch milliseconds
        long fromEpoch = dateTimeFrom.toInstant(ZoneOffset.UTC).toEpochMilli();
        long toEpoch = dateTimeTo.toInstant(ZoneOffset.UTC).toEpochMilli();

        // Perform the temporal export operation
        EntityCountSummary summary = entityService.temporalExport(file, fromEpoch, toEpoch).get();

        // Verify the summary
        assertNotNull(summary);
        // Add your assertions here based on the expected summary values
        // For example:
        assertEquals(100, summary.conceptsCount());
        assertEquals(50, summary.semanticsCount());
        assertEquals(10, summary.patternsCount());
        assertEquals(10, summary.stampsCount());

        // Clean up (delete the file if it was created)
        //file.delete();
    }

}


