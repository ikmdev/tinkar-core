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

import java.util.Arrays;

/**
 * A UUID to sequence map in primitive arrays: two longs of UUID and an int of sequence per
 * slot, open addressing with linear probing, kept under half full. About 24 bytes an entry
 * at that load, against the hundred and more of a boxed map, for a compaction that numbers
 * every component of a large change set. A sequence of 0 marks an empty slot; sequences
 * count from 1. Not thread-safe.
 */
final class UuidSequenceMap {
    private long[] msb;
    private long[] lsb;
    private int[] sequence;
    private int size;
    private int mask;

    UuidSequenceMap(int expected) {
        int capacity = Integer.highestOneBit(Math.max(16, expected * 2 - 1)) << 1;
        allocate(capacity);
    }

    private void allocate(int capacity) {
        msb = new long[capacity];
        lsb = new long[capacity];
        sequence = new int[capacity];
        mask = capacity - 1;
    }

    private static int slot(long high, long low, int mask) {
        long h = high * 0x9E3779B97F4A7C15L ^ low;
        h ^= h >>> 32;
        return (int) h & mask;
    }

    /** The sequence of a UUID, or 0 when the map has none. */
    int get(long high, long low) {
        int i = slot(high, low, mask);
        while (sequence[i] != 0) {
            if (msb[i] == high && lsb[i] == low) {
                return sequence[i];
            }
            i = (i + 1) & mask;
        }
        return 0;
    }

    /** Maps a UUID to a sequence, replacing an earlier mapping of the same UUID. */
    void put(long high, long low, int value) {
        if (value == 0) {
            throw new IllegalArgumentException("Sequences count from 1");
        }
        if ((size + 1) * 2 > msb.length) {
            grow();
        }
        int i = slot(high, low, mask);
        while (sequence[i] != 0) {
            if (msb[i] == high && lsb[i] == low) {
                sequence[i] = value;
                return;
            }
            i = (i + 1) & mask;
        }
        msb[i] = high;
        lsb[i] = low;
        sequence[i] = value;
        size++;
    }

    int size() {
        return size;
    }

    private void grow() {
        long[] oldMsb = msb;
        long[] oldLsb = lsb;
        int[] oldSequence = sequence;
        allocate(oldMsb.length * 2);
        size = 0;
        for (int i = 0; i < oldMsb.length; i++) {
            if (oldSequence[i] != 0) {
                put(oldMsb[i], oldLsb[i], oldSequence[i]);
            }
        }
        Arrays.fill(oldSequence, 0);
    }
}
