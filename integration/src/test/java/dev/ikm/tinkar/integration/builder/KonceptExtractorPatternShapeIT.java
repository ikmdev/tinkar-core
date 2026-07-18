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

import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.builder.ActiveStamp;
import dev.ikm.tinkar.entity.builder.GraphFieldValue;
import dev.ikm.tinkar.entity.builder.KnowledgeSet;
import dev.ikm.tinkar.entity.builder.KonceptExtractor;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.DefaultsTemplateTerm;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link KonceptExtractor}'s pattern-specific {@code referencedComponentMeaning}/
 * {@code referencedComponentPurpose}/{@code fields} extraction (IKE-Network/ike-issues#880),
 * against a small hand-composed pattern — the real starter set's own patterns work equally
 * well, but a minimal, known-shape pattern makes the exact expected YAML unambiguous to
 * assert against.
 * <p>
 * The defaults documentation channel (IKE-Network/ike-issues#888) rides on the same
 * fixture: the probe pattern carries a default value semantic — referenced component
 * {@link DefaultsTemplateTerm#DEFAULT_VALUE_CONCEPT}, computed
 * {@code singleSemanticUuid} identity, stamped in
 * {@link DefaultsTemplateTerm#DEFAULTS_AND_TEMPLATES_MODULE} — so per-field
 * {@code default:} keys emit alongside {@code example:} keys. A second, defaults-only
 * pattern exercises the per-type display text across the exotic value space: entity
 * references (concept- and semantic-valued), sentinel numerals, {@code NaN}, byte arrays
 * (valid UTF-8 and not), {@code Object[]}, the premundane instant, and DiTree/DiGraph
 * values.
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
        // The referenced component of the example semantic below -- a real concept, so
        // referencedComponentExample resolves to its own koncept identifier (the bare,
        // unquoted style), the same way a field value that resolves to a koncept does.
        TEST_SET.concept("Pattern shape probe subject (Test)").at(birth)
                .definition("The subject an example semantic of the probe pattern attaches to.")
                .isA(TinkarTerm.MODEL_CONCEPT);

        TEST_SET.pattern("Pattern shape probe pattern (Test)").at(birth)
                .meaning(TEST_SET.conceptRef("Pattern shape probe meaning (Test)"))
                .purpose(TEST_SET.conceptRef("Pattern shape probe purpose (Test)"))
                .field(TEST_SET.conceptRef("Pattern shape probe field meaning (Test)"),
                        TEST_SET.conceptRef("Pattern shape probe field purpose (Test)"),
                        TEST_SET.conceptRef("Pattern shape probe field data type (Test)"));

        // One real semantic of the probe pattern -- exercises exampleSemanticOf()'s search and
        // both display-text branches: the referenced component resolves to a koncept identifier
        // (bare/unquoted), while this plain-String field value does not (quoted literal).
        TEST_SET.concept("Pattern shape probe subject (Test)").at(birth)
                .semantic(TEST_SET.patternRef("Pattern shape probe pattern (Test)"),
                        PublicIds.of(UUID.fromString("7a8b5c1d-2e3f-4a5b-8c9d-0e1f2a3b4c5d")),
                        "an example field value");

        composeDefaultsContent(birth);

        TEST_SET.write();
    }

    /**
     * Composes the defaults documentation channel's fixture (IKE-Network/ike-issues#888):
     * the two seam concepts minted with the identities tinkar-core declares, a default
     * value semantic on the existing probe pattern (so {@code default:} emits alongside
     * {@code example:}), and a second, defaults-only pattern whose twelve fields sweep the
     * per-type display-text space.
     *
     * @param birth the ordinary working-content stamp the rest of the fixture uses
     */
    private static void composeDefaultsContent(ActiveStamp birth) {
        // The seam concepts, minted with the identities tinkar-core declares -- the same
        // declared-identity discipline DefaultsTemplateCalculatorIT uses.
        TEST_SET.concept("Default value concept (IkeFoundation)",
                        DefaultsTemplateTerm.DEFAULT_VALUE_CONCEPT.publicId())
                .at(birth);
        TEST_SET.concept("Defaults and templates module (IkeFoundation)",
                        DefaultsTemplateTerm.DEFAULTS_AND_TEMPLATES_MODULE.publicId())
                .at(birth);

        // Instance content stamps in the defaults module -- the module IS the category
        // boundary that keeps a defaults semantic out of the example channel.
        ActiveStamp defaultsAuthored = Stamp.active("2020-02-01T00:00:00Z", TinkarTerm.USER,
                DefaultsTemplateTerm.DEFAULTS_AND_TEMPLATES_MODULE, TinkarTerm.DEVELOPMENT_PATH);

        // Per-field meaning concepts for the display-sweep pattern -- distinct, so each
        // emitted field block is unambiguous to assert against; purpose and data type are
        // shared (the extraction resolves them identically for every field).
        for (String fqn : List.of(
                "Concept default probe meaning (Test)",
                "Semantic default probe meaning (Test)",
                "Integer default probe meaning (Test)",
                "Float default probe meaning (Test)",
                "Long default probe meaning (Test)",
                "Decimal default probe meaning (Test)",
                "Byte array default probe meaning (Test)",
                "Binary default probe meaning (Test)",
                "Array default probe meaning (Test)",
                "Instant default probe meaning (Test)",
                "Tree default probe meaning (Test)",
                "Graph default probe meaning (Test)",
                "Defaults display probe purpose (Test)",
                "Defaults display probe data type (Test)",
                "Defaults display probe meaning (Test)",
                "Defaults display probe pattern purpose (Test)")) {
            TEST_SET.concept(fqn).at(birth).isA(TinkarTerm.MODEL_CONCEPT);
        }

        EntityProxy.Concept sweepPurpose = TEST_SET.conceptRef("Defaults display probe purpose (Test)");
        EntityProxy.Concept sweepDataType = TEST_SET.conceptRef("Defaults display probe data type (Test)");
        TEST_SET.pattern("Defaults display probe pattern (Test)").at(birth)
                .meaning(TEST_SET.conceptRef("Defaults display probe meaning (Test)"))
                .purpose(TEST_SET.conceptRef("Defaults display probe pattern purpose (Test)"))
                .field(TEST_SET.conceptRef("Concept default probe meaning (Test)"), sweepPurpose, sweepDataType)
                .field(TEST_SET.conceptRef("Semantic default probe meaning (Test)"), sweepPurpose, sweepDataType)
                .field(TEST_SET.conceptRef("Integer default probe meaning (Test)"), sweepPurpose, sweepDataType)
                .field(TEST_SET.conceptRef("Float default probe meaning (Test)"), sweepPurpose, sweepDataType)
                .field(TEST_SET.conceptRef("Long default probe meaning (Test)"), sweepPurpose, sweepDataType)
                .field(TEST_SET.conceptRef("Decimal default probe meaning (Test)"), sweepPurpose, sweepDataType)
                .field(TEST_SET.conceptRef("Byte array default probe meaning (Test)"), sweepPurpose, sweepDataType)
                .field(TEST_SET.conceptRef("Binary default probe meaning (Test)"), sweepPurpose, sweepDataType)
                .field(TEST_SET.conceptRef("Array default probe meaning (Test)"), sweepPurpose, sweepDataType)
                .field(TEST_SET.conceptRef("Instant default probe meaning (Test)"), sweepPurpose, sweepDataType)
                .field(TEST_SET.conceptRef("Tree default probe meaning (Test)"), sweepPurpose, sweepDataType)
                .field(TEST_SET.conceptRef("Graph default probe meaning (Test)"), sweepPurpose, sweepDataType);

        EntityProxy.Pattern probePattern = TEST_SET.patternRef("Pattern shape probe pattern (Test)");
        EntityProxy.Pattern sweepPattern = TEST_SET.patternRef("Defaults display probe pattern (Test)");
        UUID probeDefaultsId = UuidT5Generator.singleSemanticUuid(
                probePattern.publicId(), DefaultsTemplateTerm.DEFAULT_VALUE_CONCEPT.publicId());
        UUID sweepDefaultsId = UuidT5Generator.singleSemanticUuid(
                sweepPattern.publicId(), DefaultsTemplateTerm.DEFAULT_VALUE_CONCEPT.publicId());

        TEST_SET.concept("Default value concept (IkeFoundation)").at(defaultsAuthored)
                // The probe pattern's default: a single loud String, so its one field
                // carries default: alongside the example: from the ordinary semantic.
                .semantic(probePattern, PublicIds.of(probeDefaultsId), "UNINITIALIZED probe default")
                // The sweep pattern's default: one value per field, in field order.
                .semantic(sweepPattern, PublicIds.of(sweepDefaultsId),
                        // Concept -- resolves to a koncept identifier, bare/unquoted
                        TEST_SET.conceptRef("Pattern shape probe subject (Test)"),
                        // Semantic -- an entity with no description of its own renders
                        // structurally as "<pattern> on <referenced component>", never
                        // the <nid> debug fallback (the probe pattern's example semantic)
                        EntityProxy.Semantic.make("Pattern shape probe example semantic",
                                PublicIds.of(UUID.fromString("7a8b5c1d-2e3f-4a5b-8c9d-0e1f2a3b4c5d"))),
                        // Integer -- stretched-sevens sentinel
                        777_777_777,
                        // Float -- the native non-value
                        Float.NaN,
                        // Long -- stretched-sevens sentinel
                        777_777_777_777_777_777L,
                        // Decimal -- stretched sevens with the demonstrative decimal point
                        new BigDecimal("777777777.777"),
                        // ByteArray, valid UTF-8 -- decodes to its readable text
                        "REVISE ME".getBytes(StandardCharsets.UTF_8),
                        // ByteArray, NOT valid UTF-8 -- falls back to length + hex
                        new byte[]{(byte) 0xFF, (byte) 0xFE},
                        // Object[] -- bracket-joined element display texts
                        new Object[]{"first", "second"},
                        // Instant -- the premundane instant renders by name
                        PrimitiveData.PREMUNDANE_INSTANT,
                        // DiTree -- the smallest well-formed tree
                        new GraphFieldValue.Tree(
                                List.of(new GraphFieldValue.Vertex(
                                        TEST_SET.uuidFor("Defaults display probe tree vertex"),
                                        TEST_SET.conceptRef("Tree default probe meaning (Test)"))),
                                0, List.of()),
                        // DiGraph -- the deliberate two-vertex simple cycle
                        new GraphFieldValue.Graph(
                                List.of(new GraphFieldValue.Vertex(
                                                TEST_SET.uuidFor("Defaults display probe graph vertex A"),
                                                TEST_SET.conceptRef("Graph default probe meaning (Test)")),
                                        new GraphFieldValue.Vertex(
                                                TEST_SET.uuidFor("Defaults display probe graph vertex B"),
                                                TEST_SET.conceptRef("Graph default probe meaning (Test)"))),
                                List.of(),
                                List.of(new GraphFieldValue.Edge(0, 1),
                                        new GraphFieldValue.Edge(1, 0))));
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

    @Test
    @DisplayName("a real semantic's referenced component and field values extract as live examples")
    void exampleValuesExtractFromARealSemantic() {
        String yaml = KonceptExtractor.extractYaml();
        String block = entryBlock(yaml, "PatternShapeProbePattern");

        assertTrue(block.contains("  referencedComponentExample: PatternShapeProbeSubject\n"),
                "an entity-valued referenced component must resolve to its own koncept "
                        + "identifier, bare and unquoted:\n" + block);
        assertTrue(block.contains("      example: \"an example field value\"\n"),
                "a plain-String field value has no koncept identifier to resolve to, so it "
                        + "must render as a quoted literal, not a bare identifier:\n" + block);
    }

    @Test
    @DisplayName("a pattern's default value semantic emits per-field default: keys alongside example:")
    void defaultValuesEmitAlongsideExamples() {
        String yaml = KonceptExtractor.extractYaml();
        String block = entryBlock(yaml, "PatternShapeProbePattern");

        assertTrue(block.contains("      example: \"an example field value\"\n"
                        + "      default: \"UNINITIALIZED probe default\"\n"),
                "the default value semantic's field value must emit as default: on the same "
                        + "field that carries the ordinary semantic's example::\n" + block);
    }

    @Test
    @DisplayName("default values render readably across the full field data-type space")
    void defaultValuesRenderReadablyPerType() {
        String yaml = KonceptExtractor.extractYaml();
        String block = entryBlock(yaml, "DefaultsDisplayProbePattern");

        assertFalse(block.contains("example:"),
                "the defaults semantic is not a domain assertion — a defaults-only pattern "
                        + "has no example channel:\n" + block);

        assertTrue(block.contains("    - meaning: ConceptDefaultProbeMeaning\n"
                        + "      purpose: DefaultsDisplayProbePurpose\n"
                        + "      dataType: DefaultsDisplayProbeDataType\n"
                        + "      default: PatternShapeProbeSubject\n"),
                "an entity-valued default must resolve to its koncept identifier, bare and "
                        + "unquoted:\n" + block);
        assertTrue(block.contains("    - meaning: SemanticDefaultProbeMeaning\n"
                        + "      purpose: DefaultsDisplayProbePurpose\n"
                        + "      dataType: DefaultsDisplayProbeDataType\n"
                        + "      default: \"PatternShapeProbePattern on PatternShapeProbeSubject\"\n"),
                "a semantic-valued default (no description of its own) must render "
                        + "structurally, never as the <nid> debug fallback:\n" + block);
        assertTrue(block.contains("      default: \"777777777\"\n"),
                "the Integer sevens sentinel must render as its numeral:\n" + block);
        assertTrue(block.contains("      default: \"NaN\"\n"),
                "Float NaN must render as NaN:\n" + block);
        assertTrue(block.contains("      default: \"777777777777777777\"\n"),
                "the Long sevens sentinel must render as its numeral:\n" + block);
        assertTrue(block.contains("      default: \"777777777.777\"\n"),
                "the Decimal sevens sentinel must keep its demonstrative decimal point:\n" + block);
        assertTrue(block.contains("      default: \"REVISE ME\"\n"),
                "a valid-UTF-8 byte array must decode to its readable text:\n" + block);
        assertTrue(block.contains("      default: \"2 bytes: 0xfffe\"\n"),
                "a non-UTF-8 byte array must fall back to length + hex, never [B@hash:\n" + block);
        assertTrue(block.contains("      default: \"[first, second]\"\n"),
                "an Object[] must bracket-join its elements' display texts:\n" + block);
        assertTrue(block.contains("      default: \"Premundane\"\n"),
                "the premundane instant must render by name, not as a raw epoch extreme:\n" + block);
        assertTrue(block.contains("      default: \"1-vertex tree\"\n"),
                "a DiTree default must render compactly by shape:\n" + block);
        assertTrue(block.contains("      default: \"2-vertex cycle\"\n"),
                "the deliberate DiGraph simple cycle must render as a cycle, proving the "
                        + "graph-shape detection:\n" + block);
    }

    /** The text from an entry's identifier header to the next blank line. */
    private static String entryBlock(String yaml, String identifier) {
        int start = yaml.indexOf(identifier + ":\n");
        assertTrue(start >= 0, identifier + " was not extracted at all:\n" + yaml);
        int end = yaml.indexOf("\n\n", start);
        return yaml.substring(start, end < 0 ? yaml.length() : end);
    }
}
