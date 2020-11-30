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

import java.io.*;
import java.time.Instant;
import java.util.UUID;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.dto.*;

/**
 *
 * @author kec
 */
public class TinkarInput extends DataInputStream {

    public TinkarInput(InputStream in) {
        super(in);
    }

    public ImmutableList<UUID> readImmutableUuidList() {
        return Lists.immutable.of(readUuidArray());
    }
    public UUID[] readUuidArray() {
        try {
            int length = readInt();
            UUID[] array = new UUID[length];
            for (int i = 0; i < length; i++) {
                array[i] = new UUID(readLong(), readLong());
            }
            return array;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
    
    public Instant readInstant()  {
        try {
            return Instant.ofEpochSecond(readLong(), readInt());
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
    
    public static TinkarInput make(byte[] buf) {
        ByteArrayInputStream bais = new ByteArrayInputStream(buf);
        return new TinkarInput(bais);
    }
    
    public static TinkarInput make(TinkarByteArrayOutput tinkarOut) {
        ByteArrayInputStream bais = new ByteArrayInputStream(tinkarOut.getBytes());
        return new TinkarInput(bais);
    }

    public ImmutableList<FieldDefinitionDTO> readFieldDefinitionList() {
        try {
            int length = readInt();
            FieldDefinitionDTO[] array = new FieldDefinitionDTO[length];
            for (int i = 0; i < length; i++) {
                array[i] = FieldDefinitionDTO.make(this);
            }
            return Lists.immutable.of(array);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }
    

    public ImmutableList<ConceptVersionDTO> readConceptVersionList(ImmutableList<UUID> componentUuids) {
        try {
            int length = readInt();
            ConceptVersionDTO[] array = new ConceptVersionDTO[length];
            for (int i = 0; i < length; i++) {
                array[i] = ConceptVersionDTO.make(this, componentUuids);
            }
            return Lists.immutable.of(array);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public ImmutableList<DefinitionForSemanticVersionDTO> readDefinitionForSemanticVersionList(ImmutableList<UUID> componentUuids) {
        try {
            int length = readInt();
            DefinitionForSemanticVersionDTO[] array = new DefinitionForSemanticVersionDTO[length];
            for (int i = 0; i < length; i++) {
                array[i] = DefinitionForSemanticVersionDTO.make(this, componentUuids);
            }
            return Lists.immutable.of(array);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public ImmutableList<SemanticVersionDTO> readSemanticVersionList(ImmutableList<UUID> componentUuids,
                                                                     ImmutableList<UUID> definitionForSemanticUuids,
                                                                     ImmutableList<UUID> referencedComponentUuids) {
        try {
            int length = readInt();
            SemanticVersionDTO[] array = new SemanticVersionDTO[length];
            for (int i = 0; i < length; i++) {
                array[i] = SemanticVersionDTO.make(this, componentUuids,
                        definitionForSemanticUuids, referencedComponentUuids);
            }
            return Lists.immutable.of(array);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public ImmutableList<Object> readImmutableObjectList() {
        return Lists.immutable.of(readObjectArray());
    }

    public Object[] readObjectArray() {
        try {
            int fieldCount = readInt();
            Object[] array = new Object[fieldCount];
            for (int i = 0; i < array.length; i++) {
                readObject(array, i);
            }
            return array;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private void readObject(Object[] array, int i)  {
        try {
            byte token = readByte();
            FieldDataType dataType = FieldDataType.fromToken(token);
            array[i] = switch (dataType) {
                case STRING -> readUTF();
                case FLOAT -> readFloat();
                case BOOLEAN -> readBoolean();
                case BYTE_ARRAY -> readByteArray();
                case IDENTIFIED_THING -> new IdentifiedThingDTO(readImmutableUuidList());
                case INTEGER -> readInt();
                case OBJECT_ARRAY -> readEmbeddedObjectArray();
                case DIGRAPH -> throw new UnsupportedEncodingException("Can't handle DIGRAPH.");
                case INSTANT -> readInstant();
                case CONCEPT_CHRONOLOGY, CONCEPT, DEFINITION_FOR_SEMANTIC_CHRONOLOGY,
                        DEFINITION_FOR_SEMANTIC, SEMANTIC_CHRONOLOGY, SEMANTIC -> unmarshal(dataType);
                default -> throw new UnsupportedEncodingException(dataType.toString());
            };
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Object unmarshal(FieldDataType dataType) {
        return switch (dataType) {
            case CONCEPT_CHRONOLOGY -> ConceptChronologyDTO.make(this);
            case CONCEPT -> ConceptDTO.make(this);
            case DEFINITION_FOR_SEMANTIC_CHRONOLOGY -> DefinitionForSemanticChronologyDTO.make(this);
            case DEFINITION_FOR_SEMANTIC -> DefinitionForSemanticDTO.make(this);
            case SEMANTIC_CHRONOLOGY -> SemanticChronologyDTO.make(this);
            case SEMANTIC -> SemanticDTO.make(this);
            default -> throw new UnsupportedOperationException("TinkarInput does know how to unmarshal: " + dataType);
        };
     }

    private Object[] readEmbeddedObjectArray() throws IOException {
        int size = readInt();
        Object[] objects = new Object[size];
        for (int j = 0; j < size; j++) {
            readObject(objects, j);
        }
        return objects;
    }

    private byte[] readByteArray() throws IOException {
        int size = readInt();
        return readNBytes(size);
    }

}
