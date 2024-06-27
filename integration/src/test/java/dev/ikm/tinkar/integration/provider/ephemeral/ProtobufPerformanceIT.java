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

import dev.ikm.tinkar.entity.EntityCountSummary;
import dev.ikm.tinkar.entity.export.ExportEntitiesToProtobufFile;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.integration.TestConstants;
import dev.ikm.tinkar.integration.helper.TestHelper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ProtobufPerformanceIT extends TestHelper {

    private static final Logger LOG = LoggerFactory.getLogger(ProtobufRoundTripIT.class);

    @BeforeAll
    public void setupSuite() {
        startEphemeralDataBase();
    }

    @Test
    @Order(1)
    public void roundTripPerformanceTest() throws IOException {
        //Printing out File size for this transformation

        long loadDTOTimeBefore = System.nanoTime();
        File file = TestConstants.PB_STARTER_DATA_REASONED;
        LOG.info("[1] The size of the original DTO file is: " + file.length() + " bytes long.");
        LoadEntitiesFromProtobufFile loadProto = new LoadEntitiesFromProtobufFile(file);
        EntityCountSummary count = loadProto.compute();
        LOG.info(count + " entitles loaded from file: " + loadProto.summarize() + "\n\n");
        long diffLoadProto = (System.nanoTime() - loadDTOTimeBefore);
        LOG.info("The time it took for the Load protobuf to Entities operation is: " + diffLoadProto/1000000 + " milliseconds.");

        File fileProtobuf = TestConstants.PB_PERFORMANCE_TEST_FILE;
        //Printing out File size for this transformation
        LOG.info("[2] The size of the file is: " + fileProtobuf.length() + " bytes long.");
        long loadEntitiesTimeBefore = System.nanoTime();
        ExportEntitiesToProtobufFile exportEntitiesToProtobufFile = new ExportEntitiesToProtobufFile(fileProtobuf);
        long actualProtobufExportCount = exportEntitiesToProtobufFile.compute().getTotalCount();
        diffLoadProto = (System.nanoTime() - loadEntitiesTimeBefore);
        LOG.info("[2] The count for Entities to Protobuf is: " + actualProtobufExportCount);
        LOG.info("The time it took for the Load Entities to a Protobuf file is: " + diffLoadProto/1000000 + " milliseconds.");

        //Stopping and starting the database
        stopDatabase();
        startEphemeralDataBase();

        //Printing out File size for this transformation
        LOG.info("[3] The size of the file is: " + fileProtobuf.length() + " bytes long.");
        long loadProtobufTimeBefore = System.nanoTime();
        LoadEntitiesFromProtobufFile loadEntitiesFromProtobufFile = new LoadEntitiesFromProtobufFile(fileProtobuf);
        long actualProtobufImportCount = loadEntitiesFromProtobufFile.compute().getTotalCount();
        long diffLoadEntities = (System.nanoTime() - loadProtobufTimeBefore);
        LOG.info("[3] The count for Protobuf to Entities is: " + actualProtobufImportCount);
        LOG.info("The time it took to write Entities from a Protobuf file is: " + diffLoadEntities/1000000 + " milliseconds.");
    }
}
