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
package dev.ikm.tinkar.integration.builder;

import dev.ikm.tinkar.entity.builder.ActiveStamp;
import dev.ikm.tinkar.entity.builder.KnowledgeSet;
import dev.ikm.tinkar.entity.builder.KonceptExtractor;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link KonceptExtractor}'s pattern-specific {@code referencedComponentMeaning}/
 * {@code referencedComponentPurpose}/{@code fields} extraction (IKE-Network/ike-issues#880),
 * against a small hand-composed pattern — the real starter set's own patterns work equally
 * well, but a minimal, known-shape pattern makes the exact expected YAML unambiguous to
 * assert against.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KonceptExtractorPatternShapeIT {

    private static final KnowledgeSet TEST_SET =
            KnowledgeSet.of("6f2b8d4a-3c5e-5a7b-9d1f-4e6c8a2b5d7f");

    @BeforeAll
    static void composeAndWrite() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);

        ActiveStamp birth = Stamp.active("2020-01-01T00:00:00Z",
                TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH);

        // Self-minted, not TinkarTerm constants: a bare ephemeral store only materializes the
        // handful of TinkarTerm concepts STAMP dimensions themselves need (USER, DEVELOPMENT_
        // MODULE, DEVELOPMENT_PATH, ...) -- most others carry no FQN description here, so
        // identifierByNid wouldn't resolve them. Minting our own guarantees every meaning/
        // purpose/dataType concept this test references is genuinely extractable.
        TEST_SET.concept("Pattern shape probe meaning (Test)").at(birth)
                .definition("Meaning concept for the pattern-shape probe test.")
                .isA(TinkarTerm.MODEL_CONCEPT);
        TEST_SET.concept("Pattern shape probe purpose (Test)").at(birth)
                .definition("Purpose concept for the pattern-shape probe test.")
                .isA(TinkarTerm.MODEL_CONCEPT);
        TEST_SET.concept("Pattern shape probe field meaning (Test)").at(birth)
                .definition("Field meaning concept for the pattern-shape probe test.")
                .isA(TinkarTerm.MODEL_CONCEPT);
        TEST_SET.concept("Pattern shape probe field purpose (Test)").at(birth)
                .definition("Field purpose concept for the pattern-shape probe test.")
                .isA(TinkarTerm.MODEL_CONCEPT);
        TEST_SET.concept("Pattern shape probe field data type (Test)").at(birth)
                .definition("Field data-type concept for the pattern-shape probe test.")
                .isA(TinkarTerm.MODEL_CONCEPT);

        TEST_SET.pattern("Pattern shape probe pattern (Test)").at(birth)
                .meaning(TEST_SET.conceptRef("Pattern shape probe meaning (Test)"))
                .purpose(TEST_SET.conceptRef("Pattern shape probe purpose (Test)"))
                .field(TEST_SET.conceptRef("Pattern shape probe field meaning (Test)"),
                        TEST_SET.conceptRef("Pattern shape probe field purpose (Test)"),
                        TEST_SET.conceptRef("Pattern shape probe field data type (Test)"));

        TEST_SET.write();
    }

    @AfterAll
    static void afterAll() {
        TestHelper.stopDatabase();
    }

    @Test
    @DisplayName("a pattern's referencedComponentMeaning/Purpose and fields extract correctly")
    void patternShapeExtracts() {
        String yaml = KonceptExtractor.extractYaml();
        String block = entryBlock(yaml, "PatternShapeProbePattern");

        assertTrue(block.contains("  referencedComponentMeaning: PatternShapeProbeMeaning\n"),
                "referencedComponentMeaning must resolve to the pattern's own meaning concept:\n" + block);
        assertTrue(block.contains("  referencedComponentPurpose: PatternShapeProbePurpose\n"),
                "referencedComponentPurpose must resolve to the pattern's own purpose concept:\n" + block);

        assertTrue(block.contains("  fields:\n"), "fields: must be present:\n" + block);
        assertTrue(block.contains("    - meaning: PatternShapeProbeFieldMeaning\n"
                        + "      purpose: PatternShapeProbeFieldPurpose\n"
                        + "      dataType: PatternShapeProbeFieldDataType\n"),
                "the field's meaning/purpose/dataType must resolve correctly:\n" + block);
    }

    /** The text from an entry's identifier header to the next blank line. */
    private static String entryBlock(String yaml, String identifier) {
        int start = yaml.indexOf(identifier + ":\n");
        assertTrue(start >= 0, identifier + " was not extracted at all:\n" + yaml);
        int end = yaml.indexOf("\n\n", start);
        return yaml.substring(start, end < 0 ? yaml.length() : end);
    }
}
