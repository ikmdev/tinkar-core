/*
 * Copyright 2020 kec.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hl7.tinkar.lombok.dto.binary;


import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.impl.list.mutable.primitive.ByteArrayList;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.id.PublicIdList;
import org.hl7.tinkar.common.id.PublicIdSet;
import org.hl7.tinkar.common.id.VertexId;
import org.hl7.tinkar.component.Component;
import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.lombok.dto.*;
import org.hl7.tinkar.lombok.dto.graph.DiGraphDTO;
import org.hl7.tinkar.lombok.dto.graph.DiTreeDTO;

/**
 *
 * @author kec
 */
public class TinkarOutput extends DataOutputStream {


    private int tinkerFormatVersion = Marshalable.marshalVersion;

    public final int getTinkerFormatVersion() {
        return tinkerFormatVersion;
    }

    public final void setTinkerFormatVersion(int tinkerFormatVersion) {
        this.tinkerFormatVersion = tinkerFormatVersion;
    }

    public TinkarOutput(OutputStream out) {
        super(out);
        try {
            writeInt(Marshalable.marshalVersion);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public TinkarOutput(OutputStream out, int tinkerFormatVersion) {
        super(out);
        this.tinkerFormatVersion = tinkerFormatVersion;
    }

    public void writeUuidArray(UUID[] array) {
        try {
            writeInt(array.length);
            for (UUID uuid: array) {
                writeLong(uuid.getMostSignificantBits());
                writeLong(uuid.getLeastSignificantBits());
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public void writeUuid(UUID uuid) {
        try {
            writeLong(uuid.getMostSignificantBits());
            writeLong(uuid.getLeastSignificantBits());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    /**
     * Always convert to UTC...
     * @param instant 
     */
    public void writeInstant(Instant instant)  {        
        try {
            writeLong(instant.toEpochMilli());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
        
    }    

    public void writeFieldDefinitionList(ImmutableList<FieldDefinitionDTO> fieldDefinitions) {
        try {
            writeInt(fieldDefinitions.size());
            for (FieldDefinitionDTO fieldDefinition: fieldDefinitions) {
                fieldDefinition.marshal(this);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public void writeConceptVersionList(ImmutableList<ConceptVersionDTO> versions) {
        try {
            writeInt(versions.size());
            for (ConceptVersionDTO version: versions) {
                version.marshal(this);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public void writeDefinitionForSemanticVersionList(ImmutableList<TypePatternForSemanticVersionDTO> versions) {
        try {
            writeInt(versions.size());
            for (TypePatternForSemanticVersionDTO version: versions) {
                version.marshal(this);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public void writeSemanticVersionList(ImmutableList<SemanticVersionDTO> versions) {
        try {
            writeInt(versions.size());
            for (SemanticVersionDTO version: versions) {
                version.marshal(this);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public void putTinkarNativeObject(Object object) {
        writeField(object);
    }

    private void writeField(Object object) {
        FieldDataType fieldDataType = FieldDataType.getFieldDataType(object);
        try {
            switch (fieldDataType) {
                case BOOLEAN:
                    writeBoolean((boolean) object, fieldDataType);
                    break;
                case BYTE_ARRAY:
                    if (object instanceof ByteArrayList bal) {
                        object = bal.toArray();
                    }
                    writeByteArray((byte[]) object, fieldDataType);
                    break;
                case DIGRAPH:
                    writeDigraph((DiGraphDTO) object);
                    break;
                case DITREE:
                    writeDitree((DiTreeDTO) object);
                    break;
                case FLOAT:
                    writeFloat((Number) object, fieldDataType);
                    break;
                case INTEGER:
                    writeInteger((Number) object, fieldDataType);
                    break;
                case OBJECT_ARRAY:
                    writeObjectArray((Object[]) object, fieldDataType);
                    break;
                case STRING:
                    writeString((String) object, fieldDataType);
                    break;
                case INSTANT:
                    writeInstant((Instant) object, fieldDataType);
                    break;
                case IDENTIFIED_THING:
                    writeIdentifiedThing((Component) object, fieldDataType);
                    break;

                case COMPONENT_ID_LIST:
                    writeIdList((PublicIdList<PublicId>) object, fieldDataType);
                    break;
                case COMPONENT_ID_SET:
                    writeIdSet((PublicIdSet<PublicId>) object, fieldDataType);
                    break;
                case CONCEPT:
                case CONCEPT_CHRONOLOGY:
                case PATTERN_FOR_SEMANTIC:
                case PATTERN_FOR_SEMANTIC_CHRONOLOGY:
                case SEMANTIC:
                case SEMANTIC_CHRONOLOGY:
                    writeMarshalableObject((Marshalable) object);
                break;

                default:
                    throw new UnsupportedOperationException("writeField can't handle: " + object + " " + fieldDataType);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void writeIdList(PublicIdList<PublicId> idList, FieldDataType fieldDataType) throws IOException {
        writeInt(idList.size());
        idList.forEach(publicId -> {
            putInt(publicId.uuidCount());
            publicId.forEach(longValue -> putLong(longValue));
        });
    }

    private void writeIdSet(PublicIdSet<PublicId> idSet, FieldDataType fieldDataType) throws IOException {
        writeInt(idSet.size());
        idSet.forEach(publicId -> {
            putInt(publicId.uuidCount());
            publicId.forEach(longValue -> putLong(longValue));
        });
    }


    private void writeInstant(Instant instant, FieldDataType fieldDataType) throws IOException {
        writeByte(fieldDataType.token);
        writeInstant(instant);
    }

    private void writeIdentifiedThing(Component object, FieldDataType fieldDataType) throws IOException {
        writeByte(fieldDataType.token);
        putPublicId(object.publicId());
    }
    private void writeMarshalableObject(Marshalable object) throws IOException {
        FieldDataType dataType = FieldDataType.getFieldDataType(object);
        this.writeByte(dataType.token);
        object.marshal(this);
    }

    private void writeString(String object, FieldDataType fieldDataType) throws IOException {
        writeByte(fieldDataType.token);
        writeUTF(object);
    }

    private void writeObjectArray(Object[] object, FieldDataType fieldDataType) throws IOException {
        writeByte(fieldDataType.token);
        Object[] objects = object;
        writeInt(objects.length);
        for (int i = 0; i < objects.length; i++) {
            writeField(objects[i]);
        }
    }

    private void writeInteger(Number object, FieldDataType fieldDataType) throws IOException {
        writeByte(fieldDataType.token);
        writeInt(object.intValue());
    }

    private void writeFloat(Number object, FieldDataType fieldDataType) throws IOException {
        writeByte(fieldDataType.token);
        writeFloat(object.floatValue());
    }

    private void writeDigraph(DiGraphDTO digraph) throws IOException{
        writeByte(FieldDataType.DIGRAPH.token);
        digraph.marshal(this);
    }

    private void writeDitree(DiTreeDTO digree) throws IOException{
        writeByte(FieldDataType.DITREE.token);
        digree.marshal(this);
    }

    private void writeByteArray(byte[] object, FieldDataType fieldDataType) throws IOException {
        writeByte(fieldDataType.token);
        writeInt(object.length);
        write(object);
    }

    private void writeBoolean(boolean object, FieldDataType fieldDataType) throws IOException {
        writeByte(fieldDataType.token);
        writeBoolean(object);
    }

    public void writeUuidList(ImmutableList<UUID> statusUuids) {
        writeUuidArray(statusUuids.toArray(new UUID[statusUuids.size()]));
    }


    public void writeVertexId(VertexId vertexId) {
        try {
            writeLong(vertexId.mostSignificantBits());
            writeLong(vertexId.leastSignificantBits());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void putPublicId(PublicId publicId) {
        try {
            writeInt(publicId.uuidCount());
            publicId.forEach(uuidPart -> {
                try {
                    writeLong(uuidPart);
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void putPublicId(Component component) {
        putPublicId(component.publicId());
    }

    public void writeObjectArray(Object[] fields) {
        try {
            writeInt(fields.length);
            for (Object object : fields) {
                writeField(object);
            }
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public void writeObjectList(ImmutableList<Object> fields) {
        writeObjectList(fields.castToList());
    }

    public void writeObjectList(List<Object> fields) {
        writeObjectArray(fields.toArray());
    }

    public void putInt(int v) {
        try {
            writeInt(v);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void putLong(long v) {
        try {
            writeLong(v);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

}
