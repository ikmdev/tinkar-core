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
package dev.ikm.tinkar.integration.format;

import dev.ikm.tinkar.common.id.LongIdList;
import dev.ikm.tinkar.common.id.LongIdSet;
import dev.ikm.tinkar.common.id.LongIds;
import dev.ikm.tinkar.common.id.Nid;
import dev.ikm.tinkar.common.service.EntityRecordFormat3;
import dev.ikm.tinkar.component.FieldDataType;
import dev.ikm.tinkar.component.location.PlanarPoint;
import dev.ikm.tinkar.component.location.SpatialPoint;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityFormat2;
import dev.ikm.tinkar.entity.EntityRecordFactory;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.FieldDefinitionRecord;
import dev.ikm.tinkar.entity.PatternRecord;
import dev.ikm.tinkar.entity.PatternVersionRecord;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.SemanticVersionRecord;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.entity.StampVersionRecord;
import dev.ikm.tinkar.entity.graph.DiGraphAbstract;
import dev.ikm.tinkar.entity.graph.DiGraphEntity;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.fixtures.NewEphemeralKeyValueProvider;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import io.activej.bytebuf.ByteBuf;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Entity format 2, the first 64-bit format, read from the fixtures captured when it was made
 * (design {@code design-2026-10-07-64-bit-rocks-store}; IKE-Network/ike-issues#1258), now that
 * format 3 replaced it and only a store created before then holds it (IKE-Network/ike-issues#1313).
 * Nothing writes format 2 any more, so each case is checked one way: what {@link EntityFormat2}
 * reads from a fixture is the value the fixture was captured from. A record read from format 2
 * upgrades to the same entity's format 3 record, and format 3 still refuses a nid of another kind
 * than its place names, as the fixtures of format 2 used.
 */
@ExtendWith(NewEphemeralKeyValueProvider.class)
class EntityFormat2FixturesTest {

    private static final String RESOURCE_DIRECTORY = "entity-format-2";

    private static final long CONCEPT_NID = Nid.compose64(2, 101);
    private static final long CONCEPT_NID_HIGH = Nid.compose64(200, 20_000_002);
    private static final long PATTERN_NID = Nid.compose64(1, 3);
    private static final long SEMANTIC_NID = Nid.compose64(4, 104);
    private static final long SEMANTIC_NID_HIGH = Nid.compose64(130, 105);
    private static final long STAMP_NID = Nid.compose64(3, 106);
    private static final long STAMP_NID_2 = Nid.compose64(3, 107);
    private static final long TIME = 1_767_225_600_777L;

    private static final UUID UUID_1 = UUID.fromString("0f6c6a6e-6b1d-4c0c-8b8e-3f1a2b3c4d01");
    private static final UUID UUID_2 = UUID.fromString("0f6c6a6e-6b1d-4c0c-8b8e-3f1a2b3c4d02");

    /**
     * The concept fixture with its nid in the concept pattern, (2, 20,000,002) for (200,
     * 20,000,002): format 3's concept fixture holds the same concept.
     */
    private static final String KINDED_CONCEPT =
            "0227010282dac4090f6c6a6e6b1d4c0c8b8e3f1a2b3c4d01020f6c6a6e6b1d4c0c8b8e3f1a2b3c4d02020304036a0304036b";

    /** The stamp fixture with its author and path in the concept pattern, as for the concept. */
    private static final String KINDED_STAMP =
            "021407036a0f6c6a6e6b1d4c0c8b8e3f1a2b3c4d0100011919036a02650000019b76daab090282dac40902650282dac409";

    // ---------- fields ----------

    static Map<String, Supplier<Object>> fields() {
        Map<String, Supplier<Object>> fields = new LinkedHashMap<>();
        fields.put("field-boolean", () -> Boolean.TRUE);
        fields.put("field-float", () -> 3.25f);
        fields.put("field-double", () -> 0.1f);
        fields.put("field-byte-array", () -> new byte[]{0, 1, 2, (byte) 0xFE, (byte) 0xFF});
        fields.put("field-integer", () -> -123_456_789);
        fields.put("field-long", () -> 0x0123_4567_89AB_CDEFL);
        fields.put("field-decimal", () -> new BigDecimal("-12345.678900"));
        fields.put("field-instant", () -> Instant.ofEpochSecond(1_767_225_600L, 777_000_123));
        fields.put("field-string", () -> "Format 2 — ünïcode");
        fields.put("field-concept", () -> EntityProxy.Concept.make(CONCEPT_NID_HIGH));
        fields.put("field-semantic", () -> EntityProxy.Semantic.make(SEMANTIC_NID_HIGH));
        fields.put("field-pattern", () -> EntityProxy.Pattern.make(PATTERN_NID));
        fields.put("field-identified-thing", () -> EntityProxy.make(CONCEPT_NID));
        fields.put("field-component-id-list", () -> LongIds.list.of(CONCEPT_NID_HIGH, CONCEPT_NID, SEMANTIC_NID_HIGH, CONCEPT_NID));
        fields.put("field-component-id-set", () -> LongIds.set.of(CONCEPT_NID, CONCEPT_NID_HIGH, SEMANTIC_NID, SEMANTIC_NID_HIGH, PATTERN_NID,
                Nid.compose64(2, 102), Nid.compose64(2, 300)));
        fields.put("field-object-array", () -> new Object[]{"element", 42, EntityProxy.Concept.make(CONCEPT_NID_HIGH)});
        fields.put("field-planar-point", () -> new PlanarPoint(1.5f, -2.5f));
        fields.put("field-spatial-point", () -> new SpatialPoint(7.75f, 1.5f, -2.5f));
        fields.put("field-ditree", () -> new Tree(
                new Vertex[]{
                        new Vertex("55555555-5555-5555-5555-555555555501", CONCEPT_NID, Map.of(
                                CONCEPT_NID, EntityProxy.Concept.make(CONCEPT_NID_HIGH),
                                CONCEPT_NID_HIGH, 17,
                                SEMANTIC_NID_HIGH, "property")),
                        new Vertex("55555555-5555-5555-5555-555555555502", CONCEPT_NID_HIGH, Map.of()),
                        new Vertex("55555555-5555-5555-5555-555555555503", CONCEPT_NID, Map.of())},
                Map.of(0, new int[]{1, 2})));
        fields.put("field-digraph", () -> new Tree(
                new Vertex[]{
                        new Vertex("66666666-6666-6666-6666-666666666601", CONCEPT_NID_HIGH, Map.of()),
                        new Vertex("66666666-6666-6666-6666-666666666602", CONCEPT_NID, Map.of())},
                Map.of(0, new int[]{1})));
        return fields;
    }

    static Stream<String> fieldNames() {
        return fields().keySet().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("fieldNames")
    void fieldReadFromTheFixtureIsTheValueItWasCapturedFrom(String name) {
        ByteBuf readBuf = ByteBuf.wrapForReading(fixture(name));
        Object value = EntityFormat2.readField(readBuf, FieldDataType.fromToken(readBuf.readByte()));
        assertEquals(0, readBuf.readRemaining(), name + " must be read to its last byte");
        assertSameValue(name, fields().get(name).get(), value);
    }

    @Test
    void theFacadeReadsAFormat2Field() {
        ByteBuf readBuf = ByteBuf.wrapForReading(fixture("field-string"));
        FieldDataType type = FieldDataType.fromToken(readBuf.readByte());
        assertEquals("Format 2 — ünïcode", EntityRecordFactory.readFieldData(readBuf, type, EntityFormat2.FORMAT));
    }

    // ---------- entities ----------

    @Test
    void conceptReadFromTheFixtureKeepsItsIdentityAndVersions() {
        ConceptEntity<?> concept = assertInstanceOf(ConceptEntity.class, EntityFormat2.read(fixture("entity-concept")));
        assertEquals(CONCEPT_NID_HIGH, concept.nid());
        assertArrayEquals(new UUID[]{UUID_1, UUID_2}, concept.asUuidArray());
        assertArrayEquals(new long[]{STAMP_NID, STAMP_NID_2}, stamps(concept));
    }

    @Test
    void patternReadFromTheFixtureKeepsItsFieldDefinitions() {
        PatternRecord pattern = EntityFormat2.read(fixture("entity-pattern"));
        assertEquals(PATTERN_NID, pattern.nid());
        assertArrayEquals(new UUID[]{UUID_1}, pattern.asUuidArray());
        PatternVersionRecord version = pattern.versions().getOnly();
        assertEquals(STAMP_NID, version.stampNid());
        assertEquals(CONCEPT_NID, version.semanticPurposeNid());
        assertEquals(CONCEPT_NID_HIGH, version.semanticMeaningNid());
        assertEquals(2, version.fieldDefinitions().size());
        assertDefinition(version.fieldDefinitions().get(0), CONCEPT_NID, CONCEPT_NID_HIGH, CONCEPT_NID, 0);
        assertDefinition(version.fieldDefinitions().get(1), CONCEPT_NID_HIGH, CONCEPT_NID, CONCEPT_NID_HIGH, 1);
    }

    @Test
    void semanticReadFromTheFixtureKeepsItsPatternReferenceAndFields() {
        SemanticRecord semantic = EntityFormat2.read(fixture("entity-semantic"));
        assertEquals(SEMANTIC_NID_HIGH, semantic.nid());
        assertEquals(PATTERN_NID, semantic.patternNid());
        assertEquals(CONCEPT_NID_HIGH, semantic.referencedComponentNid());
        assertArrayEquals(new UUID[]{UUID_2}, semantic.asUuidArray());
        SemanticVersionRecord version = semantic.versions().getOnly();
        assertEquals(STAMP_NID, version.stampNid());
        assertSameValue("semantic fields", new Object[]{"description text", EntityProxy.Concept.make(CONCEPT_NID),
                        EntityProxy.Concept.make(CONCEPT_NID_HIGH), LongIds.set.of(CONCEPT_NID, CONCEPT_NID_HIGH), 7},
                version.fieldValues().toArray());
    }

    @Test
    void stampReadFromTheFixtureKeepsItsStatusTimeAuthorModuleAndPath() {
        StampRecord stamp = EntityFormat2.read(fixture("entity-stamp"));
        assertEquals(STAMP_NID, stamp.nid());
        StampVersionRecord version = stamp.versions().getOnly();
        assertEquals(CONCEPT_NID, version.stateNid());
        assertEquals(TIME, version.time());
        assertEquals(CONCEPT_NID_HIGH, version.authorNid());
        assertEquals(CONCEPT_NID, version.moduleNid());
        assertEquals(CONCEPT_NID_HIGH, version.pathNid());
    }

    @Test
    void theFacadeReadsAFormat2Record() {
        Entity<? extends EntityVersion> entity = EntityRecordFactory.make(fixture("entity-semantic"));
        assertEquals(SEMANTIC_NID_HIGH, entity.nid());
    }

    @Test
    void aFormat2RecordUpgradesToTheSameEntitysFormat3Record() {
        assertUpgradesTo(KINDED_CONCEPT, "entity-concept");
        assertUpgradesTo(KINDED_STAMP, "entity-stamp");
    }

    @Test
    void theUpgradeRefusesANidOfAnotherKindThanItsPlace() {
        // The fixtures' concept nid lies in pattern 200, a semantic's, which format 3 refuses.
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class,
                () -> EntityFormat2.upgrade(fixture("entity-concept")));
        assertTrue(refused.getMessage() != null && !refused.getMessage().isBlank(), "the refusal says why");
    }

    @Test
    void onlyAFormat2RecordIsReadAsFormat2() {
        assertTrue(EntityFormat2.isFormat2(fixture("entity-concept")));
        assertTrue(!EntityFormat2.isFormat2(format3Fixture("entity-concept")));
        assertTrue(!EntityFormat2.isFormat2(null));
        assertThrows(IllegalArgumentException.class, () -> EntityFormat2.read(format3Fixture("entity-concept")));
    }

    // ---------- support ----------

    /** A graph field's expected shape: its vertices in index order and its successors. */
    private record Tree(Vertex[] vertices, Map<Integer, int[]> successors) {
    }

    private record Vertex(String uuid, long meaningNid, Map<Long, Object> properties) {
    }

    private static void assertUpgradesTo(String format2Hex, String format3Name) {
        byte[] upgraded = EntityFormat2.upgrade(HexFormat.of().parseHex(format2Hex));
        assertTrue(EntityRecordFormat3.isFormat3(upgraded));
        assertArrayEquals(format3Fixture(format3Name), upgraded, format3Name + " upgraded from format 2");
    }

    private static long[] stamps(Entity<? extends EntityVersion> entity) {
        return entity.versions().stream().mapToLong(EntityVersion::stampNid).toArray();
    }

    private static void assertDefinition(FieldDefinitionRecord definition, long meaningNid, long purposeNid,
                                         long dataTypeNid, int index) {
        assertEquals(meaningNid, definition.meaningNid(), "meaning of field " + index);
        assertEquals(purposeNid, definition.purposeNid(), "purpose of field " + index);
        assertEquals(dataTypeNid, definition.dataTypeNid(), "data type of field " + index);
        assertEquals(index, definition.indexInPattern());
    }

    /** Compares a value read with the one expected, by nid where it names a component. */
    private static void assertSameValue(String name, Object expected, Object actual) {
        switch (expected) {
            case byte[] bytes -> assertArrayEquals(bytes, (byte[]) actual, name);
            case Object[] elements -> {
                Object[] read = assertInstanceOf(Object[].class, actual, name);
                assertEquals(elements.length, read.length, name + " length");
                for (int i = 0; i < elements.length; i++) {
                    assertSameValue(name + "[" + i + "]", elements[i], read[i]);
                }
            }
            case EntityFacade facade -> {
                assertInstanceOf(facade.getClass(), actual, name);
                assertEquals(facade.nid(), ((EntityFacade) actual).nid(), name);
            }
            case LongIdList list -> assertArrayEquals(list.toArray(),
                    assertInstanceOf(LongIdList.class, actual, name).toArray(), name);
            case LongIdSet set -> {
                long[] members = set.toArray();
                Arrays.sort(members);
                assertArrayEquals(members, assertInstanceOf(LongIdSet.class, actual, name).toArray(), name);
            }
            case Tree tree -> assertGraph(name, tree, actual);
            default -> assertEquals(expected, actual, name);
        }
    }

    private static void assertGraph(String name, Tree expected, Object actual) {
        DiGraphAbstract<EntityVertex> graph = switch (actual) {
            case DiTreeEntity tree -> tree;
            case DiGraphEntity<?> digraph -> (DiGraphEntity<EntityVertex>) digraph;
            default -> throw new AssertionError(name + " is not a graph: " + actual);
        };
        assertEquals(expected.vertices().length, graph.vertexMap().size(), name + " vertices");
        for (int index = 0; index < expected.vertices().length; index++) {
            Vertex vertex = expected.vertices()[index];
            EntityVertex read = graph.vertexMap().get(index);
            String at = name + " vertex " + index;
            assertEquals(index, read.vertexIndex(), at);
            assertArrayEquals(new UUID[]{UUID.fromString(vertex.uuid())}, read.asUuidArray(), at);
            assertEquals(vertex.meaningNid(), ((EntityFacade) read.meaning()).nid(), at + " meaning");
            assertEquals(vertex.properties().size(), read.properties().size(), at + " properties");
            vertex.properties().forEach((key, value) ->
                    assertSameValue(at + " property " + key, value, read.properties().get(key)));
        }
        expected.successors().forEach((index, successors) ->
                assertArrayEquals(successors, graph.successorMap().get(index).toArray(), name + " successors of " + index));
        assertEquals(expected.successors().size(), graph.successorMap().size(), name + " vertices with successors");
    }

    private static byte[] fixture(String name) {
        return read(RESOURCE_DIRECTORY + "/" + name + ".hex");
    }

    private static byte[] format3Fixture(String name) {
        return read("entity-format-3/" + name + ".hex");
    }

    private static byte[] read(String resource) {
        try (InputStream in = EntityFormat2FixturesTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("No fixture " + resource);
            }
            return HexFormat.of().parseHex(new String(in.readAllBytes()).strip());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
