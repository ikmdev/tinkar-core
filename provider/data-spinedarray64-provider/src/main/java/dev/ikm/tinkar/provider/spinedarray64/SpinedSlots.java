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

import java.util.Arrays;
import java.util.BitSet;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.BinaryOperator;
import java.util.function.IntFunction;

/**
 * One pattern's slots of one kind, records or citations, cut into spines by a
 * {@link SpineGeometry}. A spine is allocated when its first slot is written, or read from disk
 * when first touched if the store holds it; a read of a slot whose spine is neither returns null
 * and allocates nothing. Each spine carries a changed flag, set by every write and cleared by the
 * save that writes it: a write after the clear sets it again, so the next save writes it.
 *
 * @param <T> the slot type, {@code byte[]} or {@code long[]}
 */
final class SpinedSlots<T> {

    /** A spine and whether it has changed since it was last written. */
    static final class Spine<T> {
        final AtomicReferenceArray<T> slots;
        volatile boolean changed;

        Spine(AtomicReferenceArray<T> slots) {
            this.slots = slots;
        }
    }

    /** What a slot holds, with its slot, for a walk. */
    @FunctionalInterface
    interface SlotConsumer<T> {
        void accept(int slot, T value);
    }

    private final SpineGeometry geometry;
    /** Reads a spine the store holds on disk; null for a memory-only store. */
    private final IntFunction<AtomicReferenceArray<T>> reader;
    /** The spines on disk not yet read. Read under {@link #growth}. */
    private final BitSet onDisk;
    private final Object growth = new Object();
    /** Whether any spine on disk is still unread: false lets a read of an absent spine skip the lock. */
    private volatile boolean unreadOnDisk;
    private volatile Spine<T>[] spines;

    @SuppressWarnings({"unchecked", "rawtypes"})
    SpinedSlots(SpineGeometry geometry, IntFunction<AtomicReferenceArray<T>> reader, BitSet onDisk) {
        this.geometry = geometry;
        this.reader = reader;
        this.onDisk = onDisk == null ? new BitSet() : onDisk;
        this.spines = new Spine[Math.max(1, this.onDisk.length())];
        this.unreadOnDisk = reader != null && !this.onDisk.isEmpty();
    }

    SpineGeometry geometry() {
        return geometry;
    }

    /** What a slot holds, or null. */
    T get(int slot) {
        Spine<T> spine = spineIfPresent(geometry.spineOf(slot));
        return spine == null ? null : spine.slots.get(geometry.offsetOf(slot));
    }

    /** Sets a slot, returning what it held. */
    T getAndSet(int slot, T value) {
        int index = geometry.spineOf(slot);
        if (value == null) {
            Spine<T> spine = spineIfPresent(index);
            if (spine == null) {
                return null;
            }
            T previous = spine.slots.getAndSet(geometry.offsetOf(slot), null);
            spine.changed = true;
            return previous;
        }
        Spine<T> spine = spine(index);
        T previous = spine.slots.getAndSet(geometry.offsetOf(slot), value);
        spine.changed = true;
        return previous;
    }

    /** Merges a value into a slot atomically. */
    T accumulateAndGet(int slot, T value, BinaryOperator<T> accumulator) {
        Spine<T> spine = spine(geometry.spineOf(slot));
        T merged = spine.slots.accumulateAndGet(geometry.offsetOf(slot), value, accumulator);
        spine.changed = true;
        return merged;
    }

    /** Updates a slot atomically; an update to null on a slot whose spine is absent allocates nothing. */
    T updateAndGet(int slot, java.util.function.UnaryOperator<T> update) {
        int index = geometry.spineOf(slot);
        Spine<T> spine = spineIfPresent(index);
        if (spine == null) {
            T made = update.apply(null);
            if (made == null) {
                return null;
            }
            spine = spine(index);
        }
        T updated = spine.slots.updateAndGet(geometry.offsetOf(slot), update);
        spine.changed = true;
        return updated;
    }

    /** Visits every slot that holds a value, spine by spine, in slot order. */
    void forEach(SlotConsumer<T> consumer) {
        int count = spineCount();
        for (int index = 0; index < count; index++) {
            forEachOnSpine(index, consumer);
        }
    }

    /**
     * Visits every slot that holds a value, giving only the slot: for walks that list nids and never
     * read a value. A slot is tested with a plain read, so a value written while the walk runs may or
     * may not be seen, as with the other enumerations; nothing is read through the reference, so no
     * ordering stronger than the reference's own atomicity is needed. The volatile reads of
     * {@link #forEach} cost a SNOMED-sized pattern enumeration about 1.7 times the sequential
     * engine's set iteration (IKE-Network/ike-issues#1313).
     */
    void forEachPresentSlot(java.util.function.IntConsumer consumer) {
        int count = spineCount();
        for (int index = 0; index < count; index++) {
            Spine<T> spine = spineIfPresent(index);
            if (spine == null) {
                continue;
            }
            int first = (int) geometry.firstSlotOf(index);
            AtomicReferenceArray<T> slots = spine.slots;
            int length = slots.length();
            for (int offset = 0; offset < length; offset++) {
                if (slots.getPlain(offset) != null) {
                    consumer.accept(first + offset);
                }
            }
        }
    }

    /** Visits the slots of one spine that hold a value. */
    void forEachOnSpine(int index, SlotConsumer<T> consumer) {
        Spine<T> spine = spineIfPresent(index);
        if (spine == null) {
            return;
        }
        int first = (int) geometry.firstSlotOf(index);
        AtomicReferenceArray<T> slots = spine.slots;
        for (int offset = 0; offset < slots.length(); offset++) {
            T value = slots.get(offset);
            if (value != null) {
                consumer.accept(first + offset, value);
            }
        }
    }

    /** One past the highest spine index this map has allocated or holds on disk. */
    int spineCount() {
        Spine<T>[] array = spines;
        int count = array.length;
        while (count > 0 && array[count - 1] == null) {
            count--;
        }
        if (!unreadOnDisk) {
            return count;
        }
        synchronized (growth) {
            return Math.max(count, onDisk.length());
        }
    }

    /** The slot after the last one holding a value; 0 for none. */
    int end() {
        for (int index = spineCount() - 1; index >= 0; index--) {
            Spine<T> spine = spineIfPresent(index);
            if (spine == null) {
                continue;
            }
            for (int offset = spine.slots.length() - 1; offset >= 0; offset--) {
                if (spine.slots.get(offset) != null) {
                    return (int) geometry.firstSlotOf(index) + offset + 1;
                }
            }
        }
        return 0;
    }

    /** The spines changed since the last {@link #takeChanged()}, their flags cleared; each with its index. */
    void takeChanged(java.util.function.ObjIntConsumer<AtomicReferenceArray<T>> writer) {
        Spine<T>[] array = spines;
        for (int index = 0; index < array.length; index++) {
            Spine<T> spine = array[index];
            if (spine != null && spine.changed) {
                spine.changed = false;
                writer.accept(spine.slots, index);
            }
        }
    }

    /** Drops every spine, in memory and the record of those on disk: the range is empty after. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    void clear() {
        synchronized (growth) {
            onDisk.clear();
            unreadOnDisk = false;
            spines = new Spine[1];
        }
    }

    private Spine<T> spineIfPresent(int index) {
        Spine<T>[] array = spines;
        if (index < array.length) {
            Spine<T> spine = array[index];
            if (spine != null) {
                return spine;
            }
        }
        if (!unreadOnDisk) {
            return null;
        }
        synchronized (growth) {
            array = spines;
            if (index < array.length && array[index] != null) {
                return array[index];
            }
            if (!onDisk.get(index)) {
                return null;
            }
        }
        // The file is read outside the lock, so the spines of one pattern load in parallel; the
        // first reader to finish installs its copy, and a later one takes it.
        AtomicReferenceArray<T> slots = reader.apply(index);
        if (slots != null && slots.length() != geometry.sizeOf(index)) {
            throw new IllegalStateException("spine " + index + " holds " + slots.length() + " slots; the store's geometry "
                    + geometry + " gives it " + geometry.sizeOf(index));
        }
        synchronized (growth) {
            array = spines;
            if (index < array.length && array[index] != null) {
                return array[index];
            }
            if (!onDisk.get(index)) {
                // Cleared while it was read: the range was dropped.
                return null;
            }
            onDisk.clear(index);
            unreadOnDisk = !onDisk.isEmpty();
            if (slots == null) {
                return null;
            }
            Spine<T> loaded = new Spine<>(slots);
            install(index, loaded);
            return loaded;
        }
    }

    private Spine<T> spine(int index) {
        Spine<T> present = spineIfPresent(index);
        if (present != null) {
            return present;
        }
        synchronized (growth) {
            Spine<T>[] array = spines;
            if (index < array.length && array[index] != null) {
                return array[index];
            }
            Spine<T> made = new Spine<>(new AtomicReferenceArray<>(geometry.sizeOf(index)));
            install(index, made);
            return made;
        }
    }

    /** Puts a spine in the directory, growing it by copying. Called holding {@link #growth}. */
    private void install(int index, Spine<T> spine) {
        Spine<T>[] array = spines;
        if (index >= array.length) {
            array = Arrays.copyOf(array, Math.max(index + 1, array.length * 2));
        }
        array[index] = spine;
        spines = array;
    }
}
