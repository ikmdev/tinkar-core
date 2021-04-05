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
package org.hl7.tinkar.dto.binary;

import java.io.*;
import java.time.Instant;
import java.util.UUID;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.id.*;
import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.component.location.PlanarPoint;
import org.hl7.tinkar.component.location.SpatialPoint;
import org.hl7.tinkar.dto.*;
import org.hl7.tinkar.dto.graph.DiTreeDTO;
import org.hl7.tinkar.dto.graph.DiGraphDTO;

/**
 *
 * @author kec
 */
public class TinkarInput extends DataInputStream {

    private int tinkerFormatVersion = Marshalable.marshalVersion;

    public final int getTinkerFormatVersion() {
        return tinkerFormatVersion;
    }

    public final void setTinkerFormatVersion(int tinkerFormatVersion) {
        this.tinkerFormatVersion = tinkerFormatVersion;
    }

    public TinkarInput(InputStream in) {
        super(in);
        try {
            tinkerFormatVersion = this.readInt();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public TinkarInput(InputStream in, int tinkerFormatVersion) {
        super(in);
        this.tinkerFormatVersion = tinkerFormatVersion;
    }

    public ImmutableList<UUID> readImmutableUuidList() {
        return Lists.immutable.of(readUuidArray());
    }
    public UUID[] readUuidArray() {
        try {
            int length = readInt();
            if (length > 256) {
                throw new IllegalStateException("UUID list of size > 256: " + length);
            }
            UUID[] array = new UUID[length];
            for (int i = 0; i < length; i++) {
                array[i] = new UUID(readLong(), readLong());
            }
            return array;
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public UUID getUuid() {
        try {
          return new UUID(readLong(), readLong());
         } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    
    public Instant readInstant()  {
        try {
            long time = readLong();
            if (time == Long.MAX_VALUE) {
                return Instant.MAX;
            }
            if (time == Long.MIN_VALUE) {
                return Instant.MIN;
            }
            return Instant.ofEpochMilli(time);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public static TinkarInput make(byte[] buf, int tinkerFormatVersion) {
        ByteArrayInputStream bais = new ByteArrayInputStream(buf);
        return new TinkarInput(bais, tinkerFormatVersion);
    }

    /**
     * Will read the first int of the buf to get the tinker format version.
     * @param buf
     * @return
     */
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
    

    public ImmutableList<ConceptVersionDTO> readConceptVersionList(PublicId publicId) {
        try {
            int length = readInt();
            ConceptVersionDTO[] array = new ConceptVersionDTO[length];
            for (int i = 0; i < length; i++) {
                array[i] = ConceptVersionDTO.make(this, publicId);
            }
            return Lists.immutable.of(array);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public ImmutableList<PatternVersionDTO> readPatternVersionList(PublicId componentPublicId) {
        try {
            int length = readInt();
            PatternVersionDTO[] array = new PatternVersionDTO[length];
            for (int i = 0; i < length; i++) {
                array[i] = PatternVersionDTO.make(this, componentPublicId);
            }
            return Lists.immutable.of(array);
        } catch (IOException ex) {
            throw new UncheckedIOException(ex);
        }
    }

    public ImmutableList<SemanticVersionDTO> readSemanticVersionList(PublicId componentPublicId,
                                                                     PublicId patternPublicId,
                                                                     PublicId referencedComponentPublicId) {
        try {
            int length = readInt();
            SemanticVersionDTO[] array = new SemanticVersionDTO[length];
            for (int i = 0; i < length; i++) {
                array[i] = SemanticVersionDTO.make(this, componentPublicId);
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
            array[i] = unmarshal(dataType);
         } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    public Object getTinkarNativeObject() {
        try {
            byte token = readByte();
            FieldDataType dataType = FieldDataType.fromToken(token);
            return unmarshal(dataType);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public VertexId readVertexId() {
        try {
            return VertexIds.of(readLong(), readLong());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public PublicId getPublicId() {
        try {
            int uuidCount = readInt();
            if (uuidCount == 1) {
                return PublicIds.of(readLong(), readLong());
            }
            if (uuidCount == 2) {
                return PublicIds.of(readLong(), readLong(), readLong(), readLong());
            }
            if (uuidCount == 3) {
                return PublicIds.of(readLong(), readLong(), readLong(), readLong(), readLong(), readLong());
            }


            long[] uuidParts = new long[uuidCount * 2];
            for (int i = 0; i < uuidCount; i++) {
                uuidParts[i*2] = readLong();
                uuidParts[i*2 + 1] = readLong();
            }
            return PublicIds.of(uuidParts);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Object unmarshal(FieldDataType dataType) {
        try {
            switch (dataType) {
                case STRING:
                    return readUTF();
                case FLOAT:
                    return readFloat();
                case BOOLEAN:
                    return readBoolean();
                case BYTE_ARRAY:
                    return readByteArray();
                case IDENTIFIED_THING:
                    return new ComponentDTO(getPublicId());
                case INTEGER:
                    return readInt();
                case OBJECT_ARRAY:
                    return readEmbeddedObjectArray();
                case INSTANT:
                    return readInstant();
                case CONCEPT_CHRONOLOGY:
                    return ConceptChronologyDTO.make(this);
                case CONCEPT:
                    return ConceptDTO.make(this);
                case PATTERN_CHRONOLOGY:
                    return PatternChronologyDTO.make(this);
                case PATTERN:
                    return PatternDTO.make(this);
                case SEMANTIC_CHRONOLOGY:
                    return SemanticChronologyDTO.make(this);
                case SEMANTIC:
                    return SemanticDTO.make(this);
                case DITREE:
                    return DiTreeDTO.make(this);
                case DIGRAPH:
                    return DiGraphDTO.make(this);
                case SPATIAL_POINT:
                    return new SpatialPoint(readInt(), readInt(), readInt());
                case PLANAR_POINT:
                    return new PlanarPoint(readInt(), readInt());
                case COMPONENT_ID_LIST:
                    return readIdList();
                case COMPONENT_ID_SET:
                    return readIdSet();
                default:
                    throw new UnsupportedOperationException("TinkarInput does know how to unmarshal: " + dataType);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private PublicIdList<PublicId> readIdList() throws IOException {
        PublicId[] publicIds = getPublicIds();
        return PublicIds.list.ofArray(publicIds);
    }

    private PublicIdSet<PublicId>  readIdSet() throws IOException {
        PublicId[] publicIds = getPublicIds();
        return PublicIds.set.ofArray(publicIds);
    }

    private PublicId[] getPublicIds() throws IOException {
        int size = getInt();
        PublicId[] publicIds = new PublicId[size];
        for (int publicIdCount = 0; publicIdCount < size; publicIdCount++) {
            UUID[] uuidArray = new UUID[getInt()];
            if (uuidArray.length == 0) {
                throw new IllegalStateException("UUID array with size 0");
            }
            for (int uuidCount = 0; uuidCount < uuidArray.length; uuidCount++) {
                uuidArray[uuidCount] = new UUID(readLong(), readLong());
            }
            publicIds[publicIdCount] = PublicIds.of(uuidArray);
        }
        return publicIds;
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

    public int getInt() {
        try {
            return readInt();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    public long getLong() {
        try {
            return readLong();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
