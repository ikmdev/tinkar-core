package dev.ikm.tinkar.common.id.impl;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
        for (NidLayout layout : List.of(NidLayout.SIX_BIT, NidLayout.EIGHT_BIT)) {
            long nid = layout.encode(40, 9_999);
            assertEquals(40, layout.decodePatternSequence(nid));
            assertEquals(9_999, layout.decodeElementSequence(nid));
            assertEquals(nid, layout.nidForRocksKey(layout.rocksKeyForNid(nid)));
            layout.validateNid(nid);
        }
    }

    @Test
    void sequentialNidsCarryNoPattern() {
        NidLayout layout = NidLayout.SEQUENTIAL;
        int firstNid = Integer.MIN_VALUE + 1;
        for (int nid : new int[]{firstNid, firstNid + 41, -1, 1, 12_345, Integer.MAX_VALUE - 1}) {
            assertEquals(0, layout.decodePatternSequence(nid));
            assertEquals(((long) nid) - Integer.MIN_VALUE, layout.decodeElementSequence(nid));
            assertEquals(nid, layout.encode(0, layout.decodeElementSequence(nid)));
            assertEquals(nid, layout.nidForRocksKey(layout.rocksKeyForNid(nid)));
            layout.validateNid(nid);
        }
        assertEquals(1, layout.decodeElementSequence(firstNid));
    }

    @Test
    void sequentialAgreesWithTheSequentialEntityKey() {
        int nid = Integer.MIN_VALUE + 1_000;
        dev.ikm.tinkar.common.id.EntityKey key = dev.ikm.tinkar.common.id.EntityKey.ofSequentialNid(nid);
        assertEquals(key.patternSequence(), NidLayout.SEQUENTIAL.decodePatternSequence(nid));
        assertEquals(key.elementSequence(), NidLayout.SEQUENTIAL.decodeElementSequence(nid));
        assertEquals(key.rocksKey(), NidLayout.SEQUENTIAL.rocksKeyForNid(nid));
    }

    @Test
    void sequentialRefusesPatternsAndSentinels() {
        assertThrows(IllegalArgumentException.class, () -> NidLayout.SEQUENTIAL.encode(1, 1));
        assertThrows(IllegalArgumentException.class, () -> NidLayout.SEQUENTIAL.encode(0, 0));
        assertThrows(IllegalArgumentException.class, () -> NidLayout.SEQUENTIAL.validateNid(0));
        assertThrows(IllegalArgumentException.class, () -> NidLayout.SEQUENTIAL.validateNid(Integer.MIN_VALUE));
        assertThrows(IllegalArgumentException.class, () -> NidLayout.SEQUENTIAL.validateNid(Integer.MAX_VALUE));
        assertThrows(UnsupportedOperationException.class, NidLayout.SEQUENTIAL::patternPatternSequence);
        assertThrows(UnsupportedOperationException.class, NidLayout.SEQUENTIAL::maxAssignablePatternSequence);
    }

    @Test
    void theLegacyLayoutsNameEntityFormat1AndTheSixtyFourBitLayoutFormat2() {
        for (NidLayout layout : List.of(NidLayout.SIX_BIT, NidLayout.EIGHT_BIT, NidLayout.SEQUENTIAL)) {
            assertEquals(NidLayout.ENTITY_FORMAT_1, layout.entityFormat(), layout.displayName());
            assertEquals(layout == NidLayout.SEQUENTIAL ? 0 : 2, layout.patternPrefixLength(), layout.displayName());
        }
        assertEquals(NidLayout.ENTITY_FORMAT_2, NidLayout.SIXTY_FOUR_BIT.entityFormat());
        assertEquals(4, NidLayout.SIXTY_FOUR_BIT.patternPrefixLength());
    }

    /** The 64-bit layout: the nid is the key, both halves 31 bits, the pattern-of-patterns at 1 (IKE-Network/ike-issues#1258). */
    @Test
    void theSixtyFourBitLayoutKeysByTheNid() {
        NidLayout layout = NidLayout.SIXTY_FOUR_BIT;
        long nid = layout.encode(300, 20_000_000L);
        assertEquals(dev.ikm.tinkar.common.id.Nid.compose64(300, 20_000_000), nid);
        assertEquals(300, layout.decodePatternSequence(nid));
        assertEquals(20_000_000L, layout.decodeElementSequence(nid));
        assertEquals(nid, layout.rocksKeyForNid(nid), "the key is the nid");
        assertEquals(nid, layout.nidForRocksKey(nid));
        assertEquals(nid, layout.rocksKey(300, 20_000_000L));
        assertEquals(300, layout.patternSequenceOfRocksKey(nid));
        assertEquals(20_000_000L, layout.elementSequenceOfRocksKey(nid));
        layout.validateNid(nid);
        assertEquals(1, layout.patternPatternSequence());
        assertEquals(dev.ikm.tinkar.common.id.Nid.MAX_SEQUENCE_64, layout.maxAssignablePatternSequence());
        assertEquals(dev.ikm.tinkar.common.id.Nid.MAX_SEQUENCE_64, layout.maxElementSequence());
        assertThrows(IllegalArgumentException.class, () -> layout.encode(0, 1));
        assertThrows(IllegalArgumentException.class, () -> layout.encode(1, 0));
        assertThrows(IllegalArgumentException.class, () -> layout.encode(1, Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> layout.encode(1, 1L << 32));
        assertThrows(IllegalArgumentException.class, () -> layout.validateNid(12_345L));

        NidLayout.activate(layout);
        dev.ikm.tinkar.common.id.EntityKey key = dev.ikm.tinkar.common.id.EntityKey.of(300, 20_000_000L);
        assertEquals(nid, key.nid());
        assertEquals(nid, key.rocksKey());
        assertEquals(key, dev.ikm.tinkar.common.id.EntityKey.ofRocksKey(nid));
        assertEquals(key, dev.ikm.tinkar.common.id.EntityKey.ofNid(nid));
        assertArrayEquals(KeyUtil.longToByteArray(nid), key.key());
    }

    /** A legacy key's bytes are the same whether packed by the layout or by the 16/48 helper. */
    @Test
    void theLegacyKeyBytesDoNotChange() {
        for (NidLayout layout : List.of(NidLayout.SIX_BIT, NidLayout.EIGHT_BIT)) {
            NidLayout.activate(layout);
            dev.ikm.tinkar.common.id.EntityKey key = dev.ikm.tinkar.common.id.EntityKey.of(40, 9_999);
            assertEquals(KeyUtil.patternSequenceElementSequenceToRocksKey(40, 9_999), key.rocksKey());
            assertArrayEquals(KeyUtil.patternSequenceElementSequenceToKey(40, 9_999), key.key());
            assertEquals(key, dev.ikm.tinkar.common.id.EntityKey.ofRocksKey(key.rocksKey()));
        }
    }

    @Test
    void activateSwitchesTheProcessWideLayout() {
        NidLayout.activate(NidLayout.SIX_BIT);
        assertEquals(NidLayout.SIX_BIT, NidLayout.active());
        assertEquals(NidCodec6.encode(3, 7), NidLayout.active().encode(3, 7));
    }
}
