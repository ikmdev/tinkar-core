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
package dev.ikm.tinkar.common.util.io;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The counter reads in bulk, as the stream beneath does, and counts what it read. */
class CountingInputStreamTest {

    @Test
    void bulkReadsPassThroughInOneCall() throws IOException {
        byte[] data = new byte[100_000];
        AtomicInteger calls = new AtomicInteger();
        InputStream beneath = new ByteArrayInputStream(data) {
            @Override
            public synchronized int read(byte[] b, int off, int len) {
                calls.incrementAndGet();
                return super.read(b, off, len);
            }
        };
        try (CountingInputStream counting = new CountingInputStream(beneath)) {
            byte[] buffer = new byte[65_536];
            long total = 0;
            int read;
            while ((read = counting.read(buffer, 0, buffer.length)) != -1) {
                total += read;
            }
            assertEquals(data.length, total);
            assertEquals(data.length, counting.getBytesRead());
            assertEquals(3, calls.get(), "two reads to the end and one that reports it");
        }
    }

    @Test
    void singleByteReadsAndSkipsAreCountedToo() throws IOException {
        try (CountingInputStream counting = new CountingInputStream(new ByteArrayInputStream(new byte[10]))) {
            assertEquals(0, counting.read());
            assertEquals(4, counting.skip(4));
            assertEquals(5, counting.available());
            assertEquals(5, counting.getBytesRead());
        }
    }
}
