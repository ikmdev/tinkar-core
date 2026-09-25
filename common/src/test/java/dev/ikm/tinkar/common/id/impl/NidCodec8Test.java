package dev.ikm.tinkar.common.id.impl;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Encode/decode, range, and forbidden-value tests for {@link NidCodec8}
 * (IKE-Network/ike-issues#1138).
 */
@DisplayName("NidCodec8 encode/decode tests")
class NidCodec8Test {

    private static final long MAX_ELEMENT = (1L << 24) - 1;

    @Nested
    @DisplayName("Round-trip encode/decode")
    class RoundTrip {
        @ParameterizedTest(name = "pattern={0}, element={1}")
        @CsvSource({
                "1,1",
                "1,2",
                "2,1",
                "5,123456",
                "63,1000000",
                "64,1",
                "127,1",
                "128,1",
                "200,16777215",
                "254,1670516",
                "255,1",
                "255,16777215",
                "10,16777215"
        })
        void roundTrip(int pattern, long element) {
            int nid = NidCodec8.encode(pattern, element);
            assertEquals(pattern, NidCodec8.decodePatternSequence(nid));
            assertEquals(element, NidCodec8.decodeElementSequence(nid));
            assertDoesNotThrow(() -> NidCodec8.validateNid(nid));
            assertEquals(nid, NidCodec8.nidForLongKey(NidCodec8.longKeyForNid(nid)));
        }

        @Test
        @DisplayName("Randomized sampling across the full ranges")
        void randomizedRoundTrip() {
            Random random = new Random(42);
            for (int i = 0; i < 100_000; i++) {
                int pattern = 1 + random.nextInt(255);
                long element = 1L + random.nextInt((int) MAX_ELEMENT);
                if (pattern == 127 && element == MAX_ELEMENT) {
                    element--;
                }
                int nid = NidCodec8.encode(pattern, element);
                assertEquals(pattern, NidCodec8.decodePatternSequence(nid));
                assertEquals(element, NidCodec8.decodeElementSequence(nid));
                NidCodec8.validateNid(nid);
            }
        }

        @Test
        @DisplayName("Pattern sequences 128..255 produce negative nids that still decode")
        void upperPatternsAreNegativeNids() {
            int nid = NidCodec8.encode(255, 7);
            assertTrue(nid < 0);
            assertEquals(255, NidCodec8.decodePatternSequence(nid));
            assertEquals(7, NidCodec8.decodeElementSequence(nid));
        }

        @Test
        @DisplayName("Long key keeps the 16/48 RocksDB layout")
        void longKeyLayout() {
            int nid = NidCodec8.encode(200, 12_345);
            assertEquals(KeyUtil.patternSequenceElementSequenceToLongKey(200, 12_345),
                    NidCodec8.longKeyForNid(nid));
        }
    }

    @Nested
    @DisplayName("Invalid ranges rejected")
    class InvalidRanges {
        @ParameterizedTest
        @ValueSource(ints = {0, -1, 256, 1000})
        void invalidPatternRejected(int pattern) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> NidCodec8.encode(pattern, 1));
            assertTrue(ex.getMessage().contains("patternSequence"));
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L, (1L << 24), (1L << 26) - 1, Long.MAX_VALUE})
        void invalidElementRejected(long element) {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> NidCodec8.encode(1, element));
            assertTrue(ex.getMessage().contains("elementSequence"));
        }

        @Test
        @DisplayName("Forbidden pair (127, 2^24 - 1) → Integer.MAX_VALUE is rejected")
        void forbiddenMaxValue() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> NidCodec8.encode(127, MAX_ELEMENT));
            assertTrue(ex.getMessage().contains("Forbidden"));
        }

        @Test
        @DisplayName("The full element range is available to every other pattern")
        void maxElementAllowedElsewhere() {
            assertDoesNotThrow(() -> NidCodec8.encode(126, MAX_ELEMENT));
            assertDoesNotThrow(() -> NidCodec8.encode(128, MAX_ELEMENT));
        }
    }

    @Nested
    @DisplayName("validateNid and constants")
    class ValidateChecks {
        @Test
        void validateRejectsForbiddenNids() {
            for (int bad : new int[]{0, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                assertThrows(IllegalArgumentException.class, () -> NidCodec8.validateNid(bad));
            }
        }

        @Test
        void validateRejectsZeroPattern() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> NidCodec8.validateNid(5));
            assertTrue(ex.getMessage().contains("patternSequence"));
        }

        @Test
        void validateRejectsZeroElement() {
            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> NidCodec8.validateNid(3 << 24));
            assertTrue(ex.getMessage().contains("elementSequence"));
        }

        @Test
        @DisplayName("MIN_VALUE is unreachable: (128, 1) is the smallest pattern-128 nid")
        void minValueUnreachable() {
            assertNotEquals(Integer.MIN_VALUE, NidCodec8.encode(128, 1));
        }

        @Test
        @DisplayName("Pattern-of-patterns is fixed at 255 and excluded from assignment")
        void patternPatternConstants() {
            assertEquals(255, NidCodec8.PATTERN_PATTERN_SEQUENCE);
            assertEquals(254, NidCodec8.MAX_ASSIGNABLE_PATTERN_SEQUENCE);
            assertEquals(255, NidCodec8.MAX_PATTERN_SEQUENCE);
            assertEquals(MAX_ELEMENT, NidCodec8.MAX_ELEMENT_SEQUENCE);
        }
    }
}
