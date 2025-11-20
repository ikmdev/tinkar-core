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
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.integration.StarterDataEphemeralProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Integration test for forward reference handling in changeset imports.
 * Tests the multi-pass import mechanism that resolves forward references where
 * a semantic references a concept that appears later in the changeset.
 *
 * The multi-pass algorithm:
 * - Pass 1: Imports all non-semantics (Concepts, Patterns, Stamps)
 * - Pass 2+: Imports semantics whose referenced components exist in the database
 * - Repeats until all semantics are imported or no progress is made
 *
 * Prerequisites:
 * - GenerateForwardReferenceChangeSetIT must run first to create the changeset file
 * - Changeset file: forward-reference-changeset.zip
 * - UUIDs: References GenerateForwardReferenceChangeSetIT.CONCEPT_UUID and SEMANTIC_UUID
 *
 * Tests are ordered to ensure the 1-pass test runs first (before entities exist in datastore).
 */
@ExtendWith(StarterDataEphemeralProvider.class)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ForwardReferenceChangeSetIngestStep {

    private static final Logger LOG = LoggerFactory.getLogger(ForwardReferenceChangeSetIngestStep.class);

    @TempDir
    Path tempDir;

    private final File changesetFile = ForwardReferenceChangeSetGenerateStep.CHANGESET_FILE;
    private PublicId newConceptPublicId;
    private PublicId descriptionSemanticPublicId;

    @BeforeEach
    void beforeEach() {
        // Load the pre-generated changeset file from test resources
        if (!changesetFile.exists()) {
            throw new IllegalStateException(
                    "Changeset file not found. Run ForwardReferenceChangeSetGenerateIT first.");
        }

        // Use the same UUIDs that were used in generation test
        newConceptPublicId = PublicIds.of(ForwardReferenceChangeSetGenerateStep.CONCEPT_UUID);
        descriptionSemanticPublicId = PublicIds.of(ForwardReferenceChangeSetGenerateStep.SEMANTIC_UUID);

        LOG.info("Loaded changeset file: {}", changesetFile.getAbsolutePath());
        LOG.info("Using concept UUID: {}", ForwardReferenceChangeSetGenerateStep.CONCEPT_UUID);
        LOG.info("Using semantic UUID: {}", ForwardReferenceChangeSetGenerateStep.SEMANTIC_UUID);
    }

    /**
     * Test that 1-pass import fails when encountering a forward reference.
     * The semantic is written before the concept it references, causing
     * the transformer to fail when trying to resolve the concept.
     *
     * This test runs first to ensure the entities don't exist in the datastore yet.
     */
    @Test
    @Order(1)
    @DisplayName("1-pass import should fail with forward reference")
    void testOnePassImportFailsWithForwardReference() {
        LOG.info("Testing 1-pass import with forward reference - expecting failure");


        // Create loader with 1-pass mode (useTwoPassImport = false)
        LoadEntitiesFromProtobufFile loader = new LoadEntitiesFromProtobufFile(changesetFile, false);

        // Should throw exception when trying to resolve the concept that doesn't exist yet
        RuntimeException exception = assertThrows(RuntimeException.class, loader::compute);

        LOG.info("1-pass import failed as expected: {}", exception.getMessage());
        assertNotNull(exception);
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
}
