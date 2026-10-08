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

import org.eclipse.collections.impl.factory.primitive.LongObjectMaps;
import org.eclipse.collections.api.map.primitive.MutableLongObjectMap;
import dev.ikm.tinkar.common.id.LongIds;
import dev.ikm.tinkar.common.id.impl.NidCodec8;
import dev.ikm.tinkar.component.FieldDataType;
import dev.ikm.tinkar.component.location.PlanarPoint;
import dev.ikm.tinkar.component.location.SpatialPoint;
import dev.ikm.tinkar.entity.ConceptRecord;
import dev.ikm.tinkar.entity.ConceptVersionRecord;
import dev.ikm.tinkar.entity.Entity;
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
import org.eclipse.collections.impl.factory.primitive.IntIntMaps;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
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

/**
 * The bytes of entity format 1, pinned: one serialized entity of each type and one field of each
 * data type, compared in both directions with fixtures captured before the move to {@code long}
 * nids (design {@code design-2026-09-30-64-bit-nids}, step "Specification and fixtures").
 * <p>Format 1 must not change while any 6-bit, 8-bit or sequential store exists, and the codec
 * work of the later steps could change it without another test failing. So each case is checked
 * both ways: what the codec writes for a fixed value equals the fixture, and what it reads from the
 * fixture writes the fixture again. The id set holds members on both sides of the packed sign bit,
 * which fixes the order in which a set is written.
 * <p>Three field types do not round-trip today, and the fixtures record them as they are:
 * a {@code Double} is written as a {@code float} and read as a {@code Float}; a {@link PlanarPoint}
 * writes its token twice and is read as two ints, leaving one byte unread; a
 * {@link SpatialPoint} is written as an int and two floats and read as three ints.
 * <p>To capture the fixtures again, which is only right before a deliberate format change, run
 * with {@code -Dentity.format1.capture=true}; the files are written under
 * {@code src/test/resources/entity-format-1}.
 */
@ExtendWith(NewEphemeralKeyValueProvider.class)
class EntityFormat1FixturesTest {

    private static final String RESOURCE_DIRECTORY = "entity-format-1";
    private static final boolean CAPTURE = Boolean.getBoolean("entity.format1.capture");
    private static final Path SOURCE_DIRECTORY = Path.of("src", "test", "resources", RESOURCE_DIRECTORY);

    // Nids as an 8-bit store packs them: patterns below 128 give positive nids, 128 and above negative.
    private static final long CONCEPT_NID = NidCodec8.encode(1, 101);
    private static final int CONCEPT_NID_HIGH = NidCodec8.encode(200, 102);
    private static final long PATTERN_NID = NidCodec8.encode(255, 3);
    private static final long SEMANTIC_NID = NidCodec8.encode(4, 104);
    private static final int SEMANTIC_NID_HIGH = NidCodec8.encode(130, 105);
    private static final long STAMP_NID = NidCodec8.encode(2, 106);
    private static final int STAMP_NID_2 = NidCodec8.encode(2, 107);
    private static final long TIME = 1_767_225_600_777L;

    private static final UUID UUID_1 = UUID.fromString("0f6c6a6e-6b1d-4c0c-8b8e-3f1a2b3c4d01");
    private static final UUID UUID_2 = UUID.fromString("0f6c6a6e-6b1d-4c0c-8b8e-3f1a2b3c4d02");

    // ---------- fields ----------

    /**
     * One value of each field data type, by fixture name.
     *
     * @return the values, in a stable order
     */
    static Map<String, Supplier<Object>> fields() {
        Map<String, Supplier<Object>> fields = new LinkedHashMap<>();
        fields.put("field-boolean", () -> Boolean.TRUE);
        fields.put("field-float", () -> 3.25f);
        fields.put("field-byte-array", () -> new byte[]{0, 1, 2, (byte) 0xFE, (byte) 0xFF});
        fields.put("field-integer", () -> -123_456_789);
        fields.put("field-long", () -> 0x0123_4567_89AB_CDEFL);
        fields.put("field-decimal", () -> new BigDecimal("-12345.678900"));
        fields.put("field-instant", () -> Instant.ofEpochSecond(1_767_225_600L, 777_000_123));
        fields.put("field-string", () -> "Format 1 — ünïcode");
        fields.put("field-concept", () -> EntityProxy.Concept.make(CONCEPT_NID_HIGH));
        fields.put("field-semantic", () -> EntityProxy.Semantic.make(SEMANTIC_NID_HIGH));
        fields.put("field-pattern", () -> EntityProxy.Pattern.make(PATTERN_NID));
        fields.put("field-identified-thing", () -> EntityProxy.make(CONCEPT_NID));
        fields.put("field-component-id-list", () -> LongIds.list.of(CONCEPT_NID_HIGH, CONCEPT_NID, SEMANTIC_NID_HIGH, CONCEPT_NID));
        fields.put("field-component-id-set", () -> LongIds.set.of(CONCEPT_NID, CONCEPT_NID_HIGH, SEMANTIC_NID, SEMANTIC_NID_HIGH, PATTERN_NID));
        fields.put("field-object-array", () -> new Object[]{"element", 42, EntityProxy.Concept.make(CONCEPT_NID_HIGH)});
        fields.put("field-ditree", EntityFormat1FixturesTest::tree);
        fields.put("field-digraph", EntityFormat1FixturesTest::graph);
        return fields;
    }

    static Stream<String> roundTrippingFields() {
        return fields().keySet().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("roundTrippingFields")
    void fieldIsWrittenAsTheFixture(String name) {
        byte[] written = writeField(fields().get(name).get());
        assertFixture(name, written);
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
    void doubleIsWrittenAsAFloatAndReadAsAFloat() {
        byte[] written = writeField(0.1d);
        assertFixture("field-double", written);
        ByteBuf readBuf = ByteBuf.wrapForReading(fixture("field-double"));
        Object value = readField(readBuf);
        assertEquals(0.1f, value, "read back as the float it was narrowed to");
        assertEquals(0, readBuf.readRemaining());
    }

    @Test
    void planarPointWritesItsTokenTwiceAndIsReadAsInts() {
        byte[] written = writeField(new PlanarPoint(1.5f, -2.5f));
        assertFixture("field-planar-point", written);
        ByteBuf readBuf = ByteBuf.wrapForReading(fixture("field-planar-point"));
        Object value = readField(readBuf);
        PlanarPoint point = assertInstanceOf(PlanarPoint.class, value);
        // The second token and three bytes of x make the first int; the rest of x and three bytes of y the second.
        assertEquals(new PlanarPoint(readIntAt(fixture("field-planar-point"), 1), readIntAt(fixture("field-planar-point"), 5)), point);
        assertEquals(1, readBuf.readRemaining(), "the last byte of y is left unread");
    }

    @Test
    void spatialPointIsWrittenAsAnIntAndTwoFloatsAndReadAsInts() {
        byte[] written = writeField(new SpatialPoint(7.75f, 1.5f, -2.5f));
        assertFixture("field-spatial-point", written);
        byte[] fixture = fixture("field-spatial-point");
        ByteBuf readBuf = ByteBuf.wrapForReading(fixture);
        SpatialPoint point = assertInstanceOf(SpatialPoint.class, readField(readBuf));
        assertEquals(new SpatialPoint(7, Float.floatToIntBits(1.5f), Float.floatToIntBits(-2.5f)), point,
                "x truncated to an int; y and z read as the int bits of their floats");
        assertEquals(new SpatialPoint(readIntAt(fixture, 1), readIntAt(fixture, 5), readIntAt(fixture, 9)), point);
        assertEquals(0, readBuf.readRemaining());
    }

    // ---------- entities ----------

    static Map<String, Supplier<Entity<? extends EntityVersion>>> entities() {
        Map<String, Supplier<Entity<? extends EntityVersion>>> entities = new LinkedHashMap<>();
        entities.put("entity-concept", EntityFormat1FixturesTest::concept);
        entities.put("entity-pattern", EntityFormat1FixturesTest::pattern);
        entities.put("entity-semantic", EntityFormat1FixturesTest::semantic);
        entities.put("entity-stamp", EntityFormat1FixturesTest::stamp);
        return entities;
    }

    static Stream<String> entityNames() {
        return entities().keySet().stream();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entityNames")
    void entityIsWrittenAsTheFixture(String name) {
        assertFixture(name, EntityRecordFactory.getBytes(entities().get(name).get()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("entityNames")
    void entityReadFromTheFixtureIsWrittenAsTheFixture(String name) {
        byte[] fixture = fixture(name);
        Entity<? extends EntityVersion> entity = EntityRecordFactory.make(fixture);
        assertNotNull(entity);
        assertArrayEquals(fixture, EntityRecordFactory.getBytes(entity), name + " must be written again as it was read");
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

    /** The format 1 bytes of the semantic fixture, for the size comparison of {@link EntityFormat2FixturesTest}. */
    static byte[] semanticBytes() {
        return EntityRecordFactory.getBytes(semantic());
    }

    /** A semantic with one version whose fields reference components on both sides of the sign bit. */
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

    /** A three-vertex tree whose root carries properties keyed on both sides of the sign bit. */
    private static DiTreeEntity tree() {
        EntityVertex root = EntityVertex.make(UUID.fromString("55555555-5555-5555-5555-555555555501"), CONCEPT_NID);
        root.setVertexIndex(0);
        MutableLongObjectMap<Object> properties = LongObjectMaps.mutable.empty();
        properties.put(CONCEPT_NID, EntityProxy.Concept.make(CONCEPT_NID_HIGH));
        properties.put(CONCEPT_NID_HIGH, 17);
        properties.put(SEMANTIC_NID_HIGH, "property");
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
        EntityRecordFactory.writeField(writeBuf, value);
        return writeBuf.asArray();
    }

    private static Object readField(ByteBuf readBuf) {
        FieldDataType dataType = FieldDataType.fromToken(readBuf.readByte());
        return EntityRecordFactory.readFieldData(readBuf, dataType, EntityRecordFactory.ENTITY_FORMAT_VERSION);
    }

    private static int readIntAt(byte[] bytes, int offset) {
        return ByteBuffer.wrap(bytes, offset, 4).getInt();
    }

    private static void assertFixture(String name, byte[] written) {
        if (CAPTURE) {
            capture(name, written);
            return;
        }
        assertArrayEquals(fixture(name), written, name + " is no longer written as entity format 1 was");
    }

    private static byte[] fixture(String name) {
        String resource = RESOURCE_DIRECTORY + "/" + name + ".hex";
        // While capturing, read what was just written to the sources, not the copy on the classpath.
        try (InputStream in = CAPTURE ? Files.newInputStream(SOURCE_DIRECTORY.resolve(name + ".hex"))
                : EntityFormat1FixturesTest.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("No fixture " + resource);
            }
            StringBuilder hex = new StringBuilder();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (!line.startsWith("#")) {
                    hex.append(line.strip());
                }
            }
            return HexFormat.of().parseHex(hex);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void capture(String name, byte[] bytes) {
        StringBuilder text = new StringBuilder("# Entity format 1: ").append(name).append(", ")
                .append(bytes.length).append(" bytes. Captured by EntityFormat1FixturesTest; do not edit.\n");
        String hex = HexFormat.of().formatHex(bytes);
        for (int start = 0; start < hex.length(); start += 64) {
            text.append(hex, start, Math.min(hex.length(), start + 64)).append('\n');
        }
        try {
            Files.createDirectories(SOURCE_DIRECTORY);
            Files.writeString(SOURCE_DIRECTORY.resolve(name + ".hex"), text);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
