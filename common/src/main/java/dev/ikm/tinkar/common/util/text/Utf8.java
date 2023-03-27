package dev.ikm.tinkar.common.util.text;

import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufStrings;

import java.nio.charset.StandardCharsets;

public class Utf8 {

    public static void encode(ByteBuf buf, String string) {
        int headAtStart = buf.head();
        buf.writeInt(0); // place for length of bytes for string.
        int byteCount = ByteBufStrings.encodeUtf8(buf.array(), buf.tail(), string);
        buf.head(headAtStart);
        buf.writeInt(byteCount);
        buf.moveHead(byteCount);
    }

    public static String decode(ByteBuf buf) {
        int byteCount = buf.readInt();
        String decoded = new String(buf.array(), buf.head(), byteCount, StandardCharsets.UTF_8);
        buf.moveHead(byteCount);
        return decoded;
    }

}
