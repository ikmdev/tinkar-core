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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * A committed stamp holds the uncommitted version its commit superseded; the committed
 * form an export writes leaves that version out, and leaves every other stamp as it is.
 */
class StampRecordCommittedFormTest {

    private static final long NID = 11;
    private static final int ACTIVE = 21;
    private static final int CANCELED = 22;
    private static final int AUTHOR = 31;
    private static final int MODULE = 32;
    private static final int PATH = 33;
    private static final long COMMIT_TIME = 1_791_209_253_280L;

    @Test
    @DisplayName("A committed stamp's committed form holds only its committed version")
    void committedStampDropsSupersededUncommittedVersion() {
        StampRecord stamp = stamp(new long[] {Long.MAX_VALUE, COMMIT_TIME}, new int[] {ACTIVE, ACTIVE});
        StampRecord committed = stamp.withoutSupersededUncommittedVersions();
        assertEquals(List.of(COMMIT_TIME), times(committed));
        assertEquals(stamp.lastVersion().time(), committed.lastVersion().time());
        assertEquals(stamp.nid(), committed.nid());
        assertEquals(stamp.publicId().asUuidList(), committed.publicId().asUuidList());
        StampVersionRecord version = committed.versions().getFirst();
        assertSame(committed, version.chronology());
        assertEquals(ACTIVE, version.stateNid());
        assertEquals(AUTHOR, version.authorNid());
        assertEquals(MODULE, version.moduleNid());
        assertEquals(PATH, version.pathNid());
    }

    @Test
    @DisplayName("An uncommitted stamp is work in progress, kept as it is")
    void uncommittedStampIsKept() {
        StampRecord stamp = stamp(new long[] {Long.MAX_VALUE}, new int[] {ACTIVE});
        assertSame(stamp, stamp.withoutSupersededUncommittedVersions());
    }

    @Test
    @DisplayName("A canceled stamp keeps the version that says what was canceled")
    void canceledStampIsKept() {
        StampRecord stamp = stamp(new long[] {Long.MAX_VALUE, Long.MIN_VALUE}, new int[] {ACTIVE, CANCELED});
        assertSame(stamp, stamp.withoutSupersededUncommittedVersions());
    }

    @Test
    @DisplayName("A stamp stamped at its time from the start is kept as it is")
    void stampWithoutUncommittedVersionIsKept() {
        StampRecord stamp = stamp(new long[] {COMMIT_TIME}, new int[] {ACTIVE});
        assertSame(stamp, stamp.withoutSupersededUncommittedVersions());
    }

    private static StampRecord stamp(long[] times, int[] states) {
        RecordListBuilder<StampVersionRecord> versions = RecordListBuilder.make();
        StampRecord stamp = new StampRecord(0x79664309_2762_564bL, 0xa995_98a1b07ec9cbL, null, NID, versions);
        for (int i = 0; i < times.length; i++) {
            versions.add(new StampVersionRecord(stamp, states[i], times[i], AUTHOR, MODULE, PATH));
        }
        versions.build();
        return stamp;
    }

    private static List<Long> times(StampRecord stamp) {
        return stamp.versions().collect(StampVersionRecord::time).castToList();
    }
}
