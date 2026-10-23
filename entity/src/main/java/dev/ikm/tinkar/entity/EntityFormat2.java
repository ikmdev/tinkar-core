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
package dev.ikm.tinkar.entity;

import dev.ikm.tinkar.common.id.LongIds;
import dev.ikm.tinkar.common.id.Nid;
import dev.ikm.tinkar.component.FieldDataType;
import dev.ikm.tinkar.component.location.PlanarPoint;
import dev.ikm.tinkar.component.location.SpatialPoint;
import dev.ikm.tinkar.entity.graph.DiGraphEntity;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.terms.EntityProxy;
import io.activej.bytebuf.ByteBuf;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.primitive.IntIntMaps;
import org.eclipse.collections.api.factory.primitive.IntLists;
import org.eclipse.collections.api.factory.primitive.IntObjectMaps;
import org.eclipse.collections.api.factory.primitive.LongObjectMaps;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.map.primitive.ImmutableIntObjectMap;
import org.eclipse.collections.api.map.primitive.ImmutableLongObjectMap;
import org.eclipse.collections.api.map.primitive.MutableIntIntMap;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.api.map.primitive.MutableLongObjectMap;
import org.eclipse.collections.impl.factory.primitive.LongLists;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Entity format 2, the first 64-bit record format, read so a store written in it can be
 * upgraded to {@linkplain EntityCodec3 format 3} (IKE-Network/ike-issues#1313). Nothing writes
 * format 2, and nothing reads it but an upgrade: {@link #upgrade} decodes a record and encodes
 * the entity again in format 3.
 *
 * <p>Format 3 kept format 2's 64-bit nids and dropped only what a nid already says, so an
 * upgrade changes no nid, identity or value: every version is kept, a canceled one included,
 * and a field reads as the value format 3 writes. What format 2 wrote, after its format byte:
 * the chronology's length, its type token, its nid, its UUIDs (the first, then a count of
 * further UUID longs and the longs), a semantic's referenced component and pattern; then the
 * count of versions and each version's length, type token and stamp, followed by what the
 * version holds. A nid is its two halves as varints; counts and lengths are varints; an id set
 * groups its members by pattern, each element the gap from the one before; a graph is format
 * 1's layout with varint counts, its vertices' nids and values in format 2.
 */
public final class EntityFormat2 {

    /** The format byte, the first byte of a format 2 record. */
    public static final byte FORMAT = 2;

    private EntityFormat2() {
    }

    /** Whether a record is in format 2. */
    public static boolean isFormat2(byte[] record) {
        return record != null && record.length > 0 && record[0] == FORMAT;
    }

    /**
     * The record of the entity a format 2 record holds, in format 3.
     *
     * @param record a format 2 record
     * @return the same entity's format 3 record
     * @throws IllegalArgumentException if format 3 refuses the entity, as it refuses a reference
     *                                  whose nid names another kind than its field's
     */
    public static byte[] upgrade(byte[] record) {
        return EntityCodec3.write(read(record));
    }

    /**
     * The entity a format 2 record holds, every version kept.
     *
     * @param record the record
     * @param <T>    the entity type
     * @param <V>    the version type
     * @return the entity
     */
    @SuppressWarnings("unchecked")
    public static <T extends Entity<V>, V extends EntityVersion> T read(byte[] record) {
        if (!isFormat2(record)) {
            throw new IllegalArgumentException("Not a format 2 entity record: "
                    + (record == null || record.length == 0 ? "empty" : "first byte " + record[0]));
        }
        ByteBuf buf = ByteBuf.wrapForReading(record);
        buf.readByte();
        int chronologyLength = buf.readVarInt();
        int chronologyEnd = buf.head() + chronologyLength;
        FieldDataType type = FieldDataType.fromToken(buf.readByte());
        long nid = readNid(buf);
        long msb = buf.readLong();
        long lsb = buf.readLong();
        int additionalCount = buf.readVarInt();
        long[] additional = new long[additionalCount];
        for (int i = 0; i < additionalCount; i++) {
            additional[i] = buf.readLong();
        }
        ImmutableLongList additionalLongs = LongLists.immutable.of(additional);
        return switch (type) {
            case CONCEPT_CHRONOLOGY -> {
                RecordListBuilder<ConceptVersionRecord> versions = RecordListBuilder.make();
                ConceptRecord concept = new ConceptRecord(msb, lsb, additionalLongs, nid, versions);
                readVersions(buf, chronologyEnd, concept, versions);
                yield (T) concept;
            }
            case SEMANTIC_CHRONOLOGY -> {
                long referencedComponentNid = readNid(buf);
                long patternNid = readNid(buf);
                RecordListBuilder<SemanticVersionRecord> versions = RecordListBuilder.make();
                SemanticRecord semantic = new SemanticRecord(msb, lsb, additionalLongs, nid, patternNid,
                        referencedComponentNid, versions);
                readVersions(buf, chronologyEnd, semantic, versions);
                yield (T) semantic;
            }
            case PATTERN_CHRONOLOGY -> {
                RecordListBuilder<PatternVersionRecord> versions = RecordListBuilder.make();
                PatternRecord pattern = new PatternRecord(msb, lsb, additionalLongs, nid, versions);
                readVersions(buf, chronologyEnd, pattern, versions);
                yield (T) pattern;
            }
            case STAMP -> {
                RecordListBuilder<StampVersionRecord> versions = RecordListBuilder.make();
                StampRecord stamp = new StampRecord(msb, lsb, additionalLongs, nid, versions);
                readVersions(buf, chronologyEnd, stamp, versions);
                yield (T) stamp;
            }
            default -> throw new IllegalStateException("Unexpected chronology type: " + type);
        };
    }

    @SuppressWarnings("unchecked")
    private static <V extends EntityVersion> void readVersions(ByteBuf buf, int chronologyEnd,
                                                               Entity<? extends EntityVersion> entity,
                                                               RecordListBuilder<V> versions) {
        buf.head(chronologyEnd);
        int versionCount = buf.readVarInt();
        for (int i = 0; i < versionCount; i++) {
            int length = buf.readVarInt();
            int versionEnd = buf.head() + length;
            versions.add((V) version(buf, entity));
            buf.head(versionEnd);
        }
        versions.build();
    }

    private static EntityVersion version(ByteBuf buf, Entity<? extends EntityVersion> entity) {
        byte token = buf.readByte();
        if (entity.versionDataType().token != token) {
            throw new IllegalStateException("Wrong version token " + token + " (" + FieldDataType.fromToken(token)
                    + ") for " + entity.versionDataType() + " of " + EntityText.diagnostic(entity));
        }
        long stampNid = readNid(buf);
        return switch (entity) {
            case ConceptRecord concept -> new ConceptVersionRecord(concept, stampNid);
            case SemanticRecord semantic -> {
                int fieldCount = buf.readVarInt();
                RecordListBuilder<Object> fields = RecordListBuilder.make();
                for (int i = 0; i < fieldCount; i++) {
                    fields.add(readField(buf, FieldDataType.fromToken(buf.readByte())));
                }
                fields.build();
                yield new SemanticVersionRecord(semantic, stampNid, fields);
            }
            case PatternRecord pattern -> {
                long purposeNid = readNid(buf);
                long meaningNid = readNid(buf);
                int fieldCount = buf.readVarInt();
                MutableList<FieldDefinitionRecord> definitions = Lists.mutable.ofInitialCapacity(fieldCount);
                for (int index = 0; index < fieldCount; index++) {
                    definitions.add(new FieldDefinitionRecord(readNid(buf), readNid(buf), readNid(buf),
                            stampNid, pattern.nid(), index));
                }
                yield new PatternVersionRecord(pattern, stampNid, purposeNid, meaningNid, definitions.toImmutable());
            }
            case StampRecord stamp -> {
                long stateNid = readNid(buf);
                long time = buf.readLong();
                long authorNid = readNid(buf);
                long moduleNid = readNid(buf);
                long pathNid = readNid(buf);
                yield new StampVersionRecord(stamp, stateNid, time, authorNid, moduleNid, pathNid);
            }
            default -> throw new IllegalStateException("Unexpected entity: " + EntityText.diagnostic(entity));
        };
    }

    /**
     * Reads a format 2 field value whose type token has been read.
     *
     * @param buf  the buffer
     * @param type the field's type
     * @return the value
     */
    public static Object readField(ByteBuf buf, FieldDataType type) {
        return switch (type) {
            case BOOLEAN -> buf.readBoolean();
            case FLOAT -> buf.readFloat();
            case BYTE_ARRAY -> readBytes(buf);
            case INTEGER -> buf.readInt();
            case LONG -> buf.readLong();
            case DECIMAL -> new BigDecimal(readString(buf));
            case INSTANT -> Instant.ofEpochSecond(buf.readLong(), buf.readInt());
            case STRING -> readString(buf);
            case CONCEPT -> EntityProxy.Concept.make(readNid(buf));
            case SEMANTIC -> EntityProxy.Semantic.make(readNid(buf));
            case PATTERN -> EntityProxy.Pattern.make(readNid(buf));
            case IDENTIFIED_THING -> EntityProxy.make(readNid(buf));
            case DITREE -> readTree(buf);
            case DIGRAPH -> readGraph(buf);
            case VERTEX -> readVertex(buf);
            case OBJECT_ARRAY -> {
                int count = buf.readVarInt();
                Object[] elements = new Object[count];
                for (int i = 0; i < count; i++) {
                    elements[i] = readField(buf, FieldDataType.fromToken(buf.readByte()));
                }
                yield elements;
            }
            case PLANAR_POINT -> new PlanarPoint(buf.readFloat(), buf.readFloat());
            case SPATIAL_POINT -> new SpatialPoint(buf.readFloat(), buf.readFloat(), buf.readFloat());
            case COMPONENT_ID_LIST -> {
                int size = buf.readVarInt();
                long[] nids = new long[size];
                for (int i = 0; i < size; i++) {
                    nids[i] = readNid(buf);
                }
                yield LongIds.list.of(nids);
            }
            case COMPONENT_ID_SET -> LongIds.set.ofAlreadySorted(readIdSet(buf));
            default -> throw new UnsupportedOperationException("Can't read a format 2 field of type " + type);
        };
    }

    // ---------------------------------------------------------------- graphs

    private static DiTreeEntity readTree(ByteBuf buf) {
        ImmutableList<EntityVertex> vertexMap = readVertices(buf);
        ImmutableIntObjectMap<ImmutableIntList> successorMap = readAdjacency(buf);
        int predecessorMapSize = buf.readVarInt();
        MutableIntIntMap predecessorMap = IntIntMaps.mutable.ofInitialCapacity(predecessorMapSize);
        for (int i = 0; i < predecessorMapSize; i++) {
            predecessorMap.put(buf.readInt(), buf.readInt());
        }
        int rootVertexIndex = buf.readInt();
        return new DiTreeEntity(vertexMap.get(rootVertexIndex), vertexMap, successorMap, predecessorMap.toImmutable());
    }

    private static DiGraphEntity<EntityVertex> readGraph(ByteBuf buf) {
        ImmutableList<EntityVertex> vertexMap = readVertices(buf);
        ImmutableIntObjectMap<ImmutableIntList> successorMap = readAdjacency(buf);
        ImmutableIntObjectMap<ImmutableIntList> predecessorMap = readAdjacency(buf);
        int rootCount = buf.readVarInt();
        MutableList<EntityVertex> roots = Lists.mutable.ofInitialCapacity(rootCount);
        for (int i = 0; i < rootCount; i++) {
            roots.add(vertexMap.get(buf.readInt()));
        }
        return new DiGraphEntity<>(roots.toImmutable(), vertexMap, successorMap, predecessorMap);
    }

    private static ImmutableList<EntityVertex> readVertices(ByteBuf buf) {
        int count = buf.readVarInt();
        MutableList<EntityVertex> vertices = Lists.mutable.ofInitialCapacity(count);
        for (int i = 0; i < count; i++) {
            vertices.add(readVertex(buf));
        }
        return vertices.toImmutable();
    }

    /** A vertex-to-vertices map: varint counts, vertex indexes as ints. */
    private static ImmutableIntObjectMap<ImmutableIntList> readAdjacency(ByteBuf buf) {
        int size = buf.readVarInt();
        MutableIntObjectMap<ImmutableIntList> map = IntObjectMaps.mutable.ofInitialCapacity(size);
        for (int i = 0; i < size; i++) {
            int vertexIndex = buf.readInt();
            int listSize = buf.readVarInt();
            MutableIntList list = IntLists.mutable.withInitialCapacity(listSize);
            for (int j = 0; j < listSize; j++) {
                list.add(buf.readInt());
            }
            map.put(vertexIndex, list.toImmutable());
        }
        return map.toImmutable();
    }

    private static EntityVertex readVertex(ByteBuf buf) {
        long msb = buf.readLong();
        long lsb = buf.readLong();
        int vertexIndex = buf.readInt();
        long meaningNid = readNid(buf);
        int propertyCount = buf.readVarInt();
        ImmutableLongObjectMap<Object> properties;
        if (propertyCount > 0) {
            MutableLongObjectMap<Object> mutable = LongObjectMaps.mutable.ofInitialCapacity(propertyCount);
            for (int i = 0; i < propertyCount; i++) {
                long keyNid = readNid(buf);
                mutable.put(keyNid, readField(buf, FieldDataType.fromToken(buf.readByte())));
            }
            properties = mutable.toImmutable();
        } else {
            properties = LongObjectMaps.immutable.empty();
        }
        return EntityVertex.of(msb, lsb, vertexIndex, meaningNid, properties);
    }

    // ---------------------------------------------------------------- primitives

    /** A nid as format 2 wrote it: its pattern half, then its element half, each a varint. */
    private static long readNid(ByteBuf buf) {
        int patternSequence = buf.readVarInt();
        int elementSequence = buf.readVarInt();
        return Nid.compose64(patternSequence, elementSequence);
    }

    /** The members of an id set, grouped by pattern, in ascending order. */
    private static long[] readIdSet(ByteBuf buf) {
        int patterns = buf.readVarInt();
        List<long[]> groups = new ArrayList<>(patterns);
        int total = 0;
        for (int p = 0; p < patterns; p++) {
            int pattern = buf.readVarInt();
            long[] group = new long[buf.readVarInt()];
            int element = 0;
            for (int i = 0; i < group.length; i++) {
                element += buf.readVarInt();
                group[i] = Nid.compose64(pattern, element);
            }
            groups.add(group);
            total += group.length;
        }
        long[] members = new long[total];
        int at = 0;
        for (long[] group : groups) {
            System.arraycopy(group, 0, members, at, group.length);
            at += group.length;
        }
        return members;
    }

    private static String readString(ByteBuf buf) {
        int length = buf.readVarInt();
        String text = new String(buf.array(), buf.head(), length, UTF_8);
        buf.moveHead(length);
        return text;
    }

    private static byte[] readBytes(ByteBuf buf) {
        byte[] bytes = new byte[buf.readVarInt()];
        buf.read(bytes);
        return bytes;
    }
}
