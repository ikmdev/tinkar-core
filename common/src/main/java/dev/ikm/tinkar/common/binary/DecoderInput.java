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
package dev.ikm.tinkar.common.binary;

import dev.ikm.tinkar.common.id.*;
import dev.ikm.tinkar.common.service.PluggableService;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import io.activej.bytebuf.ByteBuf;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.primitive.IntLists;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.MutableIntList;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

/**
 * Reads primitive and structured values from a binary-encoded byte buffer.
 * Pairs with {@link EncoderOutput} to provide symmetric encode/decode capability
 * for the Tinkar binary encoding format.
 */
public class DecoderInput {
    /** The encoding format version read from the start of the buffer. */
    final int encodingFormatVersion;
    /** The underlying byte buffer being read. */
    final ByteBuf buf;

    /**
     * Constructs a decoder that reads from the given byte buffer.
     * The first four bytes are consumed as the encoding format version.
     *
     * @param buf the byte buffer to decode from
     */
    public DecoderInput(ByteBuf buf) {
        this.buf = buf;
        this.encodingFormatVersion = buf.readInt();
    }

    /**
     * Constructs a decoder that reads from the given byte array.
     * The first four bytes are consumed as the encoding format version.
     *
     * @param bytes the byte array to decode from
     */
    public DecoderInput(byte[] bytes) {
        this.buf = ByteBuf.wrapForReading(bytes);
        this.encodingFormatVersion = buf.readInt();
    }

    /**
     * Returns the encoding format version read from the start of the buffer.
     *
     * @return the encoding format version
     */
    public int encodingFormatVersion() {
        return encodingFormatVersion;
    }

    /**
     * Reads an array of UUIDs from the buffer by first reading a long array
     * and converting it to UUID form.
     *
     * @return the decoded UUID array
     */
    public UUID[] readUuidArray() {
        return UuidUtil.toArray(readLongArray());
    }

    /**
     * Reads a variable-length array of long values from the buffer.
     * The array size is read first as a variable-length int, followed by each long value.
     *
     * @return the decoded long array
     */
    public long[] readLongArray() {
        int arraySize = readVarInt();
        long[] longArray = new long[arraySize];
        for (int i = 0; i < arraySize; i++) {
            longArray[i] = readLong();
        }
        return longArray;
    }

    /**
     * Reads a single UUID from the buffer as two consecutive long values
     * (most significant bits, then least significant bits).
     *
     * @return the decoded UUID
     */
    public UUID readUuid() {
        return new UUID(readLong(), readLong());
    }

    /**
     * Reads a UTF-8 encoded string from the buffer. The byte count is read first
     * as a four-byte int, followed by the encoded string bytes.
     *
     * @return the decoded string
     */
    public String readString() {
        int byteCount = buf.readInt();
        String decoded = new String(buf.array(), buf.head(), byteCount, StandardCharsets.UTF_8);
        buf.moveHead(byteCount);
        return decoded;
    }

    /**
     * Reads a single byte from the buffer.
     *
     * @return the decoded byte value
     */
    public byte readByte() {
        return buf.readByte();
    }

    /**
     * Reads a boolean value from the buffer.
     *
     * @return the decoded boolean value
     */
    public boolean readBoolean() {
        return buf.readBoolean();
    }

    /**
     * Reads a character from the buffer.
     *
     * @return the decoded char value
     */
    public char readChar() {
        return buf.readChar();
    }

    /**
     * Reads a double-precision floating-point value from the buffer.
     *
     * @return the decoded double value
     */
    public double readDouble() {
        return buf.readDouble();
    }

    /**
     * Reads a single-precision floating-point value from the buffer.
     *
     * @return the decoded float value
     */
    public float readFloat() {
        return buf.readFloat();
    }

    /**
     * Reads a four-byte integer from the buffer.
     *
     * @return the decoded int value
     */
    public int readInt() {
        return buf.readInt();
    }

    /**
     * Reads a variable-length encoded integer from the buffer.
     *
     * @return the decoded int value
     */
    public int readVarInt() {
        return buf.readVarInt();
    }

    /**
     * Reads an eight-byte long value from the buffer.
     *
     * @return the decoded long value
     */
    public long readLong() {
        return buf.readLong();
    }

    /**
     * Reads a two-byte short value from the buffer.
     *
     * @return the decoded short value
     */
    public short readShort() {
        return buf.readShort();
    }

    /**
     * Reads a variable-length encoded long value from the buffer.
     *
     * @return the decoded long value
     */
    public long readVarLong() {
        return buf.readVarLong();
    }

    /**
     * Reads a public identifier from the buffer and resolves it to a native identifier (nid).
     *
     * @return the resolved native identifier
     */
    public int readNid() {return PrimitiveData.nid(readPublicId());}

    /**
     * Reads a {@link PublicId} from the buffer. The UUID list size is read first
     * as a variable-length int, followed by each UUID.
     *
     * @return the decoded public identifier
     */
    public PublicId readPublicId() {
        int uuidListSize = readVarInt();
        UUID[] uuidList = new UUID[uuidListSize];
        for (int i = 0; i < uuidListSize; i++) {
            uuidList[i] = readUuid();
        }
        return PublicIds.of(uuidList);
    }

    /**
     * Reads an array of native identifiers (nids) from the buffer by reading
     * a nid list and converting it to an array.
     *
     * @return the decoded nid array
     */
    public int[] readNidArray() {
        return readNidList().toArray();
    }

    /**
     * Reads an {@link IntIdList} of native identifiers from the buffer.
     *
     * @return the decoded integer identifier list
     */
    public IntIdList readIntIdList() {
        return IntIds.list.of(readNidArray());
    }

    /**
     * Reads an immutable list of native identifiers (nids) from the buffer.
     * Each entry is read as a {@link PublicId} and resolved to its nid.
     *
     * @return the decoded immutable int list of nids
     */
    public ImmutableIntList readNidList() {
        int listSize = readVarInt();
        MutableList<PublicId> publidIdList = Lists.mutable.ofInitialCapacity(listSize);
        for (int i = 0; i < listSize; i++) {
            publidIdList.add(readPublicId());
        }
        return publidIdList.collectInt(publicId -> PrimitiveData.nid(publicId)).toImmutable();
    }

    /**
     * Decodes an {@link Encodable} object from the buffer. The class name is read
     * first, resolved via {@link PluggableService}, and then the class-specific
     * decoder is invoked.
     *
     * @param <T> the expected type of the decoded object
     * @return the decoded object
     * @throws RuntimeException if the class cannot be found
     */
    public <T extends Encodable> T decode() {
        try {
            String objectClassString = readString();
            return (T) Encodable.decode(PluggableService.forName(objectClassString), this);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Reads an {@link Instant} from the buffer by reading the epoch second (long)
     * followed by the nanosecond adjustment (int).
     *
     * @return the decoded instant
     */
    public Instant readInstant() {
        return Instant.ofEpochSecond(readLong(), readInt());
    }
}
