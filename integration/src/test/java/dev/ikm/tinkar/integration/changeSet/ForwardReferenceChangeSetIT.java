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

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.ConceptRecord;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.entity.transform.EntityToTinkarSchemaTransformer;
import dev.ikm.tinkar.fixtures.ForkedJvm;
import dev.ikm.tinkar.fixtures.OpenSpinedArrayKeyValueProvider;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.fixtures.TestTags;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.schema.TinkarMsg;
import dev.ikm.tinkar.terms.KernelTerm;
import dev.ikm.tinkar.terms.State;
import org.eclipse.collections.api.list.ImmutableList;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for forward reference handling in changeset imports.
 * Tests the multi-pass import mechanism that resolves forward references where
 * a semantic references a concept that appears later in the changeset.
 *
 * <p>The changeset is generated first, by the {@link Generate} stage in a JVM and
 * store of its own, so that its concept and semantic do not exist in this suite's
 * store when the import tests begin. It contains:
 * <ol>
 *   <li>A description semantic (written FIRST)</li>
 *   <li>A concept that the semantic references (written SECOND)</li>
 * </ol>
 *
 * <p>The multi-pass algorithm:
 * <ul>
 *   <li>Pass 1: Imports all non-semantics (Concepts, Patterns, Stamps)</li>
 *   <li>Pass 2+: Imports semantics whose referenced components exist in the database</li>
 *   <li>Repeats until all semantics are imported or no progress is made</li>
 * </ul>
 *
 * <p>The changeset and the generating stage's store are in this suite's own directory
 * under {@code target/generated-datastores}, so the suite runs alongside any other.
 * Tests are ordered to ensure the 1-pass test runs first (before entities exist in
 * datastore).
 */
@ExtendWith(OpenSpinedArrayKeyValueProvider.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Tag(TestTags.STAGED)
class ForwardReferenceChangeSetIT {
    private static final Logger LOG = LoggerFactory.getLogger(ForwardReferenceChangeSetIT.class);

    private static final File WORK = TestConstants.createFilePathInTargetFromClassName.apply(ForwardReferenceChangeSetIT.class);

    /** Deterministic, so the generating stage and the import tests agree without sharing anything else. */
    static final UUID CONCEPT_UUID = UUID.nameUUIDFromBytes("forward-ref-test-concept".getBytes());
    static final UUID SEMANTIC_UUID = UUID.nameUUIDFromBytes("forward-ref-test-semantic".getBytes());

    private static final String STORE = "store";
    private static final String CHANGESET_FILE = "changeset.file";

    private File changesetFile;
    private final PublicId newConceptPublicId = PublicIds.of(CONCEPT_UUID);
    private final PublicId descriptionSemanticPublicId = PublicIds.of(SEMANTIC_UUID);

    @BeforeAll
    void generateChangeSet() {
        FileUtil.recursiveDelete(WORK);
        assertTrue(WORK.mkdirs(), "Could not create " + WORK);
        Properties in = new Properties();
        in.setProperty(STORE, new File(WORK, "generate-store").getPath());
        in.setProperty(CHANGESET_FILE, new File(WORK, "forward-reference-changeset.zip").getPath());
        Properties out = ForkedJvm.run(Generate.class, in);
        changesetFile = new File(out.getProperty(CHANGESET_FILE));
        assertTrue(changesetFile.exists(), "Changeset file should be created");
    }

    @BeforeEach
    void beforeEach() {
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
    }

    /**
     * Test that 1-pass import succeeds even with forward references.
     * The semantic is written before the concept it references, but
     * Entity.nid() assigns NIDs without requiring the entity to exist.
     */
    @Test
    @Order(1)
    @DisplayName("1-pass import should succeed with forward reference")
    void testOnePassImportSucceedsWithForwardReference() {
        LOG.info("Testing 1-pass import with forward reference - expecting success");

        // Create loader with 1-pass mode (useTwoPassImport = false)
        LoadEntitiesFromProtobufFile loader = new LoadEntitiesFromProtobufFile(changesetFile, false);

        // Should succeed - Entity.nid() assigns NIDs without requiring entity to exist
        var summary = loader.compute();
        LOG.info("1-pass import succeeded: {}", summary);
        assertNotNull(summary);

        // Verify both entities were loaded
        ConceptEntity loadedConcept = EntityHandle.get(newConceptPublicId).expectConcept();
        SemanticEntity loadedSemantic = EntityHandle.get(descriptionSemanticPublicId).expectSemantic();
        assertNotNull(loadedConcept, "New concept should be loaded");
        assertNotNull(loadedSemantic, "Description semantic should be loaded");
    }

    /**
     * Test that multi-pass import succeeds with forward references.
     * Pass 1: Imports all non-semantics (concept exists)
     * Pass 2: Imports the semantic that references the now-existing concept
     *
     * This test runs second, after the 1-pass test has demonstrated the failure scenario.
     */
    @Test
    @Order(2)
    @DisplayName("Multi-pass import should succeed with forward reference")
    void testMultiPassImportSucceedsWithForwardReference() {
        LOG.info("Testing multi-pass import with forward reference - expecting success");

        // Create loader with multi-pass mode (default)
        LoadEntitiesFromProtobufFile loader = new LoadEntitiesFromProtobufFile(changesetFile, true);

        // Should succeed - Pass 1 imports concept, Pass 2 imports semantic
        var summary = loader.compute();
        LOG.info("Multi-pass import succeeded: {}", summary);
        assertNotNull(summary);

        // Verify both entities were loaded using EntityHandle
        ConceptEntity loadedConcept = EntityHandle.get(newConceptPublicId).expectConcept();
        SemanticEntity loadedSemantic = EntityHandle.get(descriptionSemanticPublicId).expectSemantic();
        assertNotNull(loadedConcept, "New concept should be loaded");
        assertNotNull(loadedSemantic, "Description semantic should be loaded");

        LOG.info("Successfully loaded concept: {}", loadedConcept);
        LOG.info("Successfully loaded semantic: {}", loadedSemantic);
    }

    @Test
    @Order(3)
    @DisplayName("Auto-detected import mode should match provider requirement")
    void testAutoDetectedImportMode() {
        // This test verifies the auto-detection matches the provider
        boolean providerRequiresMultiPass = PrimitiveData.requiresMultiPassImport();
        LOG.info("Provider requires multi-pass: {}", providerRequiresMultiPass);

        // The default constructor should use the provider's preference
        LoadEntitiesFromProtobufFile loader = new LoadEntitiesFromProtobufFile(changesetFile);

        // Verify it works (the mode is chosen correctly for the provider)
        var summary = loader.compute();
        assertNotNull(summary);
    }

    /**
     * Writes a protobuf changeset with a forward reference: the description semantic
     * BEFORE the concept it references. Runs in a store of its own, with the starter data
     * loaded so the transformer can resolve the semantic's references.
     */
    static class Generate implements ForkedJvm.Stage {
        @Override
        public void run(Properties in, Properties out) throws Exception {
            out.putAll(in);
            TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, new File(in.getProperty(STORE)));
            try {
                TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
                StampEntity testStamp = StampRecord.make(
                        UUID.randomUUID(),
                        State.ACTIVE,
                        System.currentTimeMillis(),
                        KernelTerm.USER.publicId(),
                        KernelTerm.PRIMORDIAL_MODULE.publicId(),
                        KernelTerm.DEVELOPMENT_PATH.publicId()
                );
                EntityService.get().putEntity(testStamp);
                File changesetFile = new File(in.getProperty(CHANGESET_FILE));
                createChangeSetWithForwardReference(changesetFile, testStamp);
                LOG.info("Created changeset with forward reference at: {}", changesetFile.getAbsolutePath());
            } finally {
                TestHelper.stopDatabase();
            }
        }

        private static void createChangeSetWithForwardReference(File changesetFile, StampEntity testStamp)
                throws IOException {
            try (FileOutputStream fos = new FileOutputStream(changesetFile);
                 ZipOutputStream zos = new ZipOutputStream(fos)) {

                writeManifest(zos);

                ZipEntry dataEntry = new ZipEntry("changeset.pb");
                zos.putNextEntry(dataEntry);

                EntityToTinkarSchemaTransformer transformer = EntityToTinkarSchemaTransformer.getInstance();

                ConceptRecord newConcept = ConceptRecord.build(PublicIds.of(CONCEPT_UUID), testStamp.lastVersion());
                SemanticRecord descriptionSemantic = createDescriptionSemantic(testStamp);

                // Put entities into EntityService so transformer can resolve references.
                // They are written to the changeset in forward reference order (semantic before concept).
                EntityService.get().putEntity(newConcept);
                EntityService.get().putEntity(descriptionSemantic);

                // FORWARD REFERENCE: Write description semantic BEFORE the concept
                TinkarMsg semanticMsg = transformer.transform(descriptionSemantic);
                semanticMsg.writeDelimitedTo(zos);
                LOG.info("Wrote description semantic BEFORE concept (forward reference)");

                // Write the new concept AFTER the semantic that references it
                TinkarMsg conceptMsg = transformer.transform(newConcept);
                conceptMsg.writeDelimitedTo(zos);
                LOG.info("Wrote concept AFTER description semantic");

                zos.closeEntry();
            }
        }

        /**
         * A description semantic that references the new concept, using the
         * DESCRIPTION_PATTERN from starter data.
         */
        private static SemanticRecord createDescriptionSemantic(StampEntity testStamp) {
            // Description pattern fields: language, text, case significance, description type
            ImmutableList<Object> fieldValues = org.eclipse.collections.api.factory.Lists.immutable.of(
                    KernelTerm.ENGLISH_LANGUAGE.nid(),
                    "Test Description for Forward Reference",
                    KernelTerm.DESCRIPTION_NOT_CASE_SENSITIVE.nid(),
                    KernelTerm.REGULAR_NAME_DESCRIPTION_TYPE.nid()
            );
            // The nid of a concept that is written AFTER this semantic
            int conceptNid = EntityService.get().nidForPublicId(PublicIds.of(CONCEPT_UUID));
            return SemanticRecord.build(
                    SEMANTIC_UUID,
                    KernelTerm.DESCRIPTION_PATTERN.nid(),
                    conceptNid,
                    testStamp.lastVersion(),
                    fieldValues
            );
        }

        private static void writeManifest(ZipOutputStream zos) throws IOException {
            Manifest manifest = new Manifest();
            manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
            manifest.getMainAttributes().putValue("Total-Count", "2"); // 1 semantic + 1 concept
            ZipEntry manifestEntry = new ZipEntry("META-INF/MANIFEST.MF");
            zos.putNextEntry(manifestEntry);
            manifest.write(zos);
            zos.closeEntry();
        }
    }
}
