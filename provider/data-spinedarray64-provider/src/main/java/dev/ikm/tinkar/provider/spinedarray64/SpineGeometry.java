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

/**
 * How a pattern's slots are cut into spines: spine {@code k} holds {@code min · 2^k} slots until
 * that reaches {@code max}, and every spine after holds {@code max}. A pattern of one element
 * costs one spine of {@code min} slots, one of {@code n} elements in the geometric part at most
 * {@code 2n + min} slots, and a large one at most one partly filled spine of {@code max}
 * (design-2026-10-20-instance-data, "Spine sizing"; IKE-Network/ike-issues#1313).
 *
 * <p>A slot is an element sequence minus one, so slot 0 is element 1. The arithmetic is closed
 * form: the geometric part holds {@code 2·max − min} slots, and within it slot {@code i} is on
 * spine {@code floor(log2(i/min + 1))}.
 *
 * @param min the first spine's slots, a power of two
 * @param max the largest spine's slots, a power of two no less than {@code min}
 */
record SpineGeometry(int min, int max) {

    /** The defaults: {@code -Dspinedarray64.spine.min}, else 16. */
    static final int DEFAULT_MIN = Integer.getInteger("spinedarray64.spine.min", 16);
    /** The defaults: {@code -Dspinedarray64.spine.max}, else 16,384, the sequential engine's spine size within a factor of 1.6. */
    static final int DEFAULT_MAX = Integer.getInteger("spinedarray64.spine.max", 16_384);

    /** The largest maximum spine accepted, so the geometric part's slot count stays an {@code int}. */
    static final int LARGEST_MAX = 1 << 24;

    SpineGeometry {
        if (min < 1 || Integer.bitCount(min) != 1) {
            throw new IllegalArgumentException("the minimum spine size " + min + " is not a power of two");
        }
        if (max < min || max > LARGEST_MAX || Integer.bitCount(max) != 1) {
            throw new IllegalArgumentException("the maximum spine size " + max
                    + " is not a power of two from the minimum, " + min + ", through " + LARGEST_MAX);
        }
    }

    /** The geometry the system properties name, else the defaults. */
    static SpineGeometry defaults() {
        return new SpineGeometry(DEFAULT_MIN, DEFAULT_MAX);
    }

    /** The number of geometric spines, {@code min} through {@code max} slots. */
    int geometricSpines() {
        return Integer.numberOfTrailingZeros(max) - Integer.numberOfTrailingZeros(min) + 1;
    }

    /** The slots of the geometric part: {@code 2·max − min}. */
    int geometricSlots() {
        return 2 * max - min;
    }

    /** The spine a slot is on. */
    int spineOf(int slot) {
        if (slot < geometricSlots()) {
            return 31 - Integer.numberOfLeadingZeros(slot / min + 1);
        }
        return geometricSpines() + (slot - geometricSlots()) / max;
    }

    /** The slot's offset on its spine. */
    int offsetOf(int slot) {
        if (slot < geometricSlots()) {
            int spine = 31 - Integer.numberOfLeadingZeros(slot / min + 1);
            return slot - min * ((1 << spine) - 1);
        }
        return (slot - geometricSlots()) % max;
    }

    /** The slots a spine holds. */
    int sizeOf(int spine) {
        return spine < geometricSpines() ? min << spine : max;
    }

    /** The first slot of a spine. */
    long firstSlotOf(int spine) {
        if (spine < geometricSpines()) {
            return (long) min * ((1L << spine) - 1);
        }
        return geometricSlots() + (long) (spine - geometricSpines()) * max;
    }

    /** The number of spines that hold every slot below {@code slots}: one past the spine of the last. */
    int spinesFor(long slots) {
        return slots <= 0 ? 0 : spineOf((int) (slots - 1)) + 1;
    }
}
