package dev.ikm.tinkar.common.id.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Detection, constants, and codec routing for {@link NidLayout}
 * (IKE-Network/ike-issues#1138).
 */
class NidLayoutTest {

    @AfterEach
    void resetLayout() {
        NidLayout.activate(NidLayout.EIGHT_BIT);
    }

    @Test
    void detect_newDatabaseIsEightBit() {
        assertEquals(NidLayout.EIGHT_BIT, NidLayout.detect(Set.of()));
    }

    @Test
    void detect_counterAt255IsEightBit_evenWithAnOrdinaryPatternAt63() {
        assertEquals(NidLayout.EIGHT_BIT, NidLayout.detect(Set.of(2, 63, 255)));
    }

    @Test
    void detect_countersWithout255AreSixBit() {
        assertEquals(NidLayout.SIX_BIT, NidLayout.detect(List.of(1, 2, 7, 63)));
    }

    @Test
    void patternOfPatternsAndLimitsPerLayout() {
        assertEquals(63, NidLayout.SIX_BIT.patternPatternSequence());
        assertEquals(62, NidLayout.SIX_BIT.maxAssignablePatternSequence());
        assertEquals(255, NidLayout.EIGHT_BIT.patternPatternSequence());
        assertEquals(254, NidLayout.EIGHT_BIT.maxAssignablePatternSequence());
    }

    @Test
    void defaultActiveLayoutIsEightBit() {
        assertEquals(NidLayout.EIGHT_BIT, NidLayout.active());
    }

    @Test
    void eachLayoutRoutesToItsCodec() {
        assertEquals(NidCodec6.encode(5, 123_456), NidLayout.SIX_BIT.encode(5, 123_456));
        assertEquals(NidCodec8.encode(5, 123_456), NidLayout.EIGHT_BIT.encode(5, 123_456));
        assertNotEquals(NidLayout.SIX_BIT.encode(5, 123_456), NidLayout.EIGHT_BIT.encode(5, 123_456),
                "the layouts pack the same sequences differently — why a database has exactly one");
        for (NidLayout layout : NidLayout.values()) {
            int nid = layout.encode(40, 9_999);
            assertEquals(40, layout.decodePatternSequence(nid));
            assertEquals(9_999, layout.decodeElementSequence(nid));
            assertEquals(nid, layout.nidForLongKey(layout.longKeyForNid(nid)));
            layout.validateNid(nid);
        }
    }

    @Test
    void activateSwitchesTheProcessWideLayout() {
        NidLayout.activate(NidLayout.SIX_BIT);
        assertEquals(NidLayout.SIX_BIT, NidLayout.active());
        assertEquals(NidCodec6.encode(3, 7), NidLayout.active().encode(3, 7));
    }
}
