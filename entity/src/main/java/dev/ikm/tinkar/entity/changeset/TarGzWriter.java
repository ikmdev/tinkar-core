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
package dev.ikm.tinkar.entity.changeset;

import java.io.BufferedOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;

/**
 * A gzip-compressed POSIX tar archive, written with a PAX header for every entry, so a path of
 * any length and in any script (change-set names carry {@code ꞉}, U+A789) survives as it was.
 * {@code tar -xzf} on macOS, Linux and Windows reads it. Only what an archive of originals
 * needs: folders and regular files.
 */
final class TarGzWriter implements Closeable {
    private static final int BLOCK = 512;

    private final OutputStream out;
    private final long modified;

    /**
     * @param file     the archive to write
     * @param modified the modification time every entry carries, in seconds since the epoch
     */
    TarGzWriter(Path file, long modified) throws IOException {
        this.out = new GZIPOutputStream(new BufferedOutputStream(Files.newOutputStream(file), 1 << 16), 1 << 16);
        this.modified = modified;
    }

    /** Adds a folder; {@code name} is relative and uses {@code /}. */
    void folder(String name) throws IOException {
        header(name.endsWith("/") ? name : name + "/", 0, '5', 0755, modified);
    }

    /** Adds a file's text. */
    void text(String name, String text) throws IOException {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        header(name, bytes.length, '0', 0644, modified);
        out.write(bytes);
        pad(bytes.length);
    }

    /** Adds a file's bytes, with its own modification time. */
    void file(String name, Path source) throws IOException {
        long size = Files.size(source);
        header(name, size, '0', 0644, Files.getLastModifiedTime(source).toMillis() / 1000);
        try (InputStream in = Files.newInputStream(source)) {
            long copied = in.transferTo(out);
            if (copied != size) {
                throw new IOException(source + " changed while it was archived: " + copied + " bytes, not " + size);
            }
        }
        pad(size);
    }

    private void header(String name, long size, char type, int mode, long time) throws IOException {
        // The PAX record names the path in full UTF-8; the ustar field keeps an ASCII stand-in.
        String record = paxRecord("path", name);
        byte[] pax = record.getBytes(StandardCharsets.UTF_8);
        writeHeader(ascii("PaxHeaders/" + name), pax.length, 'x', 0644, time);
        out.write(pax);
        pad(pax.length);
        writeHeader(ascii(name), size, type, mode, time);
    }

    private void writeHeader(String name, long size, char type, int mode, long time) throws IOException {
        byte[] header = new byte[BLOCK];
        put(header, 0, 100, name.length() > 100 ? name.substring(name.length() - 100) : name);
        put(header, 100, 8, octal(mode, 7));
        put(header, 108, 8, octal(0, 7));
        put(header, 116, 8, octal(0, 7));
        put(header, 124, 12, octal(size, 11));
        put(header, 136, 12, octal(time, 11));
        java.util.Arrays.fill(header, 148, 156, (byte) ' ');
        header[156] = (byte) type;
        put(header, 257, 6, "ustar");
        put(header, 263, 2, "00");
        long checksum = 0;
        for (byte b : header) {
            checksum += b & 0xff;
        }
        put(header, 148, 8, octal(checksum, 6) + "\0 ");
        out.write(header);
    }

    /** A PAX record: its own length in decimal, a space, key=value, a newline. */
    static String paxRecord(String key, String value) {
        int body = (" " + key + "=" + value + "\n").getBytes(StandardCharsets.UTF_8).length;
        int length = body + Integer.toString(body).length();
        if (Integer.toString(length).length() != Integer.toString(body).length()) {
            length++;
        }
        return length + " " + key + "=" + value + "\n";
    }

    private static String ascii(String name) {
        StringBuilder ascii = new StringBuilder(name.length());
        name.codePoints().forEach(c -> ascii.append(c < 0x80 ? (char) c : '_'));
        return ascii.toString();
    }

    private static String octal(long value, int digits) {
        String octal = Long.toOctalString(value);
        return "0".repeat(Math.max(0, digits - octal.length())) + octal;
    }

    private static void put(byte[] header, int offset, int length, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, header, offset, Math.min(length, bytes.length));
    }

    private void pad(long size) throws IOException {
        int remainder = (int) (size % BLOCK);
        if (remainder != 0) {
            out.write(new byte[BLOCK - remainder]);
        }
    }

    @Override
    public void close() throws IOException {
        out.write(new byte[2 * BLOCK]);
        out.close();
    }
}
