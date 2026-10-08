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

import dev.ikm.tinkar.common.id.LongIdList;
import dev.ikm.tinkar.common.id.LongIdSet;
import dev.ikm.tinkar.common.id.LongIds;
import dev.ikm.tinkar.common.id.Nid;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIdList;
import dev.ikm.tinkar.common.id.PublicIdSet;
import dev.ikm.tinkar.common.id.impl.NidLayout;
import dev.ikm.tinkar.common.service.EntityRecordFormat2;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.component.Component;
import dev.ikm.tinkar.component.Concept;
import dev.ikm.tinkar.component.FieldDataType;
import dev.ikm.tinkar.component.Pattern;
import dev.ikm.tinkar.component.Semantic;
import dev.ikm.tinkar.component.location.PlanarPoint;
import dev.ikm.tinkar.component.location.SpatialPoint;
import dev.ikm.tinkar.entity.graph.DiGraphEntity;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.PatternFacade;
import dev.ikm.tinkar.terms.SemanticFacade;
import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;
import org.eclipse.collections.impl.factory.primitive.LongLists;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static java.nio.charset.StandardCharsets.UTF_8;

/**
 * Entity format 2, the format of a 64-bit store (design {@code design-2026-10-07-64-bit-rocks-store};
 * IKE-Network/ike-issues#1258), behind the {@link EntityRecordFactory} facade, which chooses it
 * when the open store's {@linkplain NidLayout#entityFormat() layout names it} and dispatches a
 * read on the record's first byte. The envelope, and the identity at the head of a chronology,
 * are {@link EntityRecordFormat2}'s; this class writes and reads what follows them.
 *
 * <p>What differs from format 1:
 * <ul>
 *   <li>every nid is its two halves as varints, two to five bytes for the references a
 *   knowledge base mostly holds, and every nid must be a 64-bit nid;</li>
 *   <li>every count and length is a varint: the version count, the field count, the length of a
 *   string, a decimal or a byte array, the size of an object array, an id list or an id set, the
 *   count of a pattern's field definitions, and the counts inside graph bytes;</li>
 *   <li>an id set is grouped by pattern: for each pattern sequence, the pattern, the count, the
 *   first element, then each further element as the gap from the one before; an id list keeps
 *   its order, one nid per member;</li>
 *   <li>UUIDs, times, integers, longs and floats keep their fixed widths;</li>
 *   <li>a {@link PlanarPoint} writes its token once and its two floats, and a
 *   {@link SpatialPoint} its three floats, which format 1 did not; a {@link Double} is still
 *   written as a float, since the interchange schema has no double.</li>
 * </ul>
 */
public final class EntityCodec2 {

    /** The format byte: {@link NidLayout#ENTITY_FORMAT_2}. */
    public static final byte FORMAT = NidLayout.ENTITY_FORMAT_2;

    private static final int CHRONOLOGY_SIZE = 256;
    private static final int VERSION_SIZE = 4096;

    private EntityCodec2() {
    }

    // ---------------------------------------------------------------- writing

    /**
     * The format 2 record of an entity: its chronology and every version.
     *
     * @param entity the entity
     * @return the record
     * @throws IllegalArgumentException if a nid of the entity is not a 64-bit nid
     */
    public static byte[] write(Entity<? extends EntityVersion> entity) {
        byte[] chronology = chronology(entity);
        List<byte[]> versions = new ArrayList<>(entity.versions().size());
        for (EntityVersion version : entity.versions()) {
            versions.add(version(version));
        }
        return EntityRecordFormat2.assemble(chronology, versions);
    }

    static byte[] chronology(Entity<? extends EntityVersion> entity) {
        return bytes(CHRONOLOGY_SIZE, buf -> {
            buf.writeByte(entity.entityDataType().token);
            ImmutableLongList additional = entity.additionalUuidLongs();
            EntityRecordFormat2.writeIdentity(buf, entity.nid(), entity.mostSignificantBits(),
                    entity.leastSignificantBits(), additional == null ? null : additional.toArray());
            switch (entity) {
                case SemanticEntity<?> semantic -> {
                    EntityRecordFormat2.writeNid(buf, semantic.referencedComponentNid());
                    EntityRecordFormat2.writeNid(buf, semantic.patternNid());
                }
                case ConceptEntity<?> concept -> { }
                case PatternEntity<?> pattern -> { }
                case StampEntity<?> stamp -> { }
                default -> throw new IllegalStateException("Unexpected entity: " + EntityText.diagnostic(entity));
            }
        });
    }

    /**
     * The format 2 bytes of one version: its type token, its stamp's nid, and its content.
     *
     * @param version the version
     * @return the version part
     */
    public static byte[] version(EntityVersion version) {
        return bytes(VERSION_SIZE, buf -> {
            byte token = version.versionDataType().token;
            if (token == 0) {
                throw new IllegalStateException("Version type token cannot be zero: " + EntityText.diagnostic(version));
            }
            buf.writeByte(token);
            EntityRecordFormat2.writeNid(buf, version.stampNid());
            switch (version) {
                case ConceptEntityVersion concept -> { }
                case PatternVersionRecord pattern -> {
                    EntityRecordFormat2.writeNid(buf, pattern.semanticPurposeNid());
                    EntityRecordFormat2.writeNid(buf, pattern.semanticMeaningNid());
                    buf.writeVarInt(pattern.fieldDefinitions().size());
                    for (FieldDefinitionRecord field : pattern.fieldDefinitions()) {
                        EntityRecordFormat2.writeNid(buf, field.dataTypeNid());
                        EntityRecordFormat2.writeNid(buf, field.purposeNid());
                        EntityRecordFormat2.writeNid(buf, field.meaningNid());
                    }
                }
                case SemanticEntityVersion semantic -> {
                    buf.writeVarInt(semantic.fieldValues().size());
                    for (Object field : semantic.fieldValues()) {
                        writeField(buf, field);
                    }
                }
                case StampEntityVersion stamp -> {
                    EntityRecordFormat2.writeNid(buf, stamp.stateNid());
                    buf.writeLong(stamp.time());
                    EntityRecordFormat2.writeNid(buf, stamp.authorNid());
                    EntityRecordFormat2.writeNid(buf, stamp.moduleNid());
                    EntityRecordFormat2.writeNid(buf, stamp.pathNid());
                }
                default -> throw new IllegalStateException("Unexpected version: " + EntityText.diagnostic(version));
            }
        });
    }

    /**
     * Writes a field value with its type token.
     *
     * @param buf   the buffer
     * @param field the value; never null
     */
    public static void writeField(ByteBuf buf, Object field) {
        if (field == null) {
            throw new IllegalArgumentException("Field value cannot be null. Semantic field values must be initialized before serialization.");
        }
        switch (field) {
            case Boolean value -> { token(buf, FieldDataType.BOOLEAN); buf.writeBoolean(value); }
            case Float value -> { token(buf, FieldDataType.FLOAT); buf.writeFloat(value); }
            case Double value -> { token(buf, FieldDataType.FLOAT); buf.writeFloat(value.floatValue()); }
            case byte[] value -> { token(buf, FieldDataType.BYTE_ARRAY); buf.writeVarInt(value.length); buf.write(value); }
            case Integer value -> { token(buf, FieldDataType.INTEGER); buf.writeInt(value); }
            case Long value -> { token(buf, FieldDataType.LONG); buf.writeLong(value); }
            case BigDecimal value -> { token(buf, FieldDataType.DECIMAL); writeText(buf, value.toString()); }
            case Instant value -> {
                token(buf, FieldDataType.INSTANT);
                buf.writeLong(value.getEpochSecond());
                buf.writeInt(value.getNano());
            }
            case String value -> { token(buf, FieldDataType.STRING); writeText(buf, value); }
            case ConceptFacade value -> { token(buf, FieldDataType.CONCEPT); EntityRecordFormat2.writeNid(buf, value.nid()); }
            case Concept value -> { token(buf, FieldDataType.CONCEPT); EntityRecordFormat2.writeNid(buf, Entity.nid(value)); }
            case SemanticFacade value -> { token(buf, FieldDataType.SEMANTIC); EntityRecordFormat2.writeNid(buf, value.nid()); }
            case Semantic value -> { token(buf, FieldDataType.SEMANTIC); EntityRecordFormat2.writeNid(buf, Entity.nid(value)); }
            case PatternFacade value -> { token(buf, FieldDataType.PATTERN); EntityRecordFormat2.writeNid(buf, value.nid()); }
            case Pattern value -> { token(buf, FieldDataType.PATTERN); EntityRecordFormat2.writeNid(buf, Entity.nid(value)); }
            case EntityFacade value -> { token(buf, FieldDataType.IDENTIFIED_THING); EntityRecordFormat2.writeNid(buf, value.nid()); }
            case Component value -> { token(buf, FieldDataType.IDENTIFIED_THING); EntityRecordFormat2.writeNid(buf, Entity.nid(value)); }
            case DiTreeEntity value -> { token(buf, FieldDataType.DITREE); buf.write(value.getBytes(FORMAT)); }
            case DiGraphEntity<?> value -> { token(buf, FieldDataType.DIGRAPH); buf.write(value.getBytes(FORMAT)); }
            case Object[] value -> {
                token(buf, FieldDataType.OBJECT_ARRAY);
                buf.writeVarInt(value.length);
                for (Object element : value) {
                    writeField(buf, element);
                }
            }
            case PlanarPoint value -> { token(buf, FieldDataType.PLANAR_POINT); buf.writeFloat(value.x()); buf.writeFloat(value.y()); }
            case SpatialPoint value -> {
                token(buf, FieldDataType.SPATIAL_POINT);
                buf.writeFloat(value.x());
                buf.writeFloat(value.y());
                buf.writeFloat(value.z());
            }
            case LongIdList value -> {
                token(buf, FieldDataType.COMPONENT_ID_LIST);
                buf.writeVarInt(value.size());
                value.forEach(id -> EntityRecordFormat2.writeNid(buf, id));
            }
            case LongIdSet value -> { token(buf, FieldDataType.COMPONENT_ID_SET); writeIdSet(buf, value.toArray()); }
            case PublicId value -> { token(buf, FieldDataType.IDENTIFIED_THING); EntityRecordFormat2.writeNid(buf, Entity.nid(value)); }
            case PublicIdList value -> {
                token(buf, FieldDataType.COMPONENT_ID_LIST);
                buf.writeVarInt(value.size());
                value.forEach(publicId -> EntityRecordFormat2.writeNid(buf, PrimitiveData.get().nidForPublicId((PublicId) publicId)));
            }
            case PublicIdSet value -> {
                long[] nids = new long[value.size()];
                int[] i = {0};
                value.forEach(publicId -> nids[i[0]++] = PrimitiveData.get().nidForPublicId((PublicId) publicId));
                token(buf, FieldDataType.COMPONENT_ID_SET);
                writeIdSet(buf, nids);
            }
            default -> throw new IllegalStateException("Unexpected value: %s of class: %s".formatted(field, field.getClass()));
        }
    }

    /**
     * An id set grouped by pattern: the members sorted, then for each pattern sequence the
     * pattern, the member count, the first element sequence, and each further element as the
     * gap from the one before. Every member must be a 64-bit nid.
     */
    private static void writeIdSet(ByteBuf buf, long[] members) {
        long[] sorted = members.clone();
        Arrays.sort(sorted);
        for (long nid : sorted) {
            Nid.validate64(nid);
        }
        int patterns = 0;
        for (int i = 0; i < sorted.length; i++) {
            if (i == 0 || Nid.patternSequence64(sorted[i]) != Nid.patternSequence64(sorted[i - 1])) {
                patterns++;
            }
        }
        buf.writeVarInt(patterns);
        int i = 0;
        while (i < sorted.length) {
            int pattern = Nid.patternSequence64(sorted[i]);
            int end = i;
            while (end < sorted.length && Nid.patternSequence64(sorted[end]) == pattern) {
                end++;
            }
            buf.writeVarInt(pattern);
            buf.writeVarInt(end - i);
            int previous = 0;
            for (int j = i; j < end; j++) {
                int element = Nid.elementSequence64(sorted[j]);
                buf.writeVarInt(element - previous);
                previous = element;
            }
            i = end;
        }
    }

    private static void token(ByteBuf buf, FieldDataType type) {
        buf.writeByte(type.token);
    }

    private static void writeText(ByteBuf buf, String text) {
        byte[] bytes = text.getBytes(UTF_8);
        buf.writeVarInt(bytes.length);
        buf.write(bytes);
    }

    /** Runs the writer into a pooled buffer, doubling it as long as it overflows. */
    static byte[] bytes(int initialSize, Consumer<ByteBuf> writer) {
        int size = initialSize;
        while (true) {
            ByteBuf buf = ByteBufPool.allocate(size);
            try {
                writer.accept(buf);
                return buf.asArray();
            } catch (ArrayIndexOutOfBoundsException overflow) {
                buf.recycle();
                size *= 2;
            }
        }
    }

    // ---------------------------------------------------------------- reading

    /**
     * The entity a format 2 record holds. A version whose stamp is canceled is left out, as
     * format 1 leaves it out, except from a stamp entity.
     *
     * @param record the record
     * @param <T>    the entity type
     * @param <V>    the version type
     * @return the entity
     */
    @SuppressWarnings("unchecked")
    public static <T extends Entity<V>, V extends EntityVersion> T read(byte[] record) {
        EntityRecordFormat2.Parts parts = EntityRecordFormat2.parts(record);
        ByteBuf buf = ByteBuf.wrapForReading(parts.chronology());
        FieldDataType type = FieldDataType.fromToken(buf.readByte());
        long nid = EntityRecordFormat2.readNid(buf);
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
                for (byte[] version : parts.versions()) {
                    ConceptVersionRecord v = (ConceptVersionRecord) version(version, concept);
                    if (!PrimitiveData.get().isCanceledStampNid(v.stampNid())) {
                        versions.add(v);
                    }
                }
                versions.build();
                yield (T) concept;
            }
            case SEMANTIC_CHRONOLOGY -> {
                long referencedComponentNid = EntityRecordFormat2.readNid(buf);
                long patternNid = EntityRecordFormat2.readNid(buf);
                RecordListBuilder<SemanticVersionRecord> versions = RecordListBuilder.make();
                SemanticRecord semantic = new SemanticRecord(msb, lsb, additionalLongs, nid, patternNid, referencedComponentNid, versions);
                for (byte[] version : parts.versions()) {
                    SemanticVersionRecord v = (SemanticVersionRecord) version(version, semantic);
                    if (!PrimitiveData.get().isCanceledStampNid(v.stampNid())) {
                        versions.add(v);
                    }
                }
                versions.build();
                yield (T) semantic;
            }
            case PATTERN_CHRONOLOGY -> {
                RecordListBuilder<PatternVersionRecord> versions = RecordListBuilder.make();
                PatternRecord pattern = new PatternRecord(msb, lsb, additionalLongs, nid, versions);
                for (byte[] version : parts.versions()) {
                    PatternVersionRecord v = (PatternVersionRecord) version(version, pattern);
                    if (!PrimitiveData.get().isCanceledStampNid(v.stampNid())) {
                        versions.add(v);
                    }
                }
                versions.build();
                yield (T) pattern;
            }
            case STAMP -> {
                RecordListBuilder<StampVersionRecord> versions = RecordListBuilder.make();
                StampRecord stamp = new StampRecord(msb, lsb, additionalLongs, nid, versions);
                for (byte[] version : parts.versions()) {
                    versions.add((StampVersionRecord) version(version, stamp));
                }
                versions.build();
                yield (T) stamp;
            }
            default -> throw new IllegalStateException("Unexpected chronology type: " + type);
        };
    }

    private static EntityVersion version(byte[] bytes, Entity<? extends EntityVersion> entity) {
        ByteBuf buf = ByteBuf.wrapForReading(bytes);
        byte token = buf.readByte();
        if (entity.versionDataType().token != token) {
            throw new IllegalStateException("Wrong version token " + token + " (" + FieldDataType.fromToken(token)
                    + ") for " + entity.versionDataType() + " of " + EntityText.diagnostic(entity));
        }
        long stampNid = EntityRecordFormat2.readNid(buf);
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
                long purposeNid = EntityRecordFormat2.readNid(buf);
                long meaningNid = EntityRecordFormat2.readNid(buf);
                int fieldCount = buf.readVarInt();
                MutableList<FieldDefinitionRecord> definitions = Lists.mutable.ofInitialCapacity(fieldCount);
                for (int index = 0; index < fieldCount; index++) {
                    definitions.add(new FieldDefinitionRecord(EntityRecordFormat2.readNid(buf), EntityRecordFormat2.readNid(buf),
                            EntityRecordFormat2.readNid(buf), stampNid, pattern.nid(), index));
                }
                yield new PatternVersionRecord(pattern, stampNid, purposeNid, meaningNid, definitions.toImmutable());
            }
            case StampRecord stamp -> {
                long stateNid = EntityRecordFormat2.readNid(buf);
                long time = buf.readLong();
                long authorNid = EntityRecordFormat2.readNid(buf);
                long moduleNid = EntityRecordFormat2.readNid(buf);
                long pathNid = EntityRecordFormat2.readNid(buf);
                yield new StampVersionRecord(stamp, stateNid, time, authorNid, moduleNid, pathNid);
            }
            default -> throw new IllegalStateException("Unexpected entity: " + EntityText.diagnostic(entity));
        };
    }

    /**
     * Reads a field value whose type token has been read.
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
            case DECIMAL -> new BigDecimal(new String(readBytes(buf), UTF_8));
            case INSTANT -> Instant.ofEpochSecond(buf.readLong(), buf.readInt());
            case STRING -> new String(readBytes(buf), UTF_8);
            case CONCEPT -> EntityProxy.Concept.make(EntityRecordFormat2.readNid(buf));
            case SEMANTIC -> EntityProxy.Semantic.make(EntityRecordFormat2.readNid(buf));
            case PATTERN -> EntityProxy.Pattern.make(EntityRecordFormat2.readNid(buf));
            case IDENTIFIED_THING -> EntityProxy.make(EntityRecordFormat2.readNid(buf));
            case DITREE -> DiTreeEntity.make(buf, FORMAT);
            case DIGRAPH -> DiGraphEntity.make(buf, FORMAT);
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
                    nids[i] = EntityRecordFormat2.readNid(buf);
                }
                yield LongIds.list.of(nids);
            }
            case COMPONENT_ID_SET -> LongIds.set.ofAlreadySorted(readIdSet(buf));
            default -> throw new UnsupportedOperationException("Can't handle field read of type: " + type);
        };
    }

    /** The members of an id set as {@link #writeIdSet} wrote them, in ascending order. */
    private static long[] readIdSet(ByteBuf buf) {
        int patterns = buf.readVarInt();
        List<long[]> groups = new ArrayList<>(patterns);
        int total = 0;
        for (int p = 0; p < patterns; p++) {
            int pattern = buf.readVarInt();
            int count = buf.readVarInt();
            long[] group = new long[count];
            int element = 0;
            for (int i = 0; i < count; i++) {
                element += buf.readVarInt();
                group[i] = Nid.compose64(pattern, element);
            }
            groups.add(group);
            total += count;
        }
        long[] members = new long[total];
        int at = 0;
        for (long[] group : groups) {
            System.arraycopy(group, 0, members, at, group.length);
            at += group.length;
        }
        return members;
    }

    private static byte[] readBytes(ByteBuf buf) {
        byte[] bytes = new byte[buf.readVarInt()];
        buf.read(bytes);
        return bytes;
    }

    /**
     * The nids a record references as an entity's identity, for a UUID index rebuilt from the
     * records: the entity's nid and UUIDs.
     *
     * @param record a format 2 record
     * @return the identity at the head of its chronology
     */
    public static EntityRecordFormat2.Identity identity(byte[] record) {
        return EntityRecordFormat2.readIdentity(EntityRecordFormat2.parts(record).chronology());
    }

    /** Unused parameter kept for symmetry with the format 1 reader's signature. */
    static ImmutableList<byte[]> versions(byte[] record) {
        return EntityRecordFormat2.parts(record).versions();
    }
}
