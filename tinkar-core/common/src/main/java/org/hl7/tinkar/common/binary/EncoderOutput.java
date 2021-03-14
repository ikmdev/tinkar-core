package org.hl7.tinkar.common.binary;

import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import io.activej.bytebuf.ByteBufStrings;
import org.hl7.tinkar.common.util.uuid.UuidUtil;

import java.time.Instant;
import java.util.UUID;

public class EncoderOutput {

    private static int defaultCapacity = 10240;

    protected ByteBuf buf;

    public EncoderOutput(ByteBuf buf) {
        this.buf = buf;
    }

    public EncoderOutput(int initialCapacity) {
        this.buf = ByteBufPool.allocate(initialCapacity);
    }

    public EncoderOutput() {
        this.buf = ByteBufPool.allocate(defaultCapacity);
    }

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


    public void writeByteArray(byte[] byteArray) {
        growIfNeeded(byteArray.length + 4);
        buf.writeVarInt(byteArray.length);
        buf.write(byteArray);
    }


    public void writeUuidArray(UUID[] uuidArray) {
        writeLongArray(UuidUtil.asArray(uuidArray));
    }

    public void writeLongArray(long[] longArray) {
        growIfNeeded((longArray.length * 8) + 4);
        buf.writeVarInt(longArray.length);
        for (long value: longArray) {
            buf.writeLong(value);
        }
    }

    public void writeUuid(UUID uuid) {
        growIfNeeded(16);
        buf.writeLong(uuid.getMostSignificantBits());
        buf.writeLong(uuid.getLeastSignificantBits());
    }

    public void writeString(String string) {
        growIfNeeded(string.length() * 3);
        int headAtStart = buf.head();
        buf.writeInt(0); // place for length of bytes for string.
        int byteCount = ByteBufStrings.encodeUtf8(buf.array(), buf.tail(), string);
        buf.head(headAtStart);
        buf.writeInt(byteCount);
        buf.moveHead(byteCount);
    }

    public void writeBoolean(boolean v) {
        growIfNeeded(1);
        buf.writeBoolean(v);
    }

    public void writeByte(byte v) {
        growIfNeeded(1);
        buf.writeByte(v);
    }

    public void writeChar(char v) {
        growIfNeeded(3);
        buf.writeChar(v);
    }

    public void writeDouble(double v) {
        growIfNeeded(8);
        buf.writeDouble(v);
    }

    public void writeFloat(float v) {
        growIfNeeded(4);
        buf.writeFloat(v);
    }

    public void writeInt(int v) {
        growIfNeeded(4);
        buf.writeInt(v);
    }

    public void writeLong(long v) {
        growIfNeeded(8);
        buf.writeLong(v);
    }

    public void writeShort(short v) {
        growIfNeeded(2);
        buf.writeShort(v);
    }

    public void writeVarInt(int v) {
        growIfNeeded(5);
        buf.writeVarInt(v);
    }

    public void writeVarLong(long v) {
        growIfNeeded(10);
        buf.writeVarLong(v);
    }

    public void writeNid(int nid) {
        growIfNeeded(4);
        buf.writeInt(nid);
    }

    public void writeNidArray(int[] nids) {
        growIfNeeded(4 + (nids.length * 4));
        buf.writeVarInt(nids.length);
        for (int i = 0; i < nids.length; i++) {
            buf.writeInt(nids[i]);
        }
    }

    public void writeInstant(Instant instant) {
        growIfNeeded(12);
        buf.writeLong(instant.getEpochSecond());
        buf.writeInt(instant.getNano());
    }

    public void recycle() {
        buf.recycle();
    }
}
