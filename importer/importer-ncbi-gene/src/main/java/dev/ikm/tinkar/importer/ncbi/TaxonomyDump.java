/*
 * Copyright © 2026 IKE Network (support@ike.network)
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
package dev.ikm.tinkar.importer.ncbi;

import dev.ikm.tinkar.importer.ImportException;

import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPInputStream;

/**
 * Reads one file of NCBI Taxonomy's {@code taxdump.tar.gz} line by line, without unpacking the
 * archive: a gzip-compressed POSIX tar, read header by header (512-byte blocks: the name at
 * offset 0, the octal size at 124, the type at 156, the ustar name prefix at 345).
 */
final class TaxonomyDump {

    private static final int BLOCK = 512;

    /**
     * What is done with each line.
     */
    @FunctionalInterface
    interface LineConsumer {
        /**
         * Takes a line.
         *
         * @param line the line, without its terminator
         * @throws IOException when the line cannot be used
         */
        void accept(String line) throws IOException;
    }

    private TaxonomyDump() {
    }

    /**
     * Gives every line of a file of the archive to a consumer.
     *
     * @param archive  the {@code .tar.gz}
     * @param fileName the file's name in the archive ({@code nodes.dmp}), matched with or without
     *                 a leading directory
     * @param consumer what takes each line
     * @throws IOException     when the archive cannot be read, or the consumer fails
     * @throws ImportException when the archive has no such file
     */
    static void lines(Path archive, String fileName, LineConsumer consumer) throws IOException {
        try (DataInputStream tar = new DataInputStream(new GZIPInputStream(Files.newInputStream(archive), 1 << 16))) {
            byte[] header = new byte[BLOCK];
            while (true) {
                try {
                    tar.readFully(header);
                } catch (EOFException end) {
                    break;
                }
                if (isZero(header)) {
                    break;
                }
                String name = entryName(header);
                long size = octal(header, 124, 12);
                byte type = header[156];
                boolean regular = type == 0 || type == '0';
                if (regular && (name.equals(fileName) || name.endsWith("/" + fileName))) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(new Bounded(tar, size),
                            StandardCharsets.UTF_8), 1 << 16);
                    String line;
                    while ((line = reader.readLine()) != null) {
                        consumer.accept(line);
                    }
                    return;
                }
                long padded = (size + BLOCK - 1) / BLOCK * BLOCK;
                tar.skipNBytes(padded);
            }
        }
        throw new ImportException(archive + " holds no " + fileName);
    }

    private static String entryName(byte[] header) {
        String name = text(header, 0, 100);
        String prefix = text(header, 345, 155);
        return prefix.isEmpty() ? name : prefix + "/" + name;
    }

    private static String text(byte[] header, int offset, int length) {
        int end = offset;
        while (end < offset + length && header[end] != 0) {
            end++;
        }
        return new String(header, offset, end - offset, StandardCharsets.UTF_8);
    }

    private static long octal(byte[] header, int offset, int length) {
        long value = 0;
        for (int i = offset; i < offset + length; i++) {
            byte b = header[i];
            if (b >= '0' && b <= '7') {
                value = value * 8 + (b - '0');
            } else if (b == 0 || (b == ' ' && value > 0)) {
                break;
            }
        }
        return value;
    }

    private static boolean isZero(byte[] block) {
        for (byte b : block) {
            if (b != 0) {
                return false;
            }
        }
        return true;
    }

    /** A view of the next {@code size} bytes of a stream, which never closes it. */
    private static final class Bounded extends InputStream {
        private final InputStream in;
        private long remaining;

        Bounded(InputStream in, long size) {
            this.in = in;
            this.remaining = size;
        }

        @Override
        public int read() throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int b = in.read();
            if (b >= 0) {
                remaining--;
            }
            return b;
        }

        @Override
        public int read(byte[] buffer, int off, int len) throws IOException {
            if (remaining <= 0) {
                return -1;
            }
            int read = in.read(buffer, off, (int) Math.min(len, remaining));
            if (read > 0) {
                remaining -= read;
            }
            return read;
        }

        @Override
        public void close() {
            // the archive stays open for the caller
        }
    }
}
