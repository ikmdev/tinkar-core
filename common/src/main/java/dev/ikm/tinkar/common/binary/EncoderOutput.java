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

import dev.ikm.tinkar.common.id.IntIdList;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import io.activej.bytebuf.ByteBufStrings;
import org.eclipse.collections.api.factory.primitive.IntLists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.list.primitive.IntList;

import java.time.Instant;
import java.util.UUID;

/**
 * Writes primitive and structured values into a binary-encoded byte buffer.
 * Pairs with {@link DecoderInput} to provide symmetric encode/decode capability
 * for the Tinkar binary encoding format. The buffer grows automatically as needed.
 */
public class EncoderOutput {

    /** Default initial buffer capacity in bytes. */
    private static int defaultCapacity = 10240;

    /** The underlying byte buffer being written to. */
    protected ByteBuf buf;

    /**
     * Constructs an encoder output that writes to the given byte buffer.
     *
     * @param buf the byte buffer to write to
     */
    public EncoderOutput(ByteBuf buf) {
        this.buf = buf;
    }

    /**
     * Constructs an encoder output with the specified initial buffer capacity.
     *
     * @param initialCapacity the initial buffer capacity in bytes
     */
    public EncoderOutput(int initialCapacity) {
        this.buf = ByteBufPool.allocate(initialCapacity);
    }

    /**
     * Constructs an encoder output with the default buffer capacity.
     */
    public EncoderOutput() {
        this.buf = ByteBufPool.allocate(defaultCapacity);
    }

    /**
     * Grows the underlying buffer if the remaining write capacity is insufficient.
     * Handles the special case where the buffer is a {@code ByteBufSlice} (package-private subclass)
     * by always reallocating when the concrete class is not exactly {@link ByteBuf}.
     *
     * @param bytesNeeded the number of bytes that the next write operation requires
     */
    private void growIfNeeded(int bytesNeeded) {
        // if ByteBuf is instance of ByteBufSlice, then we can't use the writeRemaining to determine
        // allowed bytes. ByteBufSlice is package private, so can only make sure that the ByteBuf is not a subclass
        if (buf.writeRemaining() < bytesNeeded || (buf.getClass() != ByteBuf.class)) {
            int usedBytes = buf.readRemaining();
            int newCapacity = usedBytes + (usedBytes >> 1);
            ByteBuf newBuf = ByteBufPool.allocate(newCapacity);
            newBuf.put(buf);
            buf.recycle();
            buf = newBuf;
        }
    }

    /**
     * Writes a byte array to the buffer, prefixed by its length as a variable-length int.
     *
     * @param byteArray the byte array to write
     */
    public void writeByteArray(byte[] byteArray) {
        growIfNeeded(byteArray.length + 4);
        buf.writeVarInt(byteArray.length);
        buf.write(byteArray);
    }

    /**
     * Writes an array of UUIDs by converting them to a long array and delegating
     * to {@link #writeLongArray(long[])}.
     *
     * @param uuidArray the UUID array to write
     */
    public void writeUuidArray(UUID[] uuidArray) {
        writeLongArray(UuidUtil.asArray(uuidArray));
    }

    /**
     * Writes a long array to the buffer, prefixed by its length as a variable-length int.
     *
     * @param longArray the long array to write
     */
    public void writeLongArray(long[] longArray) {
        growIfNeeded((longArray.length * 8) + 4);
        buf.writeVarInt(longArray.length);
        for (long value: longArray) {
            buf.writeLong(value);
        }
    }

    /**
     * Writes a single UUID as two consecutive long values (most significant bits,
     * then least significant bits).
     *
     * @param uuid the UUID to write
     */
    public void writeUuid(UUID uuid) {
        growIfNeeded(16);
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    /**
     * Writes a UTF-8 encoded string to the buffer. The byte count is written first
     * as a four-byte int, followed by the encoded string bytes.
     *
     * @param string the string to write
     */
    public void writeString(String string) {
        growIfNeeded(string.length() * 3);
        int tailAtStart = buf.tail();
        buf.writeInt(0); // place for length of bytes for string.
        int byteCount = ByteBufStrings.encodeUtf8(buf.array(), buf.tail(), string);
        buf.tail(tailAtStart);
        buf.writeInt(byteCount);
        buf.moveTail(byteCount);
    }

    /**
     * Writes a boolean value to the buffer.
     *
     * @param v the boolean value to write
     */
    public void writeBoolean(boolean v) {
        growIfNeeded(1);
        buf.writeBoolean(v);
    }

    /**
     * Writes a single byte to the buffer.
     *
     * @param v the byte value to write
     */
    public void writeByte(byte v) {
        growIfNeeded(1);
        buf.writeByte(v);
    }

    /**
     * Writes a character to the buffer.
     *
     * @param v the char value to write
     */
    public void writeChar(char v) {
        growIfNeeded(3);
        buf.writeChar(v);
    }

    /**
     * Writes a double-precision floating-point value to the buffer.
     *
     * @param v the double value to write
     */
    public void writeDouble(double v) {
        growIfNeeded(8);
        buf.writeDouble(v);
    }

    /**
     * Writes a single-precision floating-point value to the buffer.
     *
     * @param v the float value to write
     */
    public void writeFloat(float v) {
        growIfNeeded(4);
        buf.writeFloat(v);
    }

    /**
     * Writes a four-byte integer to the buffer.
     *
     * @param v the int value to write
     */
    public void writeInt(int v) {
        growIfNeeded(4);
        buf.writeInt(v);
    }

    /**
     * Writes an eight-byte long value to the buffer.
     *
     * @param v the long value to write
     */
    public void writeLong(long v) {
        growIfNeeded(8);
        buf.writeLong(v);
    }

    /**
     * Writes a two-byte short value to the buffer.
     *
     * @param v the short value to write
     */
    public void writeShort(short v) {
        growIfNeeded(2);
        buf.writeShort(v);
    }

    /**
     * Writes a variable-length encoded integer to the buffer.
     *
     * @param v the int value to write
     */
    public void writeVarInt(int v) {
        growIfNeeded(5);
        buf.writeVarInt(v);
    }

    /**
     * Writes a variable-length encoded long value to the buffer.
     *
     * @param v the long value to write
     */
    public void writeVarLong(long v) {
        growIfNeeded(10);
        buf.writeVarLong(v);
    }

    /**
     * Writes a native identifier (nid) by resolving it to a {@link PublicId} and writing that.
     *
     * @param nid the native identifier to write
     */
    public void writeNid(int nid) {
        writePublicId(PrimitiveData.publicId(nid));
    }

    /**
     * Writes a {@link PublicId} to the buffer. The UUID list size is written first
     * as a variable-length int, followed by each UUID.
     *
     * @param publicId the public identifier to write
     */
    public void writePublicId(PublicId publicId) {
        ImmutableList<UUID> uuidList = publicId.asUuidList();
        growIfNeeded(4);
        writeVarInt(uuidList.size());
        uuidList.forEach(this::writeUuid);
    }

    /**
     * Writes an array of native identifiers (nids) to the buffer by converting
     * to an immutable int list and delegating to {@link #writeNidList(ImmutableIntList)}.
     *
     * @param nids the nid array to write
     */
    public void writeNidArray(int[] nids) {
        writeNidList(IntLists.immutable.of(nids));
    }

    /**
     * Writes an immutable list of native identifiers (nids) to the buffer.
     * Each nid is resolved to its {@link PublicId} before writing.
     *
     * @param nids the immutable int list of nids to write
     */
    public void writeNidList(ImmutableIntList nids) {
        growIfNeeded(4);
        buf.writeVarInt(nids.size());
        nids.forEach(this::writeNid);
    }

    /**
     * Writes an {@link IntIdList} to the buffer by converting it to an array
     * and delegating to {@link #writeNidArray(int[])}.
     *
     * @param intIdList the integer identifier list to write
     */
    public void writeIntIdList(IntIdList intIdList) {
        writeNidArray(intIdList.toArray());
    }

    /**
     * Writes an {@link Instant} to the buffer as the epoch second (long)
     * followed by the nanosecond adjustment (int).
     *
     * @param instant the instant to write
     */
    public void writeInstant(Instant instant) {
        growIfNeeded(12);
        buf.writeLong(instant.getEpochSecond());
        buf.writeInt(instant.getNano());
    }

    /**
     * Writes an {@link Encodable} object to the buffer by first writing its class name,
     * then delegating to the object's {@link Encodable#encode(EncoderOutput)} method.
     *
     * @param encodable the encodable object to write
     */
    public void write(Encodable encodable) {
        this.writeString(encodable.getClass().getName());
        encodable.encode(this);
    }

    /**
     * Recycles the underlying byte buffer, returning it to the buffer pool.
     */
    public void recycle() {
        buf.recycle();
    }
}
