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
package dev.ikm.tinkar.provider.spinedarray64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** A pattern's slots in memory and written to and read back from its spine files. */
class SpinedSlotsTest {

    private static final SpineGeometry GEOMETRY = new SpineGeometry(4, 16);

    @Test
    void aReadOfAnAbsentSlotAllocatesNothing() {
        SpinedSlots<byte[]> slots = new SpinedSlots<>(GEOMETRY, null, null);
        assertNull(slots.get(1_000_000));
        assertEquals(0, slots.spineCount());
        assertNull(slots.updateAndGet(1_000_000, present -> null));
        assertEquals(0, slots.spineCount());
        slots.getAndSet(0, new byte[]{1});
        assertEquals(1, slots.spineCount());
        assertEquals(1, slots.end());
    }

    @Test
    void slotsAreVisitedInOrderAndEndAfterTheLast() {
        SpinedSlots<byte[]> slots = new SpinedSlots<>(GEOMETRY, null, null);
        int[] written = {0, 3, 4, 27, 100, 101};
        for (int slot : written) {
            slots.getAndSet(slot, new byte[]{(byte) slot});
        }
        List<Integer> visited = new ArrayList<>();
        slots.forEach((slot, value) -> {
            assertEquals((byte) slot, value[0]);
            visited.add(slot);
        });
        assertEquals(List.of(0, 3, 4, 27, 100, 101), visited);
        assertEquals(102, slots.end());
        slots.clear();
        assertEquals(0, slots.end());
        assertNull(slots.get(27));
    }

    @Test
    void changedSpinesAreWrittenAndReadBackLazily(@TempDir Path root) {
        StoreFiles files = new StoreFiles(root.toFile());
        SpinedSlots<byte[]> records = new SpinedSlots<>(GEOMETRY, spine -> files.readRecords(7, spine), null);
        SpinedSlots<long[]> citations = new SpinedSlots<>(GEOMETRY, spine -> files.readCitations(7, spine), null);
        records.getAndSet(2, new byte[]{2, 2});
        records.getAndSet(50, new byte[]{5, 0});
        citations.getAndSet(50, new long[]{11L, 12L});
        AtomicInteger written = new AtomicInteger();
        records.takeChanged((spine, index) -> {
            files.writeRecords(7, index, spine);
            written.incrementAndGet();
        });
        citations.takeChanged((spine, index) -> files.writeCitations(7, index, spine));
        assertEquals(2, written.get());
        records.takeChanged((spine, index) -> written.incrementAndGet());
        assertEquals(2, written.get(), "nothing changed since the last write");

        SpinedSlots<byte[]> reopened = new SpinedSlots<>(GEOMETRY, spine -> files.readRecords(7, spine),
                files.spinesOnDisk(7, StoreFiles.RECORDS));
        SpinedSlots<long[]> reopenedCitations = new SpinedSlots<>(GEOMETRY, spine -> files.readCitations(7, spine),
                files.spinesOnDisk(7, StoreFiles.CITATIONS));
        assertArrayEquals(new byte[]{2, 2}, reopened.get(2));
        assertArrayEquals(new byte[]{5, 0}, reopened.get(50));
        assertNull(reopened.get(3));
        assertArrayEquals(new long[]{11L, 12L}, reopenedCitations.get(50));
        assertEquals(51, reopened.end());

        // A spine emptied is deleted, not written.
        reopened.getAndSet(2, null);
        reopened.takeChanged((spine, index) -> files.writeRecords(7, index, spine));
        assertEquals(1, files.spinesOnDisk(7, StoreFiles.RECORDS).cardinality());
        files.deletePattern(7);
        assertEquals(0, files.spinesOnDisk(7, StoreFiles.RECORDS).cardinality());
    }
}
