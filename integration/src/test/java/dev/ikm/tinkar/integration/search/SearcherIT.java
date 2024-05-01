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
package dev.ikm.tinkar.integration.search;

import dev.ikm.tinkar.common.service.*;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.integration.TestConstants;
import dev.ikm.tinkar.provider.search.Searcher;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class SearcherIT {

    private static final File SAP_SPINEDARRAYPROVIDERIT_DATASTORE_ROOT = TestConstants.createFilePathInTargetFromClassName.apply(
            SearcherIT.class);

    @BeforeAll
    public void setup() {
        CachingService.clearAll();
        ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, SAP_SPINEDARRAYPROVIDERIT_DATASTORE_ROOT);
        FileUtil.recursiveDelete(SAP_SPINEDARRAYPROVIDERIT_DATASTORE_ROOT);
        PrimitiveData.selectControllerByName(TestConstants.SA_STORE_OPEN_NAME);
        PrimitiveData.start();
    }

    @AfterAll
    public void teardown() {
        PrimitiveData.stop();
    }

    @Test
    public void searchAfterNewEntitiesAreWrittenToDatabase() throws Exception {
        //Given an empty database
        //When new entities are written
        LoadEntitiesFromProtobufFile loadEntitiesFromProtobufFile = new LoadEntitiesFromProtobufFile(TestConstants.PB_STARTER_DATA_REASONED);
        loadEntitiesFromProtobufFile.compute();

        //Then the searcher should immediately search on the newly added/indexed entities
        Searcher searcher = new Searcher();
        PrimitiveDataSearchResult[] searchResults = searcher.search("user", 100);

        assertTrue(searchResults.length > 0, "Missing search results");
    }
}
