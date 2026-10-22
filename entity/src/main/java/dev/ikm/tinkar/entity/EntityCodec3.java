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
import dev.ikm.tinkar.common.service.EntityRecordFormat3;
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
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.PatternFacade;
import dev.ikm.tinkar.terms.SemanticFacade;
import dev.ikm.tinkar.terms.StampFacade;
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
 * Entity format 3, the format of a 64-bit store (IKE-Network/ike-issues#1313, after format 2 of
 * design {@code design-2026-10-07-64-bit-rocks-store} and IKE-Network/ike-issues#1258), behind
 * the {@link EntityRecordFactory} facade, which chooses it when the open store's
 * {@linkplain NidLayout#entityFormat() layout names it} and dispatches a read on the record's
 * first byte. The envelope, and the identity at the head of a chronology, are
 * {@link EntityRecordFormat3}'s; this class writes and reads what follows them.
 *
 * <p>What differs from format 1:
 * <ul>
 *   <li>the entity's kind, and a semantic's pattern, are not written: the nid gives them, and
 *   the writer refuses an entity whose nid does not;</li>
 *   <li>a reference whose kind fixes its pattern half is its element half alone, a varint: a
 *   version's stamp, a STAMP's state, author, module and path, a pattern's purpose and meaning
 *   and its field definitions' data type, purpose and meaning, a {@code CONCEPT} or
 *   {@code PATTERN} field, and a graph vertex's meaning and property keys; the writer refuses one
 *   whose pattern half is another. Every other reference is the nid's two halves as varints;</li>
 *   <li>a version carries no type token, and a STAMP's version no stamp nid, which is its own;</li>
 *   <li>every count and length is a varint, an {@code INTEGER} field a zigzag varint, an
 *   {@code INSTANT} its seconds as a zigzag varint and its nanoseconds as a varint, and a STAMP's
 *   time a varint of its bits;</li>
 *   <li>an id set is grouped by pattern: for each pattern sequence, the pattern, the count, the
 *   first element, then each further element as the gap from the one before; an id list keeps
 *   its order, one nid per member;</li>
 *   <li>a graph writes its vertices without their indexes, which are their positions, its
 *   successors as each vertex's list by position, and every count and index as a varint; a
 *   tree's predecessors are not written but rebuilt from its successors;</li>
 *   <li>UUIDs, longs and floats keep their fixed widths;</li>
 *   <li>a {@link PlanarPoint} writes its token once and its two floats, and a
 *   {@link SpatialPoint} its three floats, which format 1 did not; a {@link Double} is still
 *   written as a float, since the interchange schema has no double.</li>
 * </ul>
 */
public final class EntityCodec3 {

    /** The format byte: {@link NidLayout#ENTITY_FORMAT_3}. */
    public static final byte FORMAT = NidLayout.ENTITY_FORMAT_3;

    private static final int CHRONOLOGY_SIZE = 256;
    private static final int VERSION_SIZE = 4096;

    private EntityCodec3() {
    }

    // ---------------------------------------------------------------- writing

    /**
     * The format 3 record of an entity: its chronology and every version.
     *
     * @param entity the entity
     * @return the record
     * @throws IllegalArgumentException if a nid of the entity is not a 64-bit nid, the entity's nid
     *                                  does not give its kind, or a typed reference names another kind
     */
    public static byte[] write(Entity<? extends EntityVersion> entity) {
        try {
            byte[] chronology = chronology(entity);
            List<byte[]> versions = new ArrayList<>(entity.versions().size());
            for (EntityVersion version : entity.versions()) {
                versions.add(version(version));
            }
            return EntityRecordFormat3.assemble(chronology, versions);
        } catch (IllegalArgumentException refused) {
            // The codec's refusals name a nid, not the entity it was written for.
            throw new IllegalArgumentException(refused.getMessage() + ", writing " + EntityText.diagnostic(entity), refused);
        }
    }

    static byte[] chronology(Entity<? extends EntityVersion> entity) {
        return bytes(CHRONOLOGY_SIZE, buf -> {
            EntityRecordFormat3.requireKindOfNid(entity.entityDataType().token, entity.nid(),
                    entity instanceof SemanticEntity<?> semantic ? semantic.patternNid() : 0);
            ImmutableLongList additional = entity.additionalUuidLongs();
            EntityRecordFormat3.writeIdentity(buf, entity.nid(), entity.mostSignificantBits(),
                    entity.leastSignificantBits(), additional == null ? null : additional.toArray());
            switch (entity) {
                case SemanticEntity<?> semantic -> EntityRecordFormat3.writeNid(buf, semantic.referencedComponentNid());
                case ConceptEntity<?> concept -> { }
                case PatternEntity<?> pattern -> { }
                case StampEntity<?> stamp -> { }
                default -> throw new IllegalStateException("Unexpected entity: " + EntityText.diagnostic(entity));
            }
        });
    }

    /**
     * The format 3 bytes of one version: its stamp, unless it is a STAMP's own, and its content.
     *
     * @param version the version
     * @return the version part
     */
    public static byte[] version(EntityVersion version) {
        return bytes(VERSION_SIZE, buf -> {
            if (!(version instanceof StampEntityVersion)) {
                EntityRecordFormat3.writeStampNid(buf, version.stampNid());
            }
            switch (version) {
                case ConceptEntityVersion concept -> { }
                case PatternVersionRecord pattern -> {
                    EntityRecordFormat3.writeConceptNid(buf, pattern.semanticPurposeNid());
                    EntityRecordFormat3.writeConceptNid(buf, pattern.semanticMeaningNid());
                    buf.writeVarInt(pattern.fieldDefinitions().size());
                    for (FieldDefinitionRecord field : pattern.fieldDefinitions()) {
                        EntityRecordFormat3.writeConceptNid(buf, field.dataTypeNid());
                        EntityRecordFormat3.writeConceptNid(buf, field.purposeNid());
                        EntityRecordFormat3.writeConceptNid(buf, field.meaningNid());
                    }
                }
                case SemanticEntityVersion semantic -> {
                    buf.writeVarInt(semantic.fieldValues().size());
                    for (Object field : semantic.fieldValues()) {
                        writeField(buf, field);
                    }
                }
                case StampEntityVersion stamp -> {
                    EntityRecordFormat3.writeConceptNid(buf, stamp.stateNid());
                    buf.writeVarLong(stamp.time());
                    EntityRecordFormat3.writeConceptNid(buf, stamp.authorNid());
                    EntityRecordFormat3.writeConceptNid(buf, stamp.moduleNid());
                    EntityRecordFormat3.writeConceptNid(buf, stamp.pathNid());
                }
                default -> throw new IllegalStateException("Unexpected version: " + EntityText.diagnostic(version));
            }
        });
    }

    /**
     * The nid of a component a field names by public id: its own when the store knows it; for a
     * proxy of a concept, pattern or STAMP, which names its kind, one minted under that kind, so
     * a reference to a component the store does not hold, as a domain set's reference to a
     * foundation it does not declare, is that kind's absent identity. A bare public id carries no
     * kind, and a nid minted for one would guess it, filing a semantic or pattern written later
     * under a concept's nid (IKE-Network/ike-issues#1313): it is refused. A caller naming a
     * component not yet written registers it first ({@code nidForSemantic}, {@code nidForConcept})
     * or names it by a proxy of its kind.
     *
     * @throws IllegalArgumentException if the store does not know a bare public id
     */
    private static long referencedNid(PublicId publicId) {
        if (PrimitiveData.get().hasPublicId(publicId)) {
            return PrimitiveData.get().nidForPublicId(publicId);
        }
        if (publicId instanceof ConceptFacade || publicId instanceof PatternFacade || publicId instanceof StampFacade) {
            return ((EntityFacade) publicId).nid();
        }
        throw new IllegalArgumentException("A field names " + publicId.idString() + ", which the store does not know:"
                + " a public id carries no kind to mint its nid under; register the component first"
                + " (IKE-Network/ike-issues#1313)");
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
            case Integer value -> { token(buf, FieldDataType.INTEGER); EntityRecordFormat3.writeSignedVarInt(buf, value); }
            case Long value -> { token(buf, FieldDataType.LONG); buf.writeLong(value); }
            case BigDecimal value -> { token(buf, FieldDataType.DECIMAL); writeText(buf, value.toString()); }
            case Instant value -> {
                token(buf, FieldDataType.INSTANT);
                EntityRecordFormat3.writeSignedVarLong(buf, value.getEpochSecond());
                buf.writeVarInt(value.getNano());
            }
            case String value -> { token(buf, FieldDataType.STRING); writeText(buf, value); }
            case ConceptFacade value -> { token(buf, FieldDataType.CONCEPT); EntityRecordFormat3.writeConceptNid(buf, value.nid()); }
            case Concept value -> { token(buf, FieldDataType.CONCEPT); EntityRecordFormat3.writeConceptNid(buf, Entity.nid(value)); }
            case SemanticFacade value -> { token(buf, FieldDataType.SEMANTIC); EntityRecordFormat3.writeNid(buf, value.nid()); }
            case Semantic value -> { token(buf, FieldDataType.SEMANTIC); EntityRecordFormat3.writeNid(buf, Entity.nid(value)); }
            case PatternFacade value -> { token(buf, FieldDataType.PATTERN); EntityRecordFormat3.writePatternNid(buf, value.nid()); }
            case Pattern value -> { token(buf, FieldDataType.PATTERN); EntityRecordFormat3.writePatternNid(buf, Entity.nid(value)); }
            case EntityFacade value -> { token(buf, FieldDataType.IDENTIFIED_THING); EntityRecordFormat3.writeNid(buf, value.nid()); }
            case Component value -> { token(buf, FieldDataType.IDENTIFIED_THING); EntityRecordFormat3.writeNid(buf, Entity.nid(value)); }
            case DiTreeEntity value -> { token(buf, FieldDataType.DITREE); value.write3(buf); }
            case DiGraphEntity<?> value -> { token(buf, FieldDataType.DIGRAPH); value.write3(buf); }
            case EntityVertex value -> { token(buf, FieldDataType.VERTEX); value.write3(buf, true); }
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
                value.forEach(id -> EntityRecordFormat3.writeNid(buf, id));
            }
            case LongIdSet value -> { token(buf, FieldDataType.COMPONENT_ID_SET); writeIdSet(buf, value.toArray()); }
            case PublicId value -> { token(buf, FieldDataType.IDENTIFIED_THING); EntityRecordFormat3.writeNid(buf, referencedNid(value)); }
            case PublicIdList value -> {
                token(buf, FieldDataType.COMPONENT_ID_LIST);
                buf.writeVarInt(value.size());
                value.forEach(publicId -> EntityRecordFormat3.writeNid(buf, referencedNid((PublicId) publicId)));
            }
            case PublicIdSet value -> {
                long[] nids = new long[value.size()];
                int[] i = {0};
                value.forEach(publicId -> nids[i[0]++] = referencedNid((PublicId) publicId));
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
     * The entity a format 3 record holds. A version whose stamp is canceled is left out, as
     * format 1 leaves it out, except from a stamp entity.
     *
     * @param record the record
     * @param <T>    the entity type
     * @param <V>    the version type
     * @return the entity
     */
    @SuppressWarnings("unchecked")
    public static <T extends Entity<V>, V extends EntityVersion> T read(byte[] record) {
        // One buffer over the record, each part decoded where it lies: the parts are never
        // copied out of the record, which made the first 64-bit format decode 1.3 to 2.4 times
        // format 1's (IKE-Network/ike-issues#1313). A part's length marks where the next begins.
        EntityRecordFormat3.requireFormat3(record);
        ByteBuf buf = ByteBuf.wrapForReading(record);
        buf.readByte();
        int chronologyLength = buf.readVarInt();
        int chronologyEnd = buf.head() + chronologyLength;
        long nid = EntityRecordFormat3.readNid(buf);
        FieldDataType type = FieldDataType.fromToken(EntityRecordFormat3.kindToken(nid));
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
                readVersions(buf, chronologyEnd, concept, versions, true);
                yield (T) concept;
            }
            case SEMANTIC_CHRONOLOGY -> {
                long referencedComponentNid = EntityRecordFormat3.readNid(buf);
                long patternNid = EntityRecordFormat3.impliedPatternNid(nid);
                RecordListBuilder<SemanticVersionRecord> versions = RecordListBuilder.make();
                SemanticRecord semantic = new SemanticRecord(msb, lsb, additionalLongs, nid, patternNid, referencedComponentNid, versions);
                readVersions(buf, chronologyEnd, semantic, versions, true);
                yield (T) semantic;
            }
            case PATTERN_CHRONOLOGY -> {
                RecordListBuilder<PatternVersionRecord> versions = RecordListBuilder.make();
                PatternRecord pattern = new PatternRecord(msb, lsb, additionalLongs, nid, versions);
                readVersions(buf, chronologyEnd, pattern, versions, true);
                yield (T) pattern;
            }
            case STAMP -> {
                RecordListBuilder<StampVersionRecord> versions = RecordListBuilder.make();
                StampRecord stamp = new StampRecord(msb, lsb, additionalLongs, nid, versions);
                readVersions(buf, chronologyEnd, stamp, versions, false);
                yield (T) stamp;
            }
            default -> throw new IllegalStateException("Unexpected chronology type: " + type);
        };
    }

    /**
     * Reads a record's versions in place into the builder, then builds it.
     *
     * @param buf           the buffer over the record
     * @param chronologyEnd where the chronology ends and the version count begins
     * @param entity        the entity the versions belong to
     * @param versions      the builder
     * @param dropCanceled  whether a version whose stamp is canceled is left out
     */
    @SuppressWarnings("unchecked")
    private static <V extends EntityVersion> void readVersions(ByteBuf buf, int chronologyEnd,
                                                               Entity<? extends EntityVersion> entity,
                                                               RecordListBuilder<V> versions, boolean dropCanceled) {
        buf.head(chronologyEnd);
        int versionCount = buf.readVarInt();
        for (int i = 0; i < versionCount; i++) {
            int length = buf.readVarInt();
            int versionEnd = buf.head() + length;
            V version = (V) version(buf, entity);
            buf.head(versionEnd);
            if (!dropCanceled || !PrimitiveData.get().isCanceledStampNid(version.stampNid())) {
                versions.add(version);
            }
        }
        versions.build();
    }

    /** Reads one version at the buffer's head, after its length. */
    private static EntityVersion version(ByteBuf buf, Entity<? extends EntityVersion> entity) {
        long stampNid = entity instanceof StampRecord ? entity.nid() : EntityRecordFormat3.readStampNid(buf);
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
                long purposeNid = EntityRecordFormat3.readConceptNid(buf);
                long meaningNid = EntityRecordFormat3.readConceptNid(buf);
                int fieldCount = buf.readVarInt();
                MutableList<FieldDefinitionRecord> definitions = Lists.mutable.ofInitialCapacity(fieldCount);
                for (int index = 0; index < fieldCount; index++) {
                    definitions.add(new FieldDefinitionRecord(EntityRecordFormat3.readConceptNid(buf), EntityRecordFormat3.readConceptNid(buf),
                            EntityRecordFormat3.readConceptNid(buf), stampNid, pattern.nid(), index));
                }
                yield new PatternVersionRecord(pattern, stampNid, purposeNid, meaningNid, definitions.toImmutable());
            }
            case StampRecord stamp -> {
                long stateNid = EntityRecordFormat3.readConceptNid(buf);
                long time = buf.readVarLong();
                long authorNid = EntityRecordFormat3.readConceptNid(buf);
                long moduleNid = EntityRecordFormat3.readConceptNid(buf);
                long pathNid = EntityRecordFormat3.readConceptNid(buf);
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
            case INTEGER -> EntityRecordFormat3.readSignedVarInt(buf);
            case LONG -> buf.readLong();
            case DECIMAL -> new BigDecimal(readString(buf));
            case INSTANT -> Instant.ofEpochSecond(EntityRecordFormat3.readSignedVarLong(buf), buf.readVarInt());
            case STRING -> readString(buf);
            case CONCEPT -> EntityProxy.Concept.make(EntityRecordFormat3.readConceptNid(buf));
            case SEMANTIC -> EntityProxy.Semantic.make(EntityRecordFormat3.readNid(buf));
            case PATTERN -> EntityProxy.Pattern.make(EntityRecordFormat3.readPatternNid(buf));
            case IDENTIFIED_THING -> EntityProxy.make(EntityRecordFormat3.readNid(buf));
            case DITREE -> DiTreeEntity.read3(buf);
            case DIGRAPH -> DiGraphEntity.read3(buf);
            case VERTEX -> EntityVertex.read3(buf, -1);
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
                    nids[i] = EntityRecordFormat3.readNid(buf);
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
        if (patterns == 1) {
            // The usual case, every member in one pattern: its group is the result, uncopied.
            int pattern = buf.readVarInt();
            long[] members = new long[buf.readVarInt()];
            int element = 0;
            for (int i = 0; i < members.length; i++) {
                element += buf.readVarInt();
                members[i] = Nid.compose64(pattern, element);
            }
            return members;
        }
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

    /** A string decoded straight from the buffer's array, with no intermediate copy of its bytes. */
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

    /**
     * The nids a record references as an entity's identity, for a UUID index rebuilt from the
     * records: the entity's nid and UUIDs.
     *
     * @param record a format 3 record
     * @return the identity at the head of its chronology, its rest offset counted from the record's start
     */
    public static EntityRecordFormat3.Identity identity(byte[] record) {
        return EntityRecordFormat3.readIdentity(EntityRecordFormat3.chronologyReader(record));
    }

    /** Unused parameter kept for symmetry with the format 1 reader's signature. */
    static ImmutableList<byte[]> versions(byte[] record) {
        return EntityRecordFormat3.parts(record).versions();
    }
}
