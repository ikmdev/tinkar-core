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
package org.hl7.tinkar.binary;


import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.dto.*;

/**
 *
 * @author kec
 */
public class TinkarOutput extends DataOutputStream {

    public TinkarOutput(OutputStream out) {
        super(out);
    }
    
    public void writeUuidArray(UUID[] array) {
        try {
            writeInt(array.length);
            for (UUID uuid: array) {
                writeLong(uuid.getMostSignificantBits());
                writeLong(uuid.getLeastSignificantBits());
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }
    
    /**
     * Always convert to UTC...
     * @param instant 
     */
    public void writeInstant(Instant instant)  {        
        try {
            writeLong(instant.getEpochSecond());
            writeInt(instant.getNano());
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
        
    }    

    public void writeFieldDefinitionList(ImmutableList<FieldDefinitionDTO> fieldDefinitions) {
        try {
            writeInt(fieldDefinitions.size());
            for (FieldDefinitionDTO fieldDefinition: fieldDefinitions) {
                fieldDefinition.marshal(this);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void writeConceptVersionList(ImmutableList<ConceptVersionDTO> versions) {
        try {
            writeInt(versions.size());
            for (ConceptVersionDTO version: versions) {
                version.marshal(this);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void writeDefinitionForSemanticVersionList(ImmutableList<DefinitionForSemanticVersionDTO> versions) {
        try {
            writeInt(versions.size());
            for (DefinitionForSemanticVersionDTO version: versions) {
                version.marshal(this);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void writeSemanticVersionList(ImmutableList<SemanticVersionDTO> versions) {
        try {
            writeInt(versions.size());
            for (SemanticVersionDTO version: versions) {
                version.marshal(this);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    private void writeField(Object object) throws IOException {
        FieldDataType fieldDataType = FieldDataType.getFieldDataType(object);
        switch (fieldDataType) {
            case BOOLEAN -> {
                writeByte(fieldDataType.token);
                writeBoolean((boolean) object);
            }
            case BYTE_ARRAY -> {
                writeByte(fieldDataType.token);
                writeInt(((byte[]) object).length);
                write((byte[]) object);
            }
            case IDENTIFIED_THING -> {
                writeByte(fieldDataType.token);
                writeUuidList(((IdentifiedThingDTO) object).componentUuids());
            }
            case DIGRAPH -> {
                throw new UnsupportedOperationException();
            }
            case FLOAT -> {
                writeByte(fieldDataType.token);
                writeFloat((Float) object);
            }
            case INTEGER -> {
                writeByte(fieldDataType.token);
                writeInt((Integer) object);
            }
            case OBJECT_ARRAY -> {
                writeByte(fieldDataType.token);
                Object[] objects = (Object[]) object;
                writeInt(objects.length);
                for (int i = 0; i < objects.length; i++) {
                    writeField(objects[i]);
                }
            }
            case STRING -> {
                writeByte(fieldDataType.token);
                writeUTF((String) object);
            }
            default -> {
                throw new UnsupportedOperationException("Can't handle: " + object);
            }
        }
    }

    public void writeUuidList(ImmutableList<UUID> statusUuids) {
        writeUuidArray(statusUuids.toArray(new UUID[statusUuids.size()]));
    }

    public void writeObjectArray(Object[] fields) {
        try {
            writeInt(fields.length);
            for (Object object : fields) {
                writeField(object);
            }
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    public void writeObjectList(ImmutableList<Object> fields) {
        writeObjectList(fields.castToList());
    }

    public void writeObjectList(List<Object> fields) {
        writeObjectArray(fields.toArray());
    }
}
