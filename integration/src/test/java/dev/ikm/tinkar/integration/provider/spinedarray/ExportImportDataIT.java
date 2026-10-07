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

import dev.ikm.tinkar.terms.KernelTerm;
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
import dev.ikm.tinkar.common.service.EntityCountSummary;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.PatternEntityVersion;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.export.ExportEntitiesToProtobufFile;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.fixtures.ForkedJvm;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.fixtures.TestTags;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.EntityProxy;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Properties;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exports the example data to protobuf from one store, imports the export into another
 * on top of the same data, and checks that a semantic's set and list fields survive.
 *
 * <p>The export runs as the {@link Export} stage, in a JVM and store of its own; the
 * import is this suite's store. Both stores and the export file are in this suite's own
 * directory under {@code target/generated-datastores}, so the suite runs alongside any
 * other.
 */
@Tag(TestTags.STAGED)
class ExportImportDataIT {
    private static final Logger LOG = LoggerFactory.getLogger(ExportImportDataIT.class);
    private static final File WORK = TestConstants.createFilePathInTargetFromClassName.apply(ExportImportDataIT.class);

    private static final String STORE = "store";
    private static final String EXPORT_FILE = "export.file";

    @BeforeAll
    static void beforeAll() {
        FileUtil.recursiveDelete(WORK);
        assertTrue(WORK.mkdirs(), "Could not create " + WORK);
        Properties in = new Properties();
        in.setProperty(STORE, new File(WORK, "export-store").getPath());
        in.setProperty(EXPORT_FILE, new File(WORK, "export-pb.zip").getPath());
        Properties exported = ForkedJvm.run(Export.class, in);

        TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, new File(WORK, "import-store"));

        // Load the original example data first
        LoadEntitiesFromProtobufFile loadProto = new LoadEntitiesFromProtobufFile(TestConstants.PB_EXAMPLE_DATA_REASONED);
        EntityCountSummary count = loadProto.compute();
        LOG.info(count + " entitles loaded from file: " + loadProto.summarize() + "\n\n");

        // Load the exported pb file from the export stage
        loadProto = new LoadEntitiesFromProtobufFile(new File(exported.getProperty(EXPORT_FILE)));
        count = loadProto.compute();
        LOG.info(count + " entitles loaded from file: " + loadProto.summarize() + "\n\n");
    }

    @AfterAll
    static void afterAll() {
        TestHelper.stopDatabase();
    }

    @Test
    public void testImportFieldTypeSetToList() {
        EntityProxy.Concept concept = EntityProxy.Concept.make(PublicIds.of(UUID.fromString("dde159ca-415e-4947-9174-cae7e8e7202d")));
        StateSet stateActive = StateSet.ACTIVE;
        StampCalculator stampCalcActive = StampCalculatorWithCache
                .getCalculator(StampCoordinateRecord.make(stateActive, Coordinates.Position.LatestOnDevelopment()));

        // NOTE. No binding declares EXAMPLE_PATTERN_TWO. Instead, tinkar-example-data repo creates its own EntityProxy.Pattern
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
                // A set: Active state is a member, in no particular position.
                assertTrue(intIdSet.get().contains(KernelTerm.ACTIVE_STATE.nid()));

                intIdList.set(latestPattern.getFieldWithMeaning(COMPONENT_LIST_FIELD_MEANING, latestActive2.get()));
                int [] tempListArray2 = intIdList.get().toArray();
                assertEquals(KernelTerm.ACTIVE_STATE.nid(), tempListArray2 [0]);

                atomicBoolean.set(true);
            }
        });
        assertTrue(atomicBoolean.get());
    }

    /**
     * A store of its own with the example data loaded, the first element of a semantic's
     * set and list fields changed to Active, exported to protobuf.
     */
    static class Export implements ForkedJvm.Stage {
        @Override
        public void run(Properties in, Properties out) throws Exception {
            out.putAll(in);
            TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, new File(in.getProperty(STORE)));
            try {
                LoadEntitiesFromProtobufFile loadProto = new LoadEntitiesFromProtobufFile(TestConstants.PB_EXAMPLE_DATA_REASONED);
                EntityCountSummary count = loadProto.compute();
                LOG.info(count + " entitles loaded from file: " + loadProto.summarize() + "\n\n");

                changeFirstSetAndListElementsToActive();

                File exportFile = new File(in.getProperty(EXPORT_FILE));
                long exported = new ExportEntitiesToProtobufFile(exportFile).compute().getTotalCount();
                LOG.info("Entities exported to protobuf: " + exported);
                out.setProperty("export.count", Long.toString(exported));
            } finally {
                TestHelper.stopDatabase();
            }
        }

        /**
         * Changes the cached semantic in place: {@code toArray()} of an id set or list
         * returns its backing array, so the export writes the changed elements.
         */
        private static void changeFirstSetAndListElementsToActive() {
            EntityProxy.Concept concept = EntityProxy.Concept.make(PublicIds.of(UUID.fromString("dde159ca-415e-4947-9174-cae7e8e7202d")));
            StampCalculator stampCalcActive = StampCalculatorWithCache
                    .getCalculator(StampCoordinateRecord.make(StateSet.ACTIVE, Coordinates.Position.LatestOnDevelopment()));

            // NOTE. No binding declares EXAMPLE_PATTERN_TWO. Instead, tinkar-example-data repo creates its own EntityProxy.Pattern
            EntityProxy.Pattern EXAMPLE_PATTERN_TWO = EntityProxy.Pattern.make("Example Pattern Two", UUID.fromString("7222d538-9641-474a-94ce-72c5bf6462b3"));
            EntityProxy.Concept COMPONENT_SET_FIELD_MEANING = EntityProxy.Concept.make(PublicIds.of(UUID.fromString("990e5a92-cdc2-4e23-a68d-1f01345b8759")));
            EntityProxy.Concept COMPONENT_LIST_FIELD_MEANING = EntityProxy.Concept.make(PublicIds.of(UUID.fromString("f0847cd3-2034-43f5-b25f-2bd6e923d228")));

            PatternEntityVersion latestPattern = (PatternEntityVersion) Calculators.Stamp.DevelopmentLatest().latest(EXAMPLE_PATTERN_TWO).get();

            EntityService.get().forEachSemanticForComponentOfPattern(concept.nid(), EXAMPLE_PATTERN_TWO.nid(), semanticEntity -> {
                Latest<SemanticEntityVersion> latestActive = stampCalcActive.latest(semanticEntity);
                if (latestActive.isPresent()) {
                    IntIdSetArray intIdSet = latestPattern.getFieldWithMeaning(COMPONENT_SET_FIELD_MEANING, latestActive.get());
                    intIdSet.toArray()[0] = KernelTerm.ACTIVE_STATE.nid();
                    IntIdListArray intIdList = latestPattern.getFieldWithMeaning(COMPONENT_LIST_FIELD_MEANING, latestActive.get());
                    intIdList.toArray()[0] = KernelTerm.ACTIVE_STATE.nid();
                }
            });
        }
    }
}
