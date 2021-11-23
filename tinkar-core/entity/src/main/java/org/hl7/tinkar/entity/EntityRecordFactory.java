package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.hl7.tinkar.common.id.*;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.component.*;
import org.hl7.tinkar.component.graph.DiGraph;
import org.hl7.tinkar.component.graph.DiTree;
import org.hl7.tinkar.component.location.PlanarPoint;
import org.hl7.tinkar.component.location.SpatialPoint;
import org.hl7.tinkar.entity.graph.DiGraphEntity;
import org.hl7.tinkar.entity.graph.DiTreeEntity;
import org.hl7.tinkar.terms.ComponentWithNid;
import org.hl7.tinkar.terms.EntityFacade;
import org.hl7.tinkar.terms.EntityProxy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.UUID;

import static java.nio.charset.StandardCharsets.UTF_8;

public class EntityRecordFactory {
    public static final byte ENTITY_FORMAT_VERSION = 1;
    public static final int DEFAULT_ENTITY_SIZE = 32767;
    public static final int DEFAULT_VERSION_SIZE = 16384;
    private static final Logger LOG = LoggerFactory.getLogger(EntityRecordFactory.class);
    public static int MAX_ENTITY_SIZE = DEFAULT_ENTITY_SIZE;
    public static int MAX_VERSION_SIZE = DEFAULT_VERSION_SIZE;

    public static byte[] getBytes(Entity<? extends EntityVersion> entity) {
        // TODO: write directly to a single ByteBuf, rather that the approach below.
        boolean complete = false;
        while (!complete) {
            try {
                ByteBuf byteBuf = ByteBufPool.allocate(MAX_ENTITY_SIZE); // one byte for version...
                byteBuf.writeByte(ENTITY_FORMAT_VERSION);
                byteBuf.writeByte(entity.entityDataType().token); //ensure that the chronicle byte array sorts first.
                byteBuf.writeInt(entity.nid());
                byteBuf.writeLong(entity.mostSignificantBits());
                byteBuf.writeLong(entity.leastSignificantBits());
                long[] additionalUuidLongs = entity.additionalUuidLongs();
                if (additionalUuidLongs == null) {
                    byteBuf.writeByte((byte) 0);
                } else {
                    byteBuf.writeByte((byte) additionalUuidLongs.length);
                    for (int i = 0; i < additionalUuidLongs.length; i++) {
                        byteBuf.writeLong(additionalUuidLongs[i]);
                    }
                }
                switch (entity) {
                    case SemanticEntity semanticEntity:
                        byteBuf.writeInt(semanticEntity.referencedComponentNid());
                        byteBuf.writeInt(semanticEntity.patternNid());
                        break;
                    case ConceptRecord conceptEntity:
                        // No additional fieldValues for concept records.
                        break;
                    case PatternEntity patternEntity:
                        // no additional fieldValues
                        break;
                    case StampEntity stampEntity:
                        // no additional fieldValues
                        break;
                    default:
                        throw new IllegalStateException("Unexpected value: " + entity);
                }
                //finishEntityWrite(byteBuf);
                byteBuf.writeInt(entity.versions().size());

                int chronicleArrayCount = entity.versions().size() + 1;
                int chronicleFieldIndex = 0;
                byte[][] entityArray = new byte[chronicleArrayCount][];
                entityArray[chronicleFieldIndex++] = byteBuf.asArray();
                for (EntityVersion version : entity.versions()) {
                    entityArray[chronicleFieldIndex++] = getBytes(version);
                }
                int totalSize = 0;
                totalSize += 4; // Integer for the number of arrays
                for (byte[] arrayBytes : entityArray) {
                    totalSize += 4; // integer for size of array
                    totalSize += arrayBytes.length;
                }
                ByteBuf finalByteBuf = ByteBufPool.allocate(totalSize);
                finalByteBuf.writeInt(entityArray.length);
                for (byte[] arrayBytes : entityArray) {
                    finalByteBuf.writeInt(arrayBytes.length);
                    finalByteBuf.write(arrayBytes);
                }
                return finalByteBuf.asArray();
            } catch (ArrayIndexOutOfBoundsException e) {
                MAX_ENTITY_SIZE = MAX_ENTITY_SIZE * 2;
                LOG.info(e.getMessage() + " will increase entity size to " + MAX_ENTITY_SIZE);
            }
        }
        throw new IllegalStateException("Should never reach here. ");
    }

    public static byte[] getBytes(EntityVersion entityVersion) {
        // TODO: write directly to a single ByteBuf, rather that the approach below.
        boolean complete = false;
        while (!complete) {
            try {
                ByteBuf byteBuf = ByteBufPool.allocate(MAX_VERSION_SIZE);
                if (entityVersion.versionDataType().token == 0) {
                    throw new IllegalStateException("Version type token cannot be zero... " + entityVersion);
                }
                byteBuf.writeByte(entityVersion.versionDataType().token); //ensure that the chronicle byte array sorts first.
                byteBuf.writeInt(entityVersion.stampNid());
                switch (entityVersion) {
                    case ConceptEntityVersion conceptEntityVersion:
                        // no additional data
                        break;
                    case PatternVersionRecord patternVersionRecord:
                        byteBuf.writeInt(patternVersionRecord.semanticPurposeNid());
                        byteBuf.writeInt(patternVersionRecord.semanticMeaningNid());
                        byteBuf.writeInt(patternVersionRecord.fieldDefinitions().size());
                        for (FieldDefinitionRecord field : patternVersionRecord.fieldDefinitions()) {
                            byteBuf.writeInt(field.dataTypeNid());
                            byteBuf.writeInt(field.purposeNid());
                            byteBuf.writeInt(field.meaningNid());
                        }
                        break;
                    case SemanticEntityVersion semanticEntityVersion:
                        byteBuf.writeInt(semanticEntityVersion.fieldValues().size());
                        for (Object field : semanticEntityVersion.fieldValues()) {
                            writeField(byteBuf, field);
                        }
                        break;
                    case StampEntityVersion stampEntityVersion:
                        byteBuf.writeInt(stampEntityVersion.stateNid());
                        byteBuf.writeLong(stampEntityVersion.time());
                        byteBuf.writeInt(stampEntityVersion.authorNid());
                        byteBuf.writeInt(stampEntityVersion.moduleNid());
                        byteBuf.writeInt(stampEntityVersion.pathNid());
                        break;
                    default:
                        throw new IllegalStateException("Unexpected value: " + entityVersion);
                }
                //writeVersionFields(byteBuf);
                return byteBuf.asArray();
            } catch (ArrayIndexOutOfBoundsException e) {
                MAX_VERSION_SIZE = MAX_VERSION_SIZE * 2;
                LOG.info(e.getMessage() + " will increase version size to " + MAX_VERSION_SIZE);
            }
        }
        throw new IllegalStateException("Should never reach here. ");
    }

    public static void writeField(ByteBuf writeBuf, Object field) {
        switch (field) {
            case Boolean booleanField -> {
                writeBuf.writeByte(FieldDataType.BOOLEAN.token);
                writeBuf.writeBoolean(booleanField);
            }
            case Float floatField -> {
                writeBuf.writeByte(FieldDataType.FLOAT.token);
                writeBuf.writeFloat(floatField);
            }
            case byte[] byteArrayField -> {
                writeBuf.writeByte(FieldDataType.BYTE_ARRAY.token);
                writeBuf.writeInt(byteArrayField.length);
                writeBuf.write(byteArrayField);
            }
            case Integer integerField -> {
                writeBuf.writeByte(FieldDataType.INTEGER.token);
                writeBuf.writeInt(integerField);
            }
            case Instant instantField -> {
                writeBuf.writeByte(FieldDataType.INSTANT.token);
                writeBuf.writeLong(instantField.getEpochSecond());
                writeBuf.writeInt(instantField.getNano());
            }
            case String stringField -> {
                writeBuf.writeByte(FieldDataType.STRING.token);
                byte[] bytes = stringField.getBytes(UTF_8);
                writeBuf.writeInt(bytes.length);
                writeBuf.write(bytes);
            }
            case Concept conceptField -> {
                writeBuf.writeByte(FieldDataType.CONCEPT.token);
                if (field instanceof ComponentWithNid) {
                    writeBuf.writeInt(((ComponentWithNid) field).nid());
                } else {
                    writeBuf.writeInt(EntityService.get().nidForComponent(conceptField));
                }
            }
            case Semantic semanticField -> {
                writeBuf.writeByte(FieldDataType.SEMANTIC.token);
                if (field instanceof ComponentWithNid) {
                    writeBuf.writeInt(((ComponentWithNid) field).nid());
                } else {
                    writeBuf.writeInt(EntityService.get().nidForComponent(semanticField));
                }
            }
            case Pattern patternField -> {
                writeBuf.writeByte(FieldDataType.PATTERN.token);
                if (field instanceof ComponentWithNid) {
                    writeBuf.writeInt(((ComponentWithNid) field).nid());
                } else {
                    writeBuf.writeInt(EntityService.get().nidForComponent(patternField));
                }
            }
            case EntityFacade entityField -> {
                writeBuf.writeByte(FieldDataType.IDENTIFIED_THING.token);
                writeBuf.writeInt(entityField.nid());
            }
            case Component componentField -> {
                writeBuf.writeByte(FieldDataType.IDENTIFIED_THING.token);
                writeBuf.writeInt(EntityService.get().nidForComponent(componentField));
            }
            case DiTreeEntity diTreeEntityField -> {
                writeBuf.writeByte(FieldDataType.DITREE.token);
                writeBuf.write(diTreeEntityField.getBytes());
            }
            case PlanarPoint planarPointField -> {
                writeBuf.writeByte(FieldDataType.PLANAR_POINT.token);
                writeBuf.writeInt(planarPointField.x());
                writeBuf.writeInt(planarPointField.y());
            }
            case SpatialPoint spatialPointField -> {
                writeBuf.writeByte(FieldDataType.SPATIAL_POINT.token);
                writeBuf.writeInt(spatialPointField.x());
                writeBuf.writeInt(spatialPointField.y());
                writeBuf.writeInt(spatialPointField.z());
            }
            case IntIdList intIdListField -> {
                writeBuf.writeByte(FieldDataType.COMPONENT_ID_LIST.token);
                writeBuf.writeInt(intIdListField.size());
                intIdListField.forEach(id -> writeBuf.writeInt(id));
            }
            case IntIdSet intIdSetField -> {
                writeBuf.writeByte(FieldDataType.COMPONENT_ID_SET.token);
                writeBuf.writeInt(intIdSetField.size());
                intIdSetField.forEach(id -> writeBuf.writeInt(id));
            }
            case PublicIdList publicIdListField -> {
                MutableIntList nidList = IntLists.mutable.withInitialCapacity(publicIdListField.size());
                publicIdListField.forEach(publicId -> {
                    nidList.add(PrimitiveData.get().nidForPublicId((PublicId) publicId));
                });
                writeBuf.writeByte(FieldDataType.COMPONENT_ID_LIST.token);
                writeBuf.writeInt(nidList.size());
                nidList.forEach(id -> writeBuf.writeInt(id));
            }
            case PublicIdSet publicIdSetField -> {
                MutableIntList nidSet = IntLists.mutable.withInitialCapacity(publicIdSetField.size());
                publicIdSetField.forEach(publicId -> {
                    nidSet.add(PrimitiveData.get().nidForPublicId((PublicId) publicId));
                });
                writeBuf.writeByte(FieldDataType.COMPONENT_ID_SET.token);
                writeBuf.writeInt(nidSet.size());
                nidSet.forEach(id -> writeBuf.writeInt(id));
            }

            default -> throw new IllegalStateException("Unexpected value: " + field);
        }
    }

    public static <T extends Entity<V>, V extends EntityVersion> T make(Chronology<Version> chronology) {
        int nid = EntityService.get().nidForPublicId(chronology.publicId());
        ImmutableList<UUID> componentUuids = chronology.publicId().asUuidList();
        UUID firstUuid = componentUuids.get(0);

        long mostSignificantBits = firstUuid.getMostSignificantBits();
        long leastSignificantBits = firstUuid.getLeastSignificantBits();
        long[] additionalUuidLongs = processAdditionalUuids(componentUuids);
        MutableList<? extends EntityVersion> versions = Lists.mutable.ofInitialCapacity(chronology.versions().size());
        Entity<? extends EntityVersion> entity = switch (chronology) {
            case ConceptChronology conceptChronology -> new ConceptRecord(mostSignificantBits, leastSignificantBits,
                    additionalUuidLongs, nid, (MutableList<ConceptEntityVersion>) versions);

            case PatternChronology patternChronology -> new PatternRecord(mostSignificantBits, leastSignificantBits,
                    additionalUuidLongs, nid, (MutableList<PatternVersionRecord>) versions);

            case SemanticChronology semanticChronology -> new SemanticRecord(mostSignificantBits, leastSignificantBits,
                    additionalUuidLongs, nid,
                    PrimitiveData.nid(semanticChronology.pattern().publicId()),
                    PrimitiveData.nid(semanticChronology.referencedComponent().publicId()),
                    (MutableList<SemanticEntityVersion>) versions);

            case Stamp stamp -> new StampRecord(mostSignificantBits, leastSignificantBits,
                    additionalUuidLongs, nid, (MutableList<StampVersionRecord>) versions);

            default -> throw new IllegalStateException("Unexpected value: " + chronology);
        };
        for (Version version : chronology.versions()) {
            versions.add(makeVersion(version, entity));
        }
        return (T) entity;
    }

    private static long[] processAdditionalUuids(ImmutableList<UUID> componentUuids) {
        if (componentUuids.size() > 1) {
            long[] additionalUuidLongs = new long[(componentUuids.size() - 1) * 2];
            for (int listIndex = 1; listIndex < componentUuids.size(); listIndex++) {
                int additionalUuidIndex = listIndex - 1;
                additionalUuidLongs[additionalUuidIndex * 2] = componentUuids.get(listIndex).getMostSignificantBits();
                additionalUuidLongs[additionalUuidIndex * 2 + 1] = componentUuids.get(listIndex).getLeastSignificantBits();
            }
            return additionalUuidLongs;
        }
        return null;
    }

    private static <V extends EntityVersion> V makeVersion(Version version, Entity<? extends EntityVersion> entity) {
        return (V) switch (version) {
            case ConceptVersion conceptVersion -> new ConceptVersionRecord((ConceptRecord) entity, conceptVersion);
            case PatternVersion patternVersion -> new PatternVersionRecord((PatternRecord) entity, patternVersion);
            case SemanticVersion semanticVersion -> new SemanticVersionRecord((SemanticRecord) entity, semanticVersion);
            case Stamp stamp -> new StampVersionRecord((StampRecord) entity, stamp);

            default -> throw new IllegalStateException("Unexpected value: " + version);
        };
    }

    public static <T extends Entity<V>, V extends EntityVersion> T make(byte[] data) {
        // TODO change to use DecoderInput instead of ByteBuf directly.
        // TODO remove the parts where it computes size.
        ByteBuf buf = ByteBuf.wrapForReading(data);
        // bytes starts with number of arrays (int = 4 bytes), then size of first array (int = 4 bytes), then type token, -1 since index starts at 0...
        int numberOfArrays = buf.readInt();
        int sizeOfFirstArray = buf.readInt();
        byte formatVersion = buf.readByte();
        return make(buf, formatVersion);
    }

    public static <T extends Entity<V>, V extends EntityVersion> T make(ByteBuf readBuf, byte entityFormatVersion) {
        FieldDataType fieldDataType = FieldDataType.fromToken(readBuf.readByte());
        return make(readBuf, entityFormatVersion, fieldDataType);
    }

    public static <T extends Entity<V>, V extends EntityVersion> T make(ByteBuf readBuf, byte entityFormatVersion, FieldDataType fieldDataType) {

        if (entityFormatVersion != ENTITY_FORMAT_VERSION) {
            throw new IllegalStateException("Unsupported entity format version: " + entityFormatVersion);
        }
        int nid = readBuf.readInt();
        long mostSignificantBits = readBuf.readLong();
        long leastSignificantBits = readBuf.readLong();

        int additionalUuidLongCount = readBuf.readByte();
        long[] additionalUuidLongs = null;

        if (additionalUuidLongCount > 0) {
            additionalUuidLongs = new long[additionalUuidLongCount];
            for (int i = 0; i < additionalUuidLongs.length; i++) {
                additionalUuidLongs[i] = readBuf.readLong();
            }
        }
        int versionCount = -1;
        return switch (fieldDataType) {
            case CONCEPT_CHRONOLOGY -> {
                versionCount = readBuf.readInt();
                MutableList<ConceptEntityVersion> versions = Lists.mutable.ofInitialCapacity(versionCount);
                ConceptRecord conceptRecord = new ConceptRecord(mostSignificantBits, leastSignificantBits,
                        additionalUuidLongs, nid, versions);
                for (int i = 0; i < versionCount; i++) {
                    versions.add((ConceptEntityVersion) makeVersion(readBuf, entityFormatVersion, conceptRecord));
                }
                yield (T) conceptRecord;
            }

            case SEMANTIC_CHRONOLOGY -> {
                int referencedComponentNid = readBuf.readInt();
                int patternNid = readBuf.readInt();
                versionCount = readBuf.readInt();
                MutableList<SemanticEntityVersion> versions = Lists.mutable.ofInitialCapacity(versionCount);
                SemanticRecord semanticRecord = new SemanticRecord(mostSignificantBits, leastSignificantBits,
                        additionalUuidLongs, nid, patternNid, referencedComponentNid,
                        versions);
                for (int i = 0; i < versionCount; i++) {
                    versions.add((SemanticEntityVersion) makeVersion(readBuf, entityFormatVersion, semanticRecord));
                }
                yield (T) semanticRecord;
            }

            case PATTERN_CHRONOLOGY -> {
                // no additional fieldValues for pattern.
                versionCount = readBuf.readInt();
                MutableList<PatternVersionRecord> versions = Lists.mutable.ofInitialCapacity(versionCount);
                PatternRecord patternRecord = new PatternRecord(mostSignificantBits, leastSignificantBits,
                        additionalUuidLongs, nid, versions);
                for (int i = 0; i < versionCount; i++) {
                    versions.add((PatternVersionRecord) makeVersion(readBuf, entityFormatVersion, patternRecord));
                }
                yield (T) patternRecord;
            }

            case STAMP -> {
                // no additional fieldValues for stamp
                versionCount = readBuf.readInt();
                MutableList<StampVersionRecord> versions = Lists.mutable.ofInitialCapacity(versionCount);
                StampRecord stampRecord = new StampRecord(mostSignificantBits, leastSignificantBits,
                        additionalUuidLongs, nid, versions);
                for (int i = 0; i < versionCount; i++) {
                    versions.add((StampVersionRecord) makeVersion(readBuf, entityFormatVersion, stampRecord));
                }
                yield (T) stampRecord;
            }

            default -> throw new IllegalStateException("Unexpected fieldDataType: " + fieldDataType);
        };
    }

    private static EntityVersion makeVersion(ByteBuf readBuf, byte formatVersion, Entity<? extends EntityVersion> entity) {
        // bytes used by this version. Not used by this way of reading the data,
        // but is used for merge functions for concurrent write of versions using CAS...
        int bytesInVersion = readBuf.readInt();
        byte token = readBuf.readByte();
        if (entity.versionDataType().token != token) {
            throw new IllegalStateException("Wrong token for type: " + token + " expecting " + entity.versionDataType() +
                    " " + entity.versionDataType().token +
                    " processing " + entity.getClass().getSimpleName() + " " + entity.publicId());
        }
        int stampNid = readBuf.readInt();
        return switch (entity) {
            case ConceptRecord conceptRecord -> new ConceptVersionRecord(conceptRecord, stampNid);
            case SemanticRecord semanticRecord -> {
                int fieldCount = readBuf.readInt();
                MutableList<Object> fields = Lists.mutable.ofInitialCapacity(fieldCount);
                for (int i = 0; i < fieldCount; i++) {
                    FieldDataType dataType = FieldDataType.fromToken(readBuf.readByte());
                    fields.add(readDataType(readBuf, dataType, formatVersion));
                }
                yield new SemanticVersionRecord(semanticRecord, stampNid, fields.toImmutable());
            }
            case PatternRecord patternRecord -> {
                int semanticPurposeNid = readBuf.readInt();
                int semanticMeaningNid = readBuf.readInt();
                int fieldCount = readBuf.readInt();
                MutableList<FieldDefinitionRecord> fieldDefinitionForEntities = Lists.mutable.ofInitialCapacity(fieldCount);
                PatternVersionRecord patternVersionRecord = new PatternVersionRecord(patternRecord, stampNid,
                        semanticPurposeNid, semanticMeaningNid, fieldDefinitionForEntities);
                for (int i = 0; i < fieldCount; i++) {
                    fieldDefinitionForEntities.add(new FieldDefinitionRecord(readBuf.readInt(),
                            readBuf.readInt(), readBuf.readInt(), patternVersionRecord.stampNid()));
                }
                // make field definition list mutable in the record?
                yield patternVersionRecord;
            }
            case StampRecord stampRecord -> {
                int stateNid = readBuf.readInt();
                long time = readBuf.readLong();
                int authorNid = readBuf.readInt();
                int moduleNid = readBuf.readInt();
                int pathNid = readBuf.readInt();
                yield new StampVersionRecord(stampRecord, stateNid, time, authorNid, moduleNid, pathNid);
            }
            default -> throw new IllegalStateException("Unexpected value: " + entity);
        };
    }

    public static Object readDataType(ByteBuf readBuf, FieldDataType dataType, byte formatVersion) {
        return switch (dataType) {
            case BOOLEAN -> readBuf.readBoolean();
            case FLOAT -> readBuf.readFloat();
            case BYTE_ARRAY -> readBytes(readBuf);
            case INTEGER -> readBuf.readInt();
            case STRING -> new String(readBytes(readBuf), UTF_8);
            case DITREE -> DiTreeEntity.make(readBuf, formatVersion);
            case DIGRAPH -> DiGraphEntity.make(readBuf, formatVersion);
            case CONCEPT -> EntityProxy.Concept.make(readBuf.readInt());
            case SEMANTIC -> EntityProxy.Semantic.make(readBuf.readInt());
            case PATTERN -> EntityProxy.Pattern.make(readBuf.readInt());
            case IDENTIFIED_THING -> EntityProxy.make(readBuf.readInt());
            case INSTANT -> Instant.ofEpochSecond(readBuf.readLong(), readBuf.readInt());
            case PLANAR_POINT -> new PlanarPoint(readBuf.readInt(), readBuf.readInt());
            case SPATIAL_POINT -> new SpatialPoint(readBuf.readInt(), readBuf.readInt(), readBuf.readInt());
            case COMPONENT_ID_LIST -> IntIds.list.of(readIntArray(readBuf));
            case COMPONENT_ID_SET -> IntIds.set.of(readIntArray(readBuf));
            default -> throw new UnsupportedOperationException("Can't handle field read of type: " + dataType);
        };
    }

    private static byte[] readBytes(ByteBuf readBuf) {
        int length = readBuf.readInt();
        byte[] bytes = new byte[length];
        readBuf.read(bytes);
        return bytes;
    }

    static protected int[] readIntArray(ByteBuf readBuf) {
        int size = readBuf.readInt();
        int[] array = new int[size];
        for (int i = 0; i < size; i++) {
            array[i] = readBuf.readInt();
        }
        return array;
    }


    public static Object externalToInternalObject(Object externalObject) {
        return switch (externalObject) {
            // no conversion
            case Boolean booleanField -> booleanField;
            case Float floatField -> floatField;
            case byte[] byteField -> byteField;
            case Integer integerField -> integerField;
            case String stringField -> stringField.strip();
            case Instant instantField -> instantField;
            case PlanarPoint planarPointField -> planarPointField;
            case SpatialPoint spatialPointField -> spatialPointField;
            // conversions
            case Concept conceptField -> EntityProxy.Concept.make(EntityService.get().nidForComponent(conceptField));
            case Semantic semanticField -> EntityProxy.Semantic.make(EntityService.get().nidForComponent(semanticField));
            case Pattern patternField -> EntityProxy.Pattern.make(EntityService.get().nidForComponent(patternField));
            case Component componentField -> EntityProxy.make(EntityService.get().nidForComponent(componentField));
            case DiTree diTreeField -> DiTreeEntity.make(diTreeField);
            case DiGraph diGraphField -> DiGraphEntity.make(diGraphField);
            case PublicIdSet publicIdSetField -> {
                MutableIntSet idSet = IntSets.mutable.withInitialCapacity(publicIdSetField.size());
                publicIdSetField.forEach(publicId -> {
                    if (publicId == null) {
                        throw new IllegalStateException("PublicId cannot be null");
                    }
                    idSet.add(EntityService.get().nidForPublicId((PublicId) publicId));
                });
                yield IntIds.set.ofAlreadySorted(idSet.toSortedArray());
            }
            case PublicIdList publicIdListField -> {
                MutableIntList idList = IntLists.mutable.withInitialCapacity(publicIdListField.size());
                publicIdListField.forEach(publicId -> {
                    idList.add(EntityService.get().nidForPublicId((PublicId) publicId));
                });
                yield IntIds.list.of(idList.toArray());
            }

            default -> throw new IllegalStateException("Unexpected value: " + externalObject);
        };
    }
}
