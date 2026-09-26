package dev.ikm.tinkar.common.id.impl;
// Java
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Random;

@DisplayName("NidCodec6 encode/decode tests")
class NidCodec6Test {

    @Nested
    @DisplayName("Round-trip encode/decode")
    class RoundTrip {
        @ParameterizedTest(name = "pattern={0}, element={1}")
        @CsvSource({
                // Typical values
                "1,1",
                "1,2",
                "2,1",
                "5,123456",
                "63,1000000",
                // Boundaries around element sequence for new encoding (no -1; element ∈ [1 .. 2^26-1])
                "10,1",
                "10,2",
                "10,67108863",
                // Boundaries around pattern sequence
                "1,100",
                "63,100"
        })
        void roundTrip(int pattern, long element) {
            int nid = NidCodec6.encode(pattern, element);
            Assertions.assertEquals(pattern, NidCodec6.decodePatternSequence(nid));
            Assertions.assertEquals(element, NidCodec6.decodeElementSequence(nid));
            Assertions.assertDoesNotThrow(() -> NidCodec6.validateNid(nid));
        }

        @Test
        @DisplayName("Randomized sampling within ranges")
        void randomizedRoundTrip() {
            Random r = new Random(42);
            for (int i = 0; i < 10_000; i++) {
                int pattern = 1 + r.nextInt(63); // [1,63]
                long element = 1L + (r.nextInt(1 << 20) | ((long) r.nextInt(1 << 6) << 20));
                // Ensure element within [1, 2^26-1] with direct packing
                element = 1L + (element - 1) % ((1L << 26) - 1);

                // Avoid the single forbidden upper-edge pair that would map to Integer.MAX_VALUE
                if (pattern == 31 && element == ((1L << 26) - 1)) {
                    element--; // move inside range
                }

                int nid = NidCodec6.encode(pattern, element);
                Assertions.assertEquals(pattern, NidCodec6.decodePatternSequence(nid));
                Assertions.assertEquals(element, NidCodec6.decodeElementSequence(nid));
                NidCodec6.validateNid(nid);
            }
        }
    }

    @Nested
    @DisplayName("Invalid ranges rejected")
    class InvalidRanges {
        @ParameterizedTest
        @ValueSource(ints = {0, -1, 64, 100})
        void invalidPatternRejected(int pattern) {
            IllegalArgumentException ex = Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> NidCodec6.encode(pattern, 1)
            );
            Assertions.assertTrue(ex.getMessage().contains("patternSequence"));
        }

        @ParameterizedTest
        @ValueSource(longs = {0L, -1L, (1L << 26), Long.MAX_VALUE})
        void invalidElementRejected(long element) {
            IllegalArgumentException ex = Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> NidCodec6.encode(1, element)
            );
            Assertions.assertTrue(ex.getMessage().contains("elementSequence"));
        }

        @Test
        @DisplayName("Forbidden pair (31, 2^26 - 1) → Integer.MAX_VALUE is rejected")
        void forbiddenMaxValue() {
            IllegalArgumentException ex = Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> NidCodec6.encode(31, (1L << 26) - 1)
            );
            Assertions.assertTrue(ex.getMessage().contains("Forbidden"));
        }
    }

    @Nested
    @DisplayName("validateNid checks")
    class ValidateChecks {
        @Test
        void validateRejectsForbiddenNids() {
            for (int bad : new int[]{0, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                IllegalArgumentException ex = Assertions.assertThrows(
                        IllegalArgumentException.class,
                        () -> NidCodec6.validateNid(bad)
                );
                Assertions.assertTrue(
                        ex.getMessage().contains("Forbidden nid value")
                                || ex.getMessage().contains("out of range")
                );
            }
        }

        @Test
        void validateRejectsZeroPattern() {
            // Construct a nid with pattern=0, elementIndex arbitrary (e.g., 5)
            int nid = 5; // lower 26 bits only, pattern bits are zero
            IllegalArgumentException ex = Assertions.assertThrows(
                    IllegalArgumentException.class,
                    () -> NidCodec6.validateNid(nid)
            );
            Assertions.assertTrue(ex.getMessage().contains("patternSequence"));
        }

        @Test
        void validateAcceptsValidNid() {
            int nid = NidCodec6.encode(7, 12345);
            Assertions.assertDoesNotThrow(() -> NidCodec6.validateNid(nid));
        }

        @Test
        @DisplayName("MIN_VALUE cannot be produced under new constraints")
        void minValueUnreachable() {
            // Ensure no valid (pattern, element) encodes to Integer.MIN_VALUE
            int pattern = 32;
            long element = 1; // minimal allowed
            int nid = NidCodec6.encode(pattern, element);
            Assertions.assertNotEquals(Integer.MIN_VALUE, nid);
        }
    }
}