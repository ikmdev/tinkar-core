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
package dev.ikm.tinkar.integration.provider.ephemeral;

import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ServiceProperties;
import dev.ikm.tinkar.entity.export.ExportEntitiesToProtobufFile;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromDtoFile;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.integration.TestConstants;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;



@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ProtobufRoundTripIT {

    private static final Logger LOG = LoggerFactory.getLogger(ProtobufRoundTripIT.class);

    @BeforeAll
    public void setupSuite() {
        LOG.info("JVM Version: " + System.getProperty("java.version"));
        LOG.info("JVM Name: " + System.getProperty("java.vm.name"));
        startDatabase();
    }
    private void startDatabase() {
        LOG.info("Clear caches");
        CachingService.clearAll();
        LOG.info("Setup Ephemeral Protobuf Suite: " + LOG.getName());
        LOG.info(ServiceProperties.jvmUuid());
        PrimitiveData.selectControllerByName(TestConstants.EPHEMERAL_STORE_NAME);
        PrimitiveData.start();
    }
    private void stopDatabase() {
        PrimitiveData.stop();
    }
    @AfterAll
    public void teardownSuite() {
        stopDatabase();
    }

    /**
     * 0. Start new database (ephemeral)
     * 1. Dto zip file -> entity store
     * 2. entity store -> Protobuf objects file
     *    // (is there version merging going on?)
     * 3. stop and start new database
     * 4. Protobuf objects file -> entity store.
     * 5. Stop database.
     *
     * @throws IOException
     */
    @Test
    @Disabled
    public void roundTripTest() throws IOException {
        // Given initial DTO data
        File fileDTO = TestConstants.TINK_TEST_FILE;
        LoadEntitiesFromDtoFile loadDTO = new LoadEntitiesFromDtoFile(fileDTO);
        // When we import DTO into entities
        int expectedEntityCount = loadDTO.compute();
        LOG.info("Entities loaded from dto: " + expectedEntityCount);

        // When we export Entities data to protobuf
        File fileProtobuf = TestConstants.PB_ROUNDTRIP_TEST_FILE;
        boolean pbZipFileSuccess = true;
        if (fileProtobuf.exists()) {
            pbZipFileSuccess = fileProtobuf.delete();
        }
        pbZipFileSuccess = fileProtobuf.createNewFile();
        if (!pbZipFileSuccess) {
            throw new RuntimeException("Round trip test has failed setup to begin test. Unable to delete or create " + fileProtobuf.getName() + " to begin.");
        }
        ExportEntitiesToProtobufFile exportEntitiesToProtobufFile = new ExportEntitiesToProtobufFile(fileProtobuf);
        int actualProtobufExportCount = exportEntitiesToProtobufFile.compute();
        LOG.info("Entities exported to protobuf: " + actualProtobufExportCount);

        stopDatabase();
        startDatabase();

        // When we import protobuf data into entities
        LoadEntitiesFromProtobufFile loadEntitiesFromProtobufFile = new LoadEntitiesFromProtobufFile(fileProtobuf);
        int actualProtobufImportCount = loadEntitiesFromProtobufFile.compute();
        LOG.info("Entities loaded from protobuf: " + actualProtobufImportCount);

//         Then all imported and exported entities counts should match
        boolean boolEntityCount = expectedEntityCount == actualProtobufExportCount && expectedEntityCount == actualProtobufImportCount;
        assertTrue(expectedEntityCount > 0, "Imported DTO count should be greater than zero.");
        assertTrue(actualProtobufExportCount > 0, "Exported Protobuf count should be greater than zero.");
        assertTrue(actualProtobufImportCount > 0, "Imported Protobuf count should be greater than zero.");
        assertEquals(expectedEntityCount, actualProtobufExportCount, "Entity count and Protobuf Export count do not match.");
        assertEquals(expectedEntityCount, actualProtobufImportCount, "Entity count and Protobuf Import count do not match.");
        assertTrue(boolEntityCount, "Counts in round-trip do not match.");
    }
}
