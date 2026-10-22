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

import dev.ikm.tinkar.common.id.LongIdSet;
import dev.ikm.tinkar.common.id.LongIds;
import dev.ikm.tinkar.common.id.Nid;
import dev.ikm.tinkar.common.id.impl.NidLayout;
import dev.ikm.tinkar.common.service.EntityRecordFormat3;
import dev.ikm.tinkar.component.FieldDataType;
import dev.ikm.tinkar.component.location.PlanarPoint;
import dev.ikm.tinkar.component.location.SpatialPoint;
import dev.ikm.tinkar.entity.ConceptRecord;
import dev.ikm.tinkar.entity.ConceptVersionRecord;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityCodec3;
import dev.ikm.tinkar.entity.EntityRecordFactory;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.FieldDefinitionRecord;
import dev.ikm.tinkar.entity.PatternRecord;
import dev.ikm.tinkar.entity.PatternVersionRecord;
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.SemanticVersionRecord;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.entity.StampVersionRecord;
import dev.ikm.tinkar.entity.graph.DiGraphEntity;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.fixtures.NewEphemeralKeyValueProvider;
import dev.ikm.tinkar.terms.EntityProxy;
import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.primitive.LongLists;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableLongObjectMap;
import org.eclipse.collections.impl.factory.primitive.IntIntMaps;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.eclipse.collections.impl.factory.primitive.LongObjectMaps;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The bytes of entity format 3, the format of a 64-bit store, pinned: one serialized entity of
 * each type and one field of each data type, compared in both directions with fixtures captured
 * when the format was made (IKE-Network/ike-issues#1313, after format 2 of design
 * {@code design-2026-10-07-64-bit-rocks-store}). As with format 1, each case is checked both
 * ways: what the codec writes for a fixed value equals the fixture, and what it reads from the
 * fixture writes the fixture again. The nids are 64-bit nids, from patterns below and above 128
 * and elements below and above 2<sup>24</sup>, so that the varints take one to four bytes; each
 * is of the kind its pattern half names, as format 3 requires.
 * <p>Two of format 1's three defects are fixed here: a {@link PlanarPoint} and a
 * {@link SpatialPoint} round-trip. A {@link Double} is still written as a float, as the
 * interchange schema has no double.
 * <p>To capture the fixtures again, which is only right before a deliberate format change, run
 * with {@code -Dentity.format3.capture=true}; the files are written under
 * {@code src/test/resources/entity-format-3}.
 */
@ExtendWith(NewEphemeralKeyValueProvider.class)
class EntityFormat3FixturesTest {

    private static final String RESOURCE_DIRECTORY = "entity-format-3";
    private static final boolean CAPTURE = Boolean.getBoolean("entity.format3.capture");
    private static final Path SOURCE_DIRECTORY = Path.of("src", "test", "resources", RESOURCE_DIRECTORY);

    private static final long CONCEPT_NID = Nid.compose64(2, 101);
    private static final long CONCEPT_NID_HIGH = Nid.compose64(2, 20_000_002);
    private static final long CONCEPT_NID_OTHER = Nid.compose64(2, 300);
    private static final long PATTERN_NID = Nid.compose64(1, 130);
    private static final long SEMANTIC_NID = Nid.compose64(4, 104);
    private static final long SEMANTIC_NID_HIGH = Nid.compose64(130, 20_000_105);
    private static final long STAMP_NID = Nid.compose64(3, 106);
    private static final long STAMP_NID_2 = Nid.compose64(3, 107);
    private static final long TIME = 1_767_225_600_777L;

    private static final UUID UUID_1 = UUID.fromString("0f6c6a6e-6b1d-4c0c-8b8e-3f1a2b3c4d01");
    private static final UUID UUID_2 = UUID.fromString("0f6c6a6e-6b1d-4c0c-8b8e-3f1a2b3c4d02");

    /** The layout the store opened with, restored after each test, whichever store the fixture opened. */
    private NidLayout opened;

    @BeforeEach
    void rememberTheLayout() {
        opened = NidLayout.active();
    }

    @AfterEach
    void restoreTheLayout() {
        NidLayout.activate(opened);
    }

    // ---------- fields ----------

    static Map<String, Supplier<Object>> fields() {
        Map<String, Supplier<Object>> fields = new LinkedHashMap<>();
        fields.put("field-boolean", () -> Boolean.TRUE);
        fields.put("field-float", () -> 3.25f);
        fields.put("field-byte-array", () -> new byte[]{0, 1, 2, (byte) 0xFE, (byte) 0xFF});
        fields.put("field-integer", () -> -123_456_789);
        fields.put("field-long", () -> 0x0123_4567_89AB_CDEFL);
        fields.put("field-decimal", () -> new BigDecimal("-12345.678900"));
        fields.put("field-instant", () -> Instant.ofEpochSecond(1_767_225_600L, 777_000_123));
        fields.put("field-string", () -> "Format 3 — ünïcode");
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
        fields.put("field-ditree", EntityFormat3FixturesTest::tree);
        fields.put("field-digraph", EntityFormat3FixturesTest::graph);
        return fields;
    }

    static Stream<String> roundTrippingFields() {
        return fields().keySet().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("roundTrippingFields")
    void fieldIsWrittenAsTheFixture(String name) {
        assertFixture(name, writeField(fields().get(name).get()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("roundTrippingFields")
    void fieldReadFromTheFixtureIsWrittenAsTheFixture(String name) {
        byte[] fixture = fixture(name);
        ByteBuf readBuf = ByteBuf.wrapForReading(fixture);
        Object value = readField(readBuf);
        assertEquals(0, readBuf.readRemaining(), name + " must be read to its last byte");
        assertArrayEquals(fixture, writeField(value), name + " must be written again as it was read");
    }

    @Test
    void theIdSetReadsBackAsItsMembersInOrder() {
        LongIdSet set = (LongIdSet) fields().get("field-component-id-set").get();
        ByteBuf readBuf = ByteBuf.wrapForReading(writeField(set));
        LongIdSet read = assertInstanceOf(LongIdSet.class, readField(readBuf));
        long[] expected = set.toArray();
        java.util.Arrays.sort(expected);
        assertArrayEquals(expected, read.toArray());
    }

    @Test
    void doubleIsWrittenAsAFloat() {
        byte[] written = writeField(0.1d);
        assertFixture("field-double", written);
        assertEquals(0.1f, readField(ByteBuf.wrapForReading(written)), "narrowed to a float, as the schema has no double");
    }

    @Test
    void theWriterRefusesANidThatIsNot64Bit() {
        assertThrows(IllegalArgumentException.class, () -> writeField(EntityProxy.Concept.make(12_345)));
        assertThrows(IllegalArgumentException.class, () -> writeField(LongIds.set.of(CONCEPT_NID, -7L)));
    }

    // ---------- entities ----------

    static Map<String, Supplier<Entity<? extends EntityVersion>>> entities() {
        Map<String, Supplier<Entity<? extends EntityVersion>>> entities = new LinkedHashMap<>();
        entities.put("entity-concept", EntityFormat3FixturesTest::concept);
        entities.put("entity-pattern", EntityFormat3FixturesTest::pattern);
        entities.put("entity-semantic", EntityFormat3FixturesTest::semantic);
        entities.put("entity-stamp", EntityFormat3FixturesTest::stamp);
        return entities;
    }

    static Stream<String> entityNames() {
        return entities().keySet().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entityNames")
    void entityIsWrittenAsTheFixture(String name) {
        assertFixture(name, EntityCodec3.write(entities().get(name).get()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entityNames")
    void entityReadFromTheFixtureIsWrittenAsTheFixture(String name) {
        byte[] fixture = fixture(name);
        assertTrue(EntityRecordFormat3.isFormat3(fixture));
        // The facade dispatches a read on the record's first byte, whatever layout is active.
        Entity<? extends EntityVersion> entity = EntityRecordFactory.make(fixture);
        assertNotNull(entity);
        assertEquals(entities().get(name).get().nid(), entity.nid());
        assertArrayEquals(fixture, EntityCodec3.write(entity), name + " must be written again as it was read");
    }

    @Test
    void theFacadeWritesFormat3UnderTheSixtyFourBitLayout() {
        NidLayout.activate(NidLayout.SIXTY_FOUR_BIT);
        byte[] written = EntityRecordFactory.getBytes(semantic());
        assertTrue(EntityRecordFormat3.isFormat3(written));
        assertArrayEquals(fixture("entity-semantic"), written);
        SemanticRecord read = EntityRecordFactory.make(written);
        assertEquals(SEMANTIC_NID_HIGH, read.nid());
        assertEquals(PATTERN_NID, read.patternNid());
        assertEquals(CONCEPT_NID_HIGH, read.referencedComponentNid());
        assertEquals(1, read.versions().size());
        assertEquals("description text", read.versions().get(0).fieldValues().get(0));
    }

    @Test
    void format3IsSmallerThanFormat1ForTheSameEntity() {
        // The same semantic with 8-bit nids, as format 1 holds it, against format 3's varints.
        int format1 = EntityFormat1FixturesTest.semanticBytes().length;
        int format3 = EntityCodec3.write(semantic()).length;
        assertTrue(format3 < format1, "format 3 " + format3 + " bytes, format 1 " + format1);
    }

    @Test
    void theWriterRefusesAnEntityWhoseNidGivesAnotherKind() {
        RecordListBuilder<ConceptVersionRecord> versions = RecordListBuilder.make();
        ConceptRecord misfiled = new ConceptRecord(UUID_1.getMostSignificantBits(), UUID_1.getLeastSignificantBits(),
                LongLists.immutable.empty(), SEMANTIC_NID, versions);
        versions.add(new ConceptVersionRecord(misfiled, STAMP_NID));
        versions.build();
        assertThrows(IllegalArgumentException.class, () -> EntityCodec3.write(misfiled), "a concept under a semantic's nid");
        assertThrows(IllegalArgumentException.class, () -> writeField(EntityProxy.Concept.make(SEMANTIC_NID)),
                "a concept field naming a semantic");
    }

    /** A concept with two UUIDs and two versions. */
    private static ConceptRecord concept() {
        RecordListBuilder<ConceptVersionRecord> versions = RecordListBuilder.make();
        ConceptRecord concept = new ConceptRecord(UUID_1.getMostSignificantBits(), UUID_1.getLeastSignificantBits(),
                LongLists.immutable.of(UUID_2.getMostSignificantBits(), UUID_2.getLeastSignificantBits()),
                CONCEPT_NID_HIGH, versions);
        versions.add(new ConceptVersionRecord(concept, STAMP_NID));
        versions.add(new ConceptVersionRecord(concept, STAMP_NID_2));
        versions.build();
        return concept;
    }

    /** A pattern with one version and two field definitions. */
    private static PatternRecord pattern() {
        RecordListBuilder<PatternVersionRecord> versions = RecordListBuilder.make();
        PatternRecord pattern = new PatternRecord(UUID_1.getMostSignificantBits(), UUID_1.getLeastSignificantBits(),
                LongLists.immutable.empty(), PATTERN_NID, versions);
        versions.add(new PatternVersionRecord(pattern, STAMP_NID, CONCEPT_NID, CONCEPT_NID_HIGH, Lists.immutable.of(
                new FieldDefinitionRecord(CONCEPT_NID, CONCEPT_NID_HIGH, CONCEPT_NID, STAMP_NID, PATTERN_NID, 0),
                new FieldDefinitionRecord(CONCEPT_NID_HIGH, CONCEPT_NID, CONCEPT_NID_HIGH, STAMP_NID, PATTERN_NID, 1))));
        versions.build();
        return pattern;
    }

    /** A semantic with one version whose fields reference components of small and large sequences. */
    private static SemanticRecord semantic() {
        RecordListBuilder<SemanticVersionRecord> versions = RecordListBuilder.make();
        SemanticRecord semantic = new SemanticRecord(UUID_2.getMostSignificantBits(), UUID_2.getLeastSignificantBits(),
                LongLists.immutable.empty(), SEMANTIC_NID_HIGH, PATTERN_NID, CONCEPT_NID_HIGH, versions);
        versions.add(new SemanticVersionRecord(semantic, STAMP_NID, Lists.immutable.of(
                "description text", EntityProxy.Concept.make(CONCEPT_NID), EntityProxy.Concept.make(CONCEPT_NID_HIGH),
                LongIds.set.of(CONCEPT_NID, CONCEPT_NID_HIGH), 7)));
        versions.build();
        return semantic;
    }

    /** A stamp with one version. */
    private static StampRecord stamp() {
        RecordListBuilder<StampVersionRecord> versions = RecordListBuilder.make();
        StampRecord stamp = new StampRecord(UUID_1.getMostSignificantBits(), UUID_1.getLeastSignificantBits(),
                LongLists.immutable.empty(), STAMP_NID, versions);
        versions.add(new StampVersionRecord(stamp, CONCEPT_NID, TIME, CONCEPT_NID_HIGH, CONCEPT_NID, CONCEPT_NID_HIGH));
        versions.build();
        return stamp;
    }

    /** A three-vertex tree whose root carries properties keyed by small and large nids. */
    private static DiTreeEntity tree() {
        EntityVertex root = EntityVertex.make(UUID.fromString("55555555-5555-5555-5555-555555555501"), CONCEPT_NID);
        root.setVertexIndex(0);
        MutableLongObjectMap<Object> properties = LongObjectMaps.mutable.empty();
        properties.put(CONCEPT_NID, EntityProxy.Concept.make(CONCEPT_NID_HIGH));
        properties.put(CONCEPT_NID_HIGH, 17);
        properties.put(CONCEPT_NID_OTHER, "property");
        root.setProperties(properties);
        EntityVertex left = EntityVertex.make(UUID.fromString("55555555-5555-5555-5555-555555555502"), CONCEPT_NID_HIGH);
        left.setVertexIndex(1);
        EntityVertex right = EntityVertex.make(UUID.fromString("55555555-5555-5555-5555-555555555503"), CONCEPT_NID);
        right.setVertexIndex(2);
        MutableIntObjectMap<ImmutableIntList> successors = IntObjectMaps.mutable.empty();
        successors.put(0, IntLists.immutable.of(1, 2));
        return new DiTreeEntity(root, Lists.immutable.of(root, left, right), successors.toImmutable(),
                IntIntMaps.mutable.empty().withKeyValue(1, 0).withKeyValue(2, 0).toImmutable());
    }

    /** A two-vertex graph with one root. */
    private static DiGraphEntity<EntityVertex> graph() {
        EntityVertex root = EntityVertex.make(UUID.fromString("66666666-6666-6666-6666-666666666601"), CONCEPT_NID_HIGH);
        root.setVertexIndex(0);
        EntityVertex leaf = EntityVertex.make(UUID.fromString("66666666-6666-6666-6666-666666666602"), CONCEPT_NID);
        leaf.setVertexIndex(1);
        MutableIntObjectMap<ImmutableIntList> successors = IntObjectMaps.mutable.empty();
        successors.put(0, IntLists.immutable.of(1));
        MutableIntObjectMap<ImmutableIntList> predecessors = IntObjectMaps.mutable.empty();
        predecessors.put(1, IntLists.immutable.of(0));
        return new DiGraphEntity<>(Lists.immutable.of(root), Lists.immutable.of(root, leaf),
                successors.toImmutable(), predecessors.toImmutable());
    }

    // ---------- support ----------

    private static byte[] writeField(Object value) {
        ByteBuf writeBuf = ByteBufPool.allocate(4096);
        EntityCodec3.writeField(writeBuf, value);
        return writeBuf.asArray();
    }

    private static Object readField(ByteBuf readBuf) {
        FieldDataType dataType = FieldDataType.fromToken(readBuf.readByte());
        return EntityRecordFactory.readFieldData(readBuf, dataType, EntityCodec3.FORMAT);
    }

    private static void assertFixture(String name, byte[] written) {
        if (CAPTURE) {
            capture(name, written);
            return;
        }
        assertArrayEquals(fixture(name), written, name + " is no longer written as entity format 3 was");
    }

    private static byte[] fixture(String name) {
        String resource = RESOURCE_DIRECTORY + "/" + name + ".hex";
        try (InputStream in = CAPTURE ? Files.newInputStream(SOURCE_DIRECTORY.resolve(name + ".hex"))
                : EntityFormat3FixturesTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("No fixture " + resource);
            }
            return HexFormat.of().parseHex(new String(in.readAllBytes()).strip());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void capture(String name, byte[] written) {
        try {
            Files.createDirectories(SOURCE_DIRECTORY);
            Files.writeString(SOURCE_DIRECTORY.resolve(name + ".hex"), HexFormat.of().formatHex(written) + "\n");
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
