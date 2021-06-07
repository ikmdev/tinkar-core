package org.hl7.tinkar.entity;

import io.activej.bytebuf.ByteBuf;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.hl7.tinkar.common.id.*;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.util.time.DateTimeUtil;
import org.hl7.tinkar.component.*;
import org.hl7.tinkar.component.graph.DiGraph;
import org.hl7.tinkar.component.graph.DiTree;
import org.hl7.tinkar.component.graph.Vertex;
import org.hl7.tinkar.component.location.PlanarPoint;
import org.hl7.tinkar.component.location.SpatialPoint;
import org.hl7.tinkar.entity.graph.DiGraphEntity;
import org.hl7.tinkar.entity.graph.DiTreeEntity;
import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.dto.graph.DiGraphDTO;
import org.hl7.tinkar.dto.graph.DiTreeDTO;
import org.hl7.tinkar.terms.*;

import java.time.Instant;

import static java.nio.charset.StandardCharsets.UTF_8;

public class SemanticEntityVersion
        extends EntityVersion
        implements SemanticVersion {

    protected final MutableList<Object> fields = Lists.mutable.empty();

    @Override
    public FieldDataType dataType() {
        return FieldDataType.SEMANTIC_VERSION;
    }

    private SemanticEntity getSemanticEntity() {
        return (SemanticEntity) this.chronology;
    }

    @Override
    public SemanticEntity chronology() {
        return (SemanticEntity) super.chronology();
    }

    public PatternEntity pattern() {
        return Entity.getFast(getSemanticEntity().patternNid);
    }

    public int patternNid() {
        return getSemanticEntity().patternNid;
    }

    @Override
    protected void finishVersionFill(ByteBuf readBuf, byte formatVersion) {
        fields.clear();
        int fieldCount = readBuf.readInt();
        for (int i = 0; i < fieldCount; i++) {
            FieldDataType dataType = FieldDataType.fromToken(readBuf.readByte());
            fields.add(readDataType(readBuf, dataType, formatVersion));
        }
    }

    public static Object readDataType(ByteBuf readBuf, FieldDataType dataType, byte formatVersion) {
        switch (dataType) {
            case BOOLEAN:
                return readBuf.readBoolean();
            case FLOAT:
                return readBuf.readFloat();
             case BYTE_ARRAY: {
                int length = readBuf.readInt();
                byte[] bytes = new byte[length];
                readBuf.read(bytes);
                return bytes;
            }
            case INTEGER:
                return readBuf.readInt();
            case STRING: {
                int length = readBuf.readInt();
                byte[] bytes = new byte[length];
                readBuf.read(bytes);
                return new String(bytes, UTF_8);
            }
            case DITREE:
                return DiTreeEntity.make(readBuf, formatVersion);
           case DIGRAPH:
                return DiGraphEntity.make(readBuf, formatVersion);
            case CONCEPT: {
                int nid = readBuf.readInt();
                return ConceptProxy.make(nid);
            }
            case SEMANTIC: {
                int nid = readBuf.readInt();
                return SemanticProxy.make(nid);
            }
            case PATTERN: {
                int nid = readBuf.readInt();
                return PatternProxy.make(nid);
            }
            case IDENTIFIED_THING:
                return EntityProxy.make(readBuf.readInt());
            case INSTANT:
                return Instant.ofEpochSecond(readBuf.readLong(), readBuf.readInt());
            case PLANAR_POINT:
                return new PlanarPoint(readBuf.readInt(), readBuf.readInt());
            case SPATIAL_POINT:
                return new SpatialPoint(readBuf.readInt(), readBuf.readInt(), readBuf.readInt());
            case COMPONENT_ID_LIST:
                return IntIds.list.of(readIntArray(readBuf));
            case COMPONENT_ID_SET:
                return IntIds.set.of(readIntArray(readBuf));
             default:
                throw new UnsupportedOperationException("Can't handle field read of type: " +
                        dataType);
        }
    }
    static protected int[] readIntArray(ByteBuf readBuf) {
        int size = readBuf.readInt();
        int[] array = new int[size];
        for (int i = 0; i < size; i++) {
            array[i] = readBuf.readInt();
        }
        return array;
    }

    @Override
    protected int subclassFieldBytesSize() {
        int size = 0;
        for (Object field: fields) {
            if (field instanceof Boolean) {
                size += 2;
            } else if (field instanceof Float) {
                size += 5;
            } else if (field instanceof byte[] byteArray) {
                size += (5 + byteArray.length);
            } else if (field instanceof Integer) {
                size += 5;
            } else if (field instanceof String string) {
                size += (5 + (string.length() * 2)); // token, length, upper bound on string bytes (average < 16 bit chars for UTF8...).
            } else if (field instanceof EntityFacade) {
                size += 5;
            } else if (field instanceof Component) {
                size += 5;
            } else if (field instanceof DiTreeEntity treeEntity) {
                size += treeEntity.size();
            } else if (field instanceof IntIdSet ids) {
                size += 5 + (ids.size() * 4);
            } else if (field instanceof IntIdList ids) {
                size += 5 + (ids.size() * 4);
            } else if (field instanceof Instant) {
                size += 8;
            } else {
                throw new UnsupportedOperationException("Can't handle field size for type: " +
                        field.getClass().getName());
            }
        }
        return size;
    }

    @Override
    protected void writeVersionFields(ByteBuf writeBuf) {
        writeBuf.writeInt(fields.size());
        for (Object field: fields) {
            writeField(writeBuf, field);
        }
    }

    public FieldDataType fieldDataType(int fieldIndex) {
        return FieldDataType.getFieldDataType(fields.get(fieldIndex));
    }

    public static void writeField(ByteBuf writeBuf, Object field) {
        if (field instanceof Boolean) {
            writeBuf.writeByte(FieldDataType.BOOLEAN.token);
            writeBuf.writeBoolean((Boolean) field);
        } else if (field instanceof Float) {
            writeBuf.writeByte(FieldDataType.FLOAT.token);
            writeBuf.writeFloat((Float) field);
        } else if (field instanceof byte[] byteArray) {
            writeBuf.writeByte(FieldDataType.BYTE_ARRAY.token);
            writeBuf.writeInt(byteArray.length);
            writeBuf.write(byteArray);
        } else if (field instanceof Integer) {
            writeBuf.writeByte(FieldDataType.INTEGER.token);
            writeBuf.writeInt((Integer) field);
        } else if (field instanceof Instant instantField) {
            writeBuf.writeByte(FieldDataType.INSTANT.token);
            writeBuf.writeLong(instantField.getEpochSecond());
            writeBuf.writeInt(instantField.getNano());
        } else if (field instanceof String string) {
            writeBuf.writeByte(FieldDataType.STRING.token);
            byte[] bytes = string.getBytes(UTF_8);
            writeBuf.writeInt(bytes.length);
            writeBuf.write(bytes);
        } else if (field instanceof Concept concept) {
            writeBuf.writeByte(FieldDataType.CONCEPT.token);
            if (field instanceof ComponentWithNid) {
                writeBuf.writeInt(((ComponentWithNid) field).nid());
            } else {
                writeBuf.writeInt(EntityService.get().nidForComponent(concept));
            }
        } else if (field instanceof Semantic semantic) {
            writeBuf.writeByte(FieldDataType.SEMANTIC.token);
            if (field instanceof ComponentWithNid) {
                writeBuf.writeInt(((ComponentWithNid) field).nid());
            } else {
                writeBuf.writeInt(EntityService.get().nidForComponent(semantic));
            }
        } else if (field instanceof Pattern pattern) {
            writeBuf.writeByte(FieldDataType.PATTERN.token);
            if (field instanceof ComponentWithNid) {
                writeBuf.writeInt(((ComponentWithNid) field).nid());
            } else {
                writeBuf.writeInt(EntityService.get().nidForComponent(pattern));
            }
        } else if (field instanceof Entity entity) {
            writeBuf.writeByte(FieldDataType.IDENTIFIED_THING.token);
            writeBuf.writeInt(entity.nid);
        } else if (field instanceof EntityProxy proxy) {
            writeBuf.writeByte(FieldDataType.IDENTIFIED_THING.token);
            writeBuf.writeInt(proxy.nid());
        } else if (field instanceof Component component) {
            writeBuf.writeByte(FieldDataType.IDENTIFIED_THING.token);
            writeBuf.writeInt(EntityService.get().nidForComponent(component));
        } else if (field instanceof DiTreeEntity diTreeEntity) {
            writeBuf.writeByte(FieldDataType.DITREE.token);
            writeBuf.write(diTreeEntity.getBytes());
        } else if (field instanceof PlanarPoint point) {
            writeBuf.writeByte(FieldDataType.PLANAR_POINT.token);
            writeBuf.writeInt(point.x());
            writeBuf.writeInt(point.y());
        } else if (field instanceof SpatialPoint point) {
            writeBuf.writeByte(FieldDataType.SPATIAL_POINT.token);
            writeBuf.writeInt(point.x());
            writeBuf.writeInt(point.y());
            writeBuf.writeInt(point.z());
        } else if (field instanceof IntIdList ids) {
            writeBuf.writeByte(FieldDataType.COMPONENT_ID_LIST.token);
            writeBuf.writeInt(ids.size());
            ids.forEach(id -> writeBuf.writeInt(id));
        } else if (field instanceof IntIdSet ids) {
            writeBuf.writeByte(FieldDataType.COMPONENT_ID_SET.token);
            writeBuf.writeInt(ids.size());
            ids.forEach(id -> writeBuf.writeInt(id));
        } else if (field instanceof PublicIdList publicIdList) {
            MutableIntList nidList = IntLists.mutable.withInitialCapacity(publicIdList.size());
            publicIdList.forEach(publicId -> {
                nidList.add(PrimitiveData.get().nidForPublicId((PublicId)  publicId));
            });
            writeBuf.writeByte(FieldDataType.COMPONENT_ID_LIST.token);
            writeBuf.writeInt(nidList.size());
            nidList.forEach(id -> writeBuf.writeInt(id));
        } else  if (field instanceof PublicIdSet publicIdSet) {
            MutableIntList nidSet = IntLists.mutable.withInitialCapacity(publicIdSet.size());
            publicIdSet.forEach(publicId -> {
                nidSet.add(PrimitiveData.get().nidForPublicId((PublicId)  publicId));
            });
            writeBuf.writeByte(FieldDataType.COMPONENT_ID_SET.token);
            writeBuf.writeInt(nidSet.size());
            nidSet.forEach(id -> writeBuf.writeInt(id));
        } else {
            throw new UnsupportedOperationException("Can't handle field write of type: " +
                    field.getClass().getName());
        }
    }

    @Override
    public ImmutableList<Object> fields() {
        return fields.toImmutable();
    }

    public ImmutableList<Field> fields(PatternEntityVersion patternVersion) {
        Field[] fieldArray = new Field[fields.size()];
        for (int i = 0; i < fieldArray.length; i++) {
            Object value = fields.get(i);
            FieldDefinitionForEntity fieldDef = patternVersion.fieldDefinitions().get(i);
            fieldArray[i] = new FieldRecord(value, fieldDef.purposeNid, fieldDef.meaningNid);
        }
        return Lists.immutable.of(fieldArray);
    }

    public static SemanticEntityVersion make(SemanticEntity semanticEntity, ByteBuf readBuf, byte formatVersion) {
        SemanticEntityVersion version = new SemanticEntityVersion();
        version.fill(semanticEntity, readBuf, formatVersion);
        return version;
    }

    public static SemanticEntityVersion make(SemanticEntity semanticEntity, SemanticVersion versionToCopy) {
        SemanticEntityVersion version = new SemanticEntityVersion();
        version.fill(semanticEntity, versionToCopy);
        version.fields.clear();
        for (Object obj: versionToCopy.fields()) {
            if (obj instanceof Boolean) {
               version.fields.add(obj);
            } else if (obj instanceof Float) {
                version.fields.add(obj);
            } else if (obj instanceof byte[]) {
                version.fields.add(obj);
            } else if (obj instanceof Integer) {
                version.fields.add(obj);
            } else if (obj instanceof String) {
                version.fields.add(obj);
            } else if (obj instanceof Instant) {
                version.fields.add(obj);
            } else if (obj instanceof PlanarPoint) {
                version.fields.add(obj);
            } else if (obj instanceof SpatialPoint) {
                version.fields.add(obj);
            } else if (obj instanceof Component component) {
                version.fields.add(EntityProxy.make(EntityService.get().nidForComponent(component)));
            } else if (obj instanceof DiTreeDTO) {
                DiTree<Vertex> component = (DiTree<Vertex>) obj;
                version.fields.add(DiTreeEntity.make(component));
            } else if (obj instanceof DiGraphDTO) {
                DiGraph<Vertex> component = (DiGraph<Vertex>) obj;
                version.fields.add(DiGraphEntity.make(component));
            } else if (obj instanceof PublicIdSet) {
                PublicIdSet<PublicId> component = (PublicIdSet<PublicId>) obj;
                MutableIntSet idSet = IntSets.mutable.withInitialCapacity(component.size());
                component.forEach(publicId -> {
                    if (publicId == null) {
                        throw new IllegalStateException("PublicId cannot be null");
                    }
                    idSet.add(EntityService.get().nidForPublicId(publicId));
                });
                version.fields.add(IntIds.set.ofAlreadySorted(idSet.toSortedArray()));
            } else if (obj instanceof PublicIdList) {
                PublicIdList<PublicId> component = (PublicIdList<PublicId>) obj;
                MutableIntList idList = IntLists.mutable.withInitialCapacity(component.size());
                component.forEach(publicId -> {
                    idList.add(EntityService.get().nidForPublicId(publicId));
                });
                version.fields.add(IntIds.list.of(idList.toArray()));
            } else {
                throw new UnsupportedOperationException("Can't handle field conversion of type: " +
                        obj.getClass().getName());
            }
        }
        return version;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append(Entity.getStamp(stampNid).describe());
        Entity pattern = Entity.getFast(this.chronology().patternNid);
        if (pattern instanceof PatternEntity patternEntity) {
            // TODO get proper version after relative position computer available.
            // Maybe put stamp coordinate on thread, or relative position computer on thread
            PatternEntityVersion patternEntityVersion =  patternEntity.versions.get(0);
            sb.append("\n");
            for (int i = 0; i < fields.size(); i++) {
               sb.append("Field ");
               sb.append((i+1));
                sb.append(": [");
               StringBuilder fieldStringBuilder = new StringBuilder();

                Object field = fields.get(i);
                if (i < patternEntityVersion.fieldDefinitionForEntities.size()) {
                    FieldDefinitionForEntity fieldDefinition = patternEntityVersion.fieldDefinitionForEntities.get(i);
                    fieldStringBuilder.append(PrimitiveData.text(fieldDefinition.meaningNid));
                } else {
                    fieldStringBuilder.append("Size error @ " + i);
                }
                fieldStringBuilder.append(": ");
                if (field instanceof EntityFacade entity) {
                    fieldStringBuilder.append(PrimitiveData.text(entity.nid()));
                } else if (field instanceof String string) {
                    fieldStringBuilder.append(string);
                } else if (field instanceof Instant instant) {
                    fieldStringBuilder.append(DateTimeUtil.format(instant));
                } else if (field instanceof IntIdList intIdList)  {
                    if (intIdList.size() == 0) {
                        fieldStringBuilder.append("ø");
                    } else {
                        for (int j = 0; j < intIdList.size(); j++) {
                            if (j > 0) {
                                fieldStringBuilder.append(", ");
                            }
                            fieldStringBuilder.append(PrimitiveData.text(intIdList.get(j)));
                        }
                    }
                } else if (field instanceof IntIdSet intIdSet)  {
                    if (intIdSet.size() == 0) {
                        fieldStringBuilder.append("ø");
                    } else {
                        int[] idSetArray = intIdSet.toArray();
                        for (int j = 0; j < idSetArray.length; j++) {
                            if (j > 0) {
                                fieldStringBuilder.append(", ");
                            }
                            fieldStringBuilder.append(PrimitiveData.text(idSetArray[j]));
                        }
                    }
                 } else {
                    fieldStringBuilder.append(field);
                }
                String fieldString = fieldStringBuilder.toString();
                if (fieldString.contains("\n")) {
                    sb.append("\n");
                    sb.append(fieldString);
                } else {
                    sb.append(fieldString);
                }
                sb.append("]\n");

            }
        } else {
            sb.append("Bad pattern: ");
            sb.append(PrimitiveData.text(pattern.nid));
            sb.append("; ");
            for (int i = 0; i < fields.size(); i++) {
                Object field = fields.get(i);
                if (i > 0) {
                    sb.append("; ");
                }
                 if (field instanceof EntityFacade entity) {
                     sb.append("Entity: ");
                     sb.append(PrimitiveData.text(entity.nid()));
                } else if (field instanceof String string) {
                     sb.append("String: ");
                     sb.append(string);
                } else if (field instanceof Instant instant) {
                     sb.append("Instant: ");
                     sb.append(DateTimeUtil.format(instant));
                } else if (field instanceof Long aLong) {
                     sb.append("Long: ");
                     sb.append(DateTimeUtil.format(aLong));
                } else if (field instanceof IntIdList intIdList)  {
                     sb.append(field.getClass().getSimpleName());
                     sb.append(": ");
                     if (intIdList.size() == 0) {
                         sb.append("ø, ");
                     } else {
                         for (int j = 0; j < intIdList.size(); j++) {
                             if (j > 0) {
                                 sb.append(", ");
                             }
                             sb.append(PrimitiveData.text(intIdList.get(j)));
                         }
                     }
                 } else if (field instanceof IntIdSet intIdSet)  {
                     sb.append(field.getClass().getSimpleName());
                     sb.append(": ");
                     if (intIdSet.size() == 0) {
                         sb.append("ø, ");
                     } else {
                         int[] idSetArray = intIdSet.toArray();
                         for (int j = 0; j < idSetArray.length; j++) {
                             if (j > 0) {
                                 sb.append(", ");
                             }
                             sb.append(PrimitiveData.text(idSetArray[j]));
                         }
                     }
                 } else {
                     sb.append(field.getClass().getSimpleName());
                     sb.append(": ");
                     sb.append(field);
                 }
            }
        }

        sb.append("]}");

        return sb.toString();
    }
}
