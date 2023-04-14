package dev.ikm.tinkar.common.binary;

import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.UUID;

public class DecoderInput {
    int encodingFormatVersion;
    ByteBuf buf;

    public DecoderInput(ByteBuf buf) {
        this.buf = buf;
        this.encodingFormatVersion = buf.readInt();
    }

    public DecoderInput(byte[] bytes) {
        this.buf = ByteBuf.wrapForReading(bytes);
        this.encodingFormatVersion = buf.readInt();
    }

    public int encodingFormatVersion() {
        return encodingFormatVersion;
    }

    public UUID[] readUuidArray() {
        return UuidUtil.toArray(readLongArray());
    }

    public long[] readLongArray() {
        int arraySize = readVarInt();
        long[] longArray = new long[arraySize];
        for (int i = 0; i < arraySize; i++) {
            longArray[i] = readLong();
        }
        return longArray;
    }

    public UUID readUuid() {
        return new UUID(readLong(), readLong());
    }

    public String readString() {
        int byteCount = buf.readInt();
        String decoded = new String(buf.array(), buf.head(), byteCount, StandardCharsets.UTF_8);
        buf.moveHead(byteCount);
        return decoded;
    }

    public byte readByte() {
        return buf.readByte();
    }

    public boolean readBoolean() {
        return buf.readBoolean();
    }

    public char readChar() {
        return buf.readChar();
    }

    public double readDouble() {
        return buf.readDouble();
    }

    public float readFloat() {
        return buf.readFloat();
    }

    public int readInt() {
        return buf.readInt();
    }

    public int readVarInt() {
        return buf.readVarInt();
    }

    public long readLong() {
        return buf.readLong();
    }

    public short readShort() {
        return buf.readShort();
    }

    public long readVarLong() {
        return buf.readVarLong();
    }

    public int readNid() {return buf.readInt();};

    public int[] readNidArray() {
        int arraySize = buf.readVarInt();
        int[] nidArray = new int[arraySize];
        for (int i = 0; i < nidArray.length; i++) {
            nidArray[i] = readInt();
        }
        return nidArray;
    }

    public Instant readInstant() {
        return Instant.ofEpochSecond(readLong(), readInt());
    }
}
