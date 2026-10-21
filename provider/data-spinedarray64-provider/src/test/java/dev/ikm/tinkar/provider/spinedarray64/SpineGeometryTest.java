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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The spine arithmetic: every slot on exactly one spine, at one offset, in slot order. */
class SpineGeometryTest {

    @Test
    void everySlotHasOneSpineAndOffsetInOrder() {
        for (SpineGeometry geometry : new SpineGeometry[]{new SpineGeometry(16, 16_384), new SpineGeometry(1, 8),
                new SpineGeometry(4, 4), new SpineGeometry(2, 64)}) {
            int spine = 0;
            int offset = 0;
            int slots = geometry.geometricSlots() + 5 * geometry.max();
            for (int slot = 0; slot < slots; slot++) {
                assertEquals(spine, geometry.spineOf(slot), geometry + " slot " + slot);
                assertEquals(offset, geometry.offsetOf(slot), geometry + " slot " + slot);
                assertEquals(slot, geometry.firstSlotOf(spine) + offset, geometry + " slot " + slot);
                offset++;
                if (offset == geometry.sizeOf(spine)) {
                    spine++;
                    offset = 0;
                }
            }
        }
    }

    @Test
    void spinesGrowFromTheMinimumToTheMaximumAndStay() {
        SpineGeometry geometry = new SpineGeometry(16, 16_384);
        assertEquals(11, geometry.geometricSpines());
        assertEquals(16, geometry.sizeOf(0));
        assertEquals(32, geometry.sizeOf(1));
        assertEquals(16_384, geometry.sizeOf(10));
        assertEquals(16_384, geometry.sizeOf(11));
        assertEquals(2 * 16_384 - 16, geometry.geometricSlots());
        assertEquals(1, geometry.spinesFor(1));
        assertEquals(0, geometry.spinesFor(0));
    }

    @Test
    void theLastElementSequenceFits() {
        SpineGeometry geometry = new SpineGeometry(16, 16_384);
        int last = Integer.MAX_VALUE - 2;           // element sequence MAX_SEQUENCE_64, minus one
        int spine = geometry.spineOf(last);
        assertTrue(geometry.offsetOf(last) < geometry.sizeOf(spine));
        assertEquals(last, geometry.firstSlotOf(spine) + geometry.offsetOf(last));
    }

    @Test
    void sizesArePowersOfTwo() {
        assertThrows(IllegalArgumentException.class, () -> new SpineGeometry(12, 16_384));
        assertThrows(IllegalArgumentException.class, () -> new SpineGeometry(32, 16));
        assertThrows(IllegalArgumentException.class, () -> new SpineGeometry(16, 1 << 25));
    }
}
