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
package dev.ikm.tinkar.integration.helper;

import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ServiceKeys;
import dev.ikm.tinkar.common.service.ServiceProperties;
import dev.ikm.tinkar.entity.EntityCountSummary;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.integration.TestConstants;
import org.junit.jupiter.api.AfterAll;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

import static dev.ikm.tinkar.integration.TestConstants.PB_STARTER_DATA_REASONED;

public class TestHelper {

    private static final Logger LOG = LoggerFactory.getLogger(TestHelper.class);

    protected static void startEphemeralDataBase() {
        CachingService.clearAll();
        LOG.info("Cleared caches");
        LOG.info("JVM Version: " + System.getProperty("java.version"));
        LOG.info("JVM Name: " + System.getProperty("java.vm.name"));
        LOG.info("Setup Ephemeral Suite: " + LOG.getName());
        LOG.info(ServiceProperties.jvmUuid());
        PrimitiveData.selectControllerByName(TestConstants.EPHEMERAL_STORE_NAME);
        PrimitiveData.start();
    }

    protected static void startSpinedArrayDataBase(File fileDataStore) {
        LOG.info("Clear caches");
        CachingService.clearAll();
        LOG.info("Setup Suite: " + LOG.getName());
        LOG.info(ServiceProperties.jvmUuid());
        ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, fileDataStore);
        //FileUtil.recursiveDelete(fileDataStore);
        PrimitiveData.selectControllerByName(TestConstants.SA_STORE_OPEN_NAME);
        PrimitiveData.start();
    }

    protected static void startMVStoreDataBase(File fileDataStore) {
        LOG.info("Clear caches");
        CachingService.clearAll();
        LOG.info("Setup suite: " + LOG.getName());
        LOG.info(ServiceProperties.jvmUuid());
        ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, fileDataStore);
        PrimitiveData.selectControllerByName(TestConstants.MV_STORE_OPEN_NAME);
        PrimitiveData.start();
    }

    protected static void loadEphemeralDataBase() {
        startEphemeralDataBase();
        loadDataBase();
    }

    protected static void loadSpinedArrayDataBase(File fileDataStore) {
        startSpinedArrayDataBase(fileDataStore);
        loadDataBase();
    }

    protected static void loadMVStoreDataBase(File fileDataStore) {
        startMVStoreDataBase(fileDataStore);
        loadDataBase();
    }

    protected static void loadDataBase() {
        LoadEntitiesFromProtobufFile loadProto = new LoadEntitiesFromProtobufFile(PB_STARTER_DATA_REASONED);
        EntityCountSummary count = loadProto.compute();
        LOG.info(count + " entitles loaded from file: " + loadProto.summarize() + "\n\n");
    }

    @AfterAll
    protected static void stopDatabase() {
        LOG.info("Teardown Suite: " + LOG.getName());
        PrimitiveData.stop();
    }
}
