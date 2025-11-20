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
import dev.ikm.tinkar.entity.ConceptRecord;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.entity.transform.EntityToTinkarSchemaTransformer;
import dev.ikm.tinkar.integration.StarterDataEphemeralProvider;
import dev.ikm.tinkar.schema.TinkarMsg;
import dev.ikm.tinkar.terms.State;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.list.ImmutableList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static dev.ikm.tinkar.integration.TestConstants.createFilePathInTarget;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test that generates a changeset file with a forward reference.
 * This test runs in a separate JVM/datastore from the import tests.
 *
 * The generated changeset contains:
 * 1. A description semantic (written FIRST)
 * 2. A concept that the semantic references (written SECOND)
 *
 * This creates a forward reference scenario where the semantic references
 * a concept that doesn't exist yet in the changeset.
 *
 * Outputs:
 * - forward-reference-changeset.zip: The changeset file
 *
 * UUIDs:
 * Uses deterministic UUID generation (UUID.nameUUIDFromBytes) so both
 * generation and import tests use the same UUIDs without needing a shared file.
 */
@ExtendWith(StarterDataEphemeralProvider.class)
class ForwardReferenceChangeSetGenerateStep {

    private static final Logger LOG = LoggerFactory.getLogger(ForwardReferenceChangeSetGenerateStep.class);

    // Public constants so ForwardReferenceChangeSetIT can reference them
    public static final File CHANGESET_FILE = createFilePathInTarget.apply("data/generated-forward-reference-changeset.zip");
    public static final UUID CONCEPT_UUID = UUID.nameUUIDFromBytes("forward-ref-test-concept".getBytes());
    public static final UUID SEMANTIC_UUID = UUID.nameUUIDFromBytes("forward-ref-test-semantic".getBytes());

    private File changesetFile;
    private PublicId newConceptPublicId;
    private PublicId descriptionSemanticPublicId;
    private StampEntity testStamp;

    @BeforeEach
    void beforeEach() {
        changesetFile = CHANGESET_FILE;
        newConceptPublicId = PublicIds.of(CONCEPT_UUID);
        descriptionSemanticPublicId = PublicIds.of(SEMANTIC_UUID);

        LOG.info("Will generate changeset at: {}", changesetFile.getAbsolutePath());
        LOG.info("Using concept UUID: {}", CONCEPT_UUID);
        LOG.info("Using semantic UUID: {}", SEMANTIC_UUID);
    }

    /**
     * Generates a changeset file with forward reference.
     */
    @Test
    @DisplayName("Generate changeset with forward reference")
    void testGenerateChangeSetWithForwardReference() throws IOException {
        LOG.info("Starting changeset generation with forward reference");
        LOG.info("Concept UUID: {}", CONCEPT_UUID);
        LOG.info("Semantic UUID: {}", SEMANTIC_UUID);

        // Create the test stamp
        testStamp = StampRecord.make(
                UUID.randomUUID(),
                State.ACTIVE,
                System.currentTimeMillis(),
                TinkarTerm.USER.publicId(),
                TinkarTerm.PRIMORDIAL_MODULE.publicId(),
                TinkarTerm.DEVELOPMENT_PATH.publicId()
        );
        EntityService.get().putEntity(testStamp);

        // Generate the changeset file
        createChangeSetWithForwardReference();

        // Verify file was created
        assertTrue(changesetFile.exists(), "Changeset file should be created");

        LOG.info("Successfully generated changeset: {}", changesetFile.getAbsolutePath());
    }

    /**
     * Creates a protobuf changeset file with a forward reference scenario.
     * The semantic description is written BEFORE the concept it references,
     * simulating a forward reference issue.
     */
    private void createChangeSetWithForwardReference() throws IOException {
        try (FileOutputStream fos = new FileOutputStream(changesetFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {

            // Create and write manifest
            writeManifest(zos);

            // Start protobuf data entry
            ZipEntry dataEntry = new ZipEntry("changeset.pb");
            zos.putNextEntry(dataEntry);

            EntityToTinkarSchemaTransformer transformer = EntityToTinkarSchemaTransformer.getInstance();

            // Prepare data for writing to changeset
            ConceptRecord newConcept = createNewConcept();
            SemanticRecord descriptionSemantic = createDescriptionSemantic();

            // Put entities into EntityService so transformer can resolve references
            // Note: We're putting them in the datastore here, but we'll write them to
            // the changeset in forward reference order (semantic before concept)
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

        LOG.info("Created changeset with forward reference at: {}", changesetFile.getAbsolutePath());
    }

    /**
     * Creates a new concept entity.
     */
    private ConceptRecord createNewConcept() {
        return ConceptRecord.build(
                newConceptPublicId,
                testStamp.lastVersion()
        );
    }

    /**
     * Creates a description semantic that references the new concept.
     * This semantic uses the DESCRIPTION_PATTERN from starter data.
     */
    private SemanticRecord createDescriptionSemantic() {
        // Create field values for description pattern
        // Description pattern fields: language, text, case significance, description type
        ImmutableList<Object> fieldValues = org.eclipse.collections.api.factory.Lists.immutable.of(
                TinkarTerm.ENGLISH_LANGUAGE.nid(),  // Language
                "Test Description for Forward Reference", // Text
                TinkarTerm.DESCRIPTION_NOT_CASE_SENSITIVE.nid(), // Case significance
                TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE.nid()  // Description type
        );

        // Note: We need to get the NID for the concept that doesn't exist yet
        // This will be registered in Pass 1 of the multi-pass import
        int conceptNid = EntityService.get().nidForPublicId(newConceptPublicId);

        return SemanticRecord.build(
                descriptionSemanticPublicId.asUuidArray()[0],
                TinkarTerm.DESCRIPTION_PATTERN.nid(),
                conceptNid,  // References the concept that will be written AFTER this semantic
                testStamp.lastVersion(),
                fieldValues
        );
    }

    /**
     * Writes the manifest entry to the changeset zip file.
     */
    private void writeManifest(ZipOutputStream zos) throws IOException {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().putValue("Total-Count", "2"); // 1 semantic + 1 concept

        ZipEntry manifestEntry = new ZipEntry("META-INF/MANIFEST.MF");
        zos.putNextEntry(manifestEntry);
        manifest.write(zos);
        zos.closeEntry();
    }
}

