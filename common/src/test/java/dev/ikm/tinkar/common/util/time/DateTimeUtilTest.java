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
package dev.ikm.tinkar.common.util.time;

import dev.ikm.tinkar.common.service.PrimitiveData;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static dev.ikm.tinkar.common.util.time.DateTimeUtil.CANCELED;
import static dev.ikm.tinkar.common.util.time.DateTimeUtil.INCEPTION;
import static dev.ikm.tinkar.common.util.time.DateTimeUtil.LATEST;
import static dev.ikm.tinkar.common.util.time.DateTimeUtil.PREMUNDANE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class DateTimeUtilTest {

    @Test
    void parse() {
        assertEquals(Long.MAX_VALUE, DateTimeUtil.parse(LATEST));
        assertEquals(Long.MIN_VALUE, DateTimeUtil.parse(CANCELED));
        assertEquals(PrimitiveData.PREMUNDANE_TIME, DateTimeUtil.parse(PREMUNDANE));
        assertEquals(PrimitiveData.INCEPTION_EPOCH, DateTimeUtil.parse(INCEPTION));
    }


    @Test
    void format() {
        assertEquals(LATEST, DateTimeUtil.format(Instant.MAX));
        assertEquals(CANCELED, DateTimeUtil.format(Instant.MIN));
        assertEquals(PREMUNDANE, DateTimeUtil.format(PrimitiveData.PREMUNDANE_INSTANT));
        assertEquals(INCEPTION, DateTimeUtil.format(PrimitiveData.INCEPTION_INSTANT));

        assertEquals(LATEST, DateTimeUtil.format(Long.MAX_VALUE));
        assertEquals(CANCELED, DateTimeUtil.format(Long.MIN_VALUE));
        assertEquals(PREMUNDANE, DateTimeUtil.format(PrimitiveData.PREMUNDANE_TIME));
        assertEquals(INCEPTION, DateTimeUtil.format(PrimitiveData.INCEPTION_EPOCH));
    }

    @Test
    void zoneExplicitRendering() {
        // A real time renders differently across zones through the zone-explicit form...
        long epoch = Instant.parse("2027-03-05T04:30:00Z").toEpochMilli();
        assertEquals("2027-03-05 04:30",
                DateTimeUtil.format(epoch, DateTimeUtil.FORMATTER, java.time.ZoneOffset.UTC));
        assertEquals("2027-03-04 20:30",
                DateTimeUtil.format(epoch, DateTimeUtil.FORMATTER, java.time.ZoneId.of("America/Los_Angeles")));
        assertEquals("2027-03-05 04:30", DateTimeUtil.formatUtc(epoch),
                "formatUtc is the machine-independent form for generated artifacts"
                        + " (IKE-Network/ike-issues#897)");

        // ...while the sentinel words are zone-invariant on every path.
        assertEquals(INCEPTION, DateTimeUtil.formatUtc(PrimitiveData.INCEPTION_EPOCH));
        assertEquals(PREMUNDANE, DateTimeUtil.formatUtc(PrimitiveData.PREMUNDANE_TIME));
        assertEquals(LATEST, DateTimeUtil.formatUtc(Long.MAX_VALUE));
        assertEquals(CANCELED, DateTimeUtil.formatUtc(Long.MIN_VALUE));
        assertEquals(INCEPTION,
                DateTimeUtil.format(PrimitiveData.INCEPTION_INSTANT, DateTimeUtil.SEC_FORMATTER),
                "the Instant/formatter path carries the inception sentinel like every other path");
    }

    @Test
    void inceptionEpochIsAMillisecondAfterMidnight() {
        // The .777 offset is deliberate: a real calendar date landing exactly at
        // 2026-01-01T00:00:00.000Z must never collide with the inception sentinel.
        assertEquals(1767225600777L, PrimitiveData.INCEPTION_EPOCH);
        assertEquals(Instant.parse("2026-01-01T00:00:00.777Z"), PrimitiveData.INCEPTION_INSTANT);
        assertNotEquals(Instant.parse("2026-01-01T00:00:00.000Z"), PrimitiveData.INCEPTION_INSTANT);
    }
}