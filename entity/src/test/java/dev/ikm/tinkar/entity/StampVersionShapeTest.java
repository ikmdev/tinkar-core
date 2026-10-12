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
package dev.ikm.tinkar.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A stamp has at most two versions — the uncommitted placeholder and one committed or canceled
 * version — and the builder is where the write path refuses anything else. Reading a record from
 * before that rule, {@link StampEntity#lastVersion()} makes the store merge's choices: a committed
 * version over a canceled one, the earliest commit over later ones.
 */
class StampVersionShapeTest {

    private static final long NID = 11;
    private static final int ACTIVE = 21;
    private static final int CANCELED = 22;
    private static final int AUTHOR = 31;
    private static final int MODULE = 32;
    private static final int PATH = 33;
    private static final long COMMIT_TIME = 1_791_209_253_280L;

    @Test
    @DisplayName("The legal shapes build")
    void legalShapesBuild() {
        assertEquals(1, rebuild(stamp(new long[] {Long.MAX_VALUE}, new int[] {ACTIVE})).versions().size());
        assertEquals(1, rebuild(stamp(new long[] {COMMIT_TIME}, new int[] {ACTIVE})).versions().size());
        assertEquals(2, rebuild(stamp(new long[] {Long.MAX_VALUE, COMMIT_TIME}, new int[] {ACTIVE, ACTIVE})).versions().size());
        assertEquals(2, rebuild(stamp(new long[] {Long.MAX_VALUE, Long.MIN_VALUE}, new int[] {ACTIVE, CANCELED})).versions().size());
    }

    @Test
    @DisplayName("A third version, a second terminal, or a second placeholder is refused")
    void illegalShapesAreRefused() {
        StampRecord committed = stamp(new long[] {Long.MAX_VALUE, COMMIT_TIME}, new int[] {ACTIVE, ACTIVE});
        IllegalStateException cancelAfterCommit = assertThrows(IllegalStateException.class, () ->
                committed.analogueBuilder().add(new StampVersionRecord(committed, CANCELED, Long.MIN_VALUE, AUTHOR, MODULE, PATH)).build());
        assertTrue(cancelAfterCommit.getMessage().contains("at most two"), cancelAfterCommit.getMessage());

        StampRecord once = stamp(new long[] {COMMIT_TIME}, new int[] {ACTIVE});
        assertThrows(IllegalStateException.class, () ->
                once.analogueBuilder().add(new StampVersionRecord(once, ACTIVE, COMMIT_TIME + 1, AUTHOR, MODULE, PATH)).build());

        StampRecord pending = stamp(new long[] {Long.MAX_VALUE}, new int[] {ACTIVE});
        assertThrows(IllegalStateException.class, () ->
                pending.analogueBuilder().add(new StampVersionRecord(pending, ACTIVE, Long.MAX_VALUE, AUTHOR, MODULE, PATH)).build());
    }

    @Test
    @DisplayName("lastVersion: committed beats canceled, the earliest commit wins, else canceled, else the placeholder")
    void lastVersionMakesTheMergesChoices() {
        assertEquals(COMMIT_TIME, stamp(new long[] {Long.MAX_VALUE, COMMIT_TIME}, new int[] {ACTIVE, ACTIVE}).lastVersion().time());
        assertEquals(Long.MIN_VALUE, stamp(new long[] {Long.MAX_VALUE, Long.MIN_VALUE}, new int[] {ACTIVE, CANCELED}).lastVersion().time());
        assertEquals(Long.MAX_VALUE, stamp(new long[] {Long.MAX_VALUE}, new int[] {ACTIVE}).lastVersion().time());
        // Records from before the shape rule: the reader heals as the merge does.
        assertEquals(COMMIT_TIME, stamp(new long[] {Long.MAX_VALUE, COMMIT_TIME, Long.MIN_VALUE}, new int[] {ACTIVE, ACTIVE, CANCELED}).lastVersion().time());
        assertEquals(COMMIT_TIME, stamp(new long[] {COMMIT_TIME + 5, Long.MAX_VALUE, COMMIT_TIME}, new int[] {ACTIVE, ACTIVE, ACTIVE}).lastVersion().time());
    }

    private static StampRecord rebuild(StampRecord stamp) {
        return stamp.analogueBuilder().build();
    }

    /** Built straight from the record list, as the codec builds a stored record, so illegal shapes can be set up. */
    private static StampRecord stamp(long[] times, int[] states) {
        RecordListBuilder<StampVersionRecord> versions = RecordListBuilder.make();
        StampRecord stamp = new StampRecord(0x79664309_2762_564bL, 0xa995_98a1b07ec9cbL, null, NID, versions);
        for (int i = 0; i < times.length; i++) {
            versions.add(new StampVersionRecord(stamp, states[i], times[i], AUTHOR, MODULE, PATH));
        }
        versions.build();
        return stamp;
    }
}
