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

import dev.ikm.tinkar.common.id.Nid;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * The storage of one pattern sequence: its records and the citations of its elements, each
 * indexed by element sequence minus one, and the counter that issues its element sequences. A
 * pattern's elements are one nid range, {@code compose64(sequence, 1)} upward, so the table is
 * the range: enumerating a pattern walks it, and dropping a scope's pattern drops it.
 */
final class PatternTable {

    /** The first element sequence of every pattern. */
    static final int FIRST_ELEMENT = 1;
    /** The last element sequence a pattern may issue. */
    private static final int LAST_SEQUENCE = Nid.MAX_SEQUENCE_64;

    final int sequence;
    final SpinedSlots<byte[]> records;
    final SpinedSlots<long[]> citations;
    private final AtomicInteger next;
    /** Set when the range is dropped: the next save deletes the pattern's directory before writing it again. */
    volatile boolean purge;

    PatternTable(int sequence, SpinedSlots<byte[]> records, SpinedSlots<long[]> citations, int next) {
        this.sequence = sequence;
        this.records = records;
        this.citations = citations;
        this.next = new AtomicInteger(Math.max(FIRST_ELEMENT, next));
    }

    /** The slot of an element of this pattern: its element sequence minus one. */
    static int slotOf(long nid) {
        return Nid.elementSequence64(nid) - 1;
    }

    /** The nid of a slot of this pattern. */
    long nidOf(int slot) {
        return Nid.compose64(sequence, slot + 1);
    }

    /**
     * Issues the next element sequence. One fetch-and-add, as in the 64-bit Rocks store's
     * counters: a call past the ceiling puts the counter back there, so it never wraps.
     *
     * @throws IllegalStateException if the pattern has issued every sequence it may
     */
    int nextElementSequence() {
        int issued = next.getAndIncrement();
        if (issued > Nid.MAX_SEQUENCE_64 || issued < FIRST_ELEMENT) {
            next.set(Nid.MAX_SEQUENCE_64 + 1);
            throw new IllegalStateException("Pattern " + sequence + " has issued every element sequence it may, "
                    + LAST_SEQUENCE + "; no new element can be created in it");
        }
        return issued;
    }

    /** One past the last element sequence issued. */
    int next() {
        return next.get();
    }

    /** Raises the counter to at least {@code atLeast}: what a store found written, or minted, beyond what it saved. */
    void raiseTo(int atLeast) {
        next.accumulateAndGet(atLeast, Math::max);
    }

    /** Releases every record and citation of the range at once; the counter stays, so no nid is reused. */
    void drop() {
        records.clear();
        citations.clear();
        purge = true;
    }
}
