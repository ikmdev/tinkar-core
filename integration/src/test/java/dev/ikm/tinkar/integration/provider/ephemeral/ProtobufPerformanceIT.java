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

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ProtobufPerformanceIT {

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

    @Test
    @Order(1)
    public void roundTripPerformanceTest() throws IOException {
        File fileDTO = TestConstants.TINK_TEST_FILE;
        //Printing out File size for this transformation
        System.out.println("[1] The size of the original DTO file is: " + fileDTO.length() + " bytes long.");
        long loadDTOTimeBefore = System.nanoTime();
        LoadEntitiesFromDtoFile loadDTO = new LoadEntitiesFromDtoFile(fileDTO);
        int expectedEntityCount = loadDTO.compute();
        long diffLoadDTO = (System.nanoTime() - loadDTOTimeBefore);
        System.out.println("[1] The count for DTO to Entities is: " + expectedEntityCount);
        System.out.println("The time it took for the Load DTO to Entities operation is: " + diffLoadDTO/1000000 + " milliseconds.");

        File fileProtobuf = TestConstants.PB_PERFORMANCE_TEST_FILE;
        //Printing out File size for this transformation
        System.out.println("[2] The size of the file is: " + fileProtobuf.length() + " bytes long.");
        long loadEntitiesTimeBefore = System.nanoTime();
        ExportEntitiesToProtobufFile exportEntitiesToProtobufFile = new ExportEntitiesToProtobufFile(fileProtobuf);
        long actualProtobufExportCount = exportEntitiesToProtobufFile.compute().getTotalCount();
        long diffLoadProto = (System.nanoTime() - loadEntitiesTimeBefore);
        System.out.println("[2] The count for Entities to Protobuf is: " + actualProtobufExportCount);
        System.out.println("The time it took for the Load Entities to a Protobuf file is: " + diffLoadProto/1000000 + " milliseconds.");

        //Stopping and starting the database
        stopDatabase();
        startDatabase();

        //Printing out File size for this transformation
        System.out.println("[3] The size of the file is: " + fileProtobuf.length() + " bytes long.");
        long loadProtobufTimeBefore = System.nanoTime();
        LoadEntitiesFromProtobufFile loadEntitiesFromProtobufFile = new LoadEntitiesFromProtobufFile(fileProtobuf);
        long actualProtobufImportCount = loadEntitiesFromProtobufFile.compute().getTotalCount();
        long diffLoadEntities = (System.nanoTime() - loadProtobufTimeBefore);
        System.out.println("[3] The count for Protobuf to Entities is: " + actualProtobufImportCount);
        System.out.println("The time it took to write Entities from a Protobuf file is: " + diffLoadEntities/1000000 + " milliseconds.");
    }
}
