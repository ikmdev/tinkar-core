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
package dev.ikm.tinkar.integration.provider.spinedarray;

import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.id.impl.IntIdListArray;
import dev.ikm.tinkar.common.id.impl.IntIdSetArray;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.Coordinates;
import dev.ikm.tinkar.coordinate.stamp.StampCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StateSet;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import dev.ikm.tinkar.entity.EntityCountSummary;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.PatternEntityVersion;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.integration.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Part 2 of the Export/Import test - Imports data from protobuf.
 * This test class runs second (alphabetically after ExportDataIT).
 * It starts a fresh database, loads the exported protobuf file, and validates the data.
 */
class ImportDataIT {
    private static final Logger LOG = LoggerFactory.getLogger(ImportDataIT.class);
    private static final File DATASTORE_ROOT = TestConstants.createFilePathInTargetFromClassName.apply(
            ImportDataIT.class);
    private static final File EXPORT_LOCK_FILE = ExportDataIT.EXPORT_LOCK_FILE;
    private static final long LOCK_FILE_TIMEOUT_MS = 10_000; // 10 seconds timeout

    @BeforeAll
    static void beforeAll() {
        FileUtil.recursiveDelete(DATASTORE_ROOT);
        TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, DATASTORE_ROOT);

        // Load the original example data first
        File file = TestConstants.PB_EXAMPLE_DATA_REASONED;
        LoadEntitiesFromProtobufFile loadProto = new LoadEntitiesFromProtobufFile(file);
        EntityCountSummary count = loadProto.compute();
        LOG.info(count + " entitles loaded from file: " + loadProto.summarize() + "\n\n");

        // Load the exported pb file from the ExportDataIT test
        waitForExportToComplete();
        File fileProtobuf = TestConstants.PB_TEST_FILE;
        loadProto = new LoadEntitiesFromProtobufFile(fileProtobuf);
        count = loadProto.compute();
        LOG.info(count + " entitles loaded from exported file: " + loadProto.summarize() + "\n\n");
    }

    @AfterAll
    static void afterAll() {
        TestHelper.stopDatabase();
    }

    /**
     * Waits for the lock file to be deleted by ExportDataIT.
     * This ensures that the export test has completed before import begins.
     * Will wait up to LOCK_FILE_TIMEOUT_MS milliseconds before proceeding.
     */
    private static void waitForExportToComplete() {
        try {
            Thread.sleep(500); // Small delay to ensure file system stability
        } catch (InterruptedException e) {
            LOG.warn("Interrupted while waiting for lock file", e);
        }
        if (EXPORT_LOCK_FILE.exists()) {
            LOG.info("Lock file exists. Waiting for ExportDataIT to complete...");
            long startTime = System.currentTimeMillis();

            while (EXPORT_LOCK_FILE.exists()) {
                long elapsedTime = System.currentTimeMillis() - startTime;
                if (elapsedTime > LOCK_FILE_TIMEOUT_MS) {
                    LOG.error("Timeout waiting for lock file to be deleted after " + LOCK_FILE_TIMEOUT_MS + "ms");
                    break;
                }

                try {
                    Thread.sleep(100); // Check every 100ms
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    LOG.error("Interrupted while waiting for lock file", e);
                    break;
                }
            }

            if (!EXPORT_LOCK_FILE.exists()) {
                LOG.info("Lock file deleted. ExportDataIT has completed.");
            }
        } else {
            LOG.info("No lock file found. ExportDataIT may have already completed.");
        }
    }

    @Test
    public void testImportFieldTypeSetToList() {
        EntityProxy.Concept concept = EntityProxy.Concept.make(PublicIds.of(UUID.fromString("dde159ca-415e-4947-9174-cae7e8e7202d")));
        StateSet stateActive = StateSet.ACTIVE;
        StampCalculator stampCalcActive = StampCalculatorWithCache
                .getCalculator(StampCoordinateRecord.make(stateActive, Coordinates.Position.LatestOnDevelopment()));

        // NOTE. There's not TinkarTerm.EXAMPLE_PATTERN_TWO declared. Instead, tinkar-example-data repo creates its own EntityProxy.Pattern
        // Therefore, we declare it here to be used for PatternEntityVersion
        EntityProxy.Pattern EXAMPLE_PATTERN_TWO = EntityProxy.Pattern.make("Example Pattern Two", UUID.fromString("7222d538-9641-474a-94ce-72c5bf6462b3"));
        // Repeat the same for the following Concepts related to EXAMPLE_PATTERN_TWO
        EntityProxy.Concept COMPONENT_SET_FIELD_MEANING = EntityProxy.Concept.make(PublicIds.of(UUID.fromString("990e5a92-cdc2-4e23-a68d-1f01345b8759")));
        EntityProxy.Concept COMPONENT_LIST_FIELD_MEANING = EntityProxy.Concept.make(PublicIds.of(UUID.fromString("f0847cd3-2034-43f5-b25f-2bd6e923d228")));

        PatternEntityVersion latestPattern = (PatternEntityVersion) Calculators.Stamp.DevelopmentLatest().latest(EXAMPLE_PATTERN_TWO).get();
        AtomicReference<IntIdSetArray> intIdSet = new AtomicReference<>();
        AtomicReference<IntIdListArray> intIdList = new AtomicReference<>();

        AtomicBoolean atomicBoolean = new AtomicBoolean(false);

        //Get the Set and List elements from newSemantic
        EntityService.get().forEachSemanticForComponentOfPattern(concept.nid(), EXAMPLE_PATTERN_TWO.nid(), semanticEntity -> {
            Latest<SemanticEntityVersion> latestActive2 = stampCalcActive.latest(semanticEntity);

            if (latestActive2.isPresent()) {
                intIdSet.set(latestPattern.getFieldWithMeaning(COMPONENT_SET_FIELD_MEANING, latestActive2.get()));
                // Reassign elements
                int [] tempSetArray2 = intIdSet.get().toArray();
                assertEquals(TinkarTerm.ACTIVE_STATE.nid(), tempSetArray2 [0]);

                intIdList.set(latestPattern.getFieldWithMeaning(COMPONENT_LIST_FIELD_MEANING, latestActive2.get()));
                int [] tempListArray2 = intIdList.get().toArray();
                assertEquals(TinkarTerm.ACTIVE_STATE.nid(), tempListArray2 [0]);

                atomicBoolean.set(true);
            }

        });

        assertTrue(atomicBoolean.get());
    }

}
