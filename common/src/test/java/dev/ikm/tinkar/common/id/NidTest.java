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
package dev.ikm.tinkar.common.id;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class NidTest {

    // Valid NID test values
    private static final int VALID_NID_POSITIVE = 12345;
    private static final int VALID_NID_NEGATIVE = -12345;
    private static final int VALID_NID_SMALL_POSITIVE = 1;
    private static final int VALID_NID_SMALL_NEGATIVE = -1;

    // Reserved/invalid NID values
    private static final int INVALID_NID_ZERO = 0;
    private static final int INVALID_NID_MAX = Integer.MAX_VALUE;
    private static final int INVALID_NID_MIN = Integer.MIN_VALUE;

    // ========== Tests for validate(int) ==========

    @Test
    public void testValidateWithValidPositiveNid() {
        int result = Nid.validate(VALID_NID_POSITIVE);
        assertEquals(VALID_NID_POSITIVE, result);
    }

    @Test
    public void testValidateWithValidNegativeNid() {
        int result = Nid.validate(VALID_NID_NEGATIVE);
        assertEquals(VALID_NID_NEGATIVE, result);
    }

    @Test
    public void testValidateWithValidSmallPositiveNid() {
        int result = Nid.validate(VALID_NID_SMALL_POSITIVE);
        assertEquals(VALID_NID_SMALL_POSITIVE, result);
    }

    @Test
    public void testValidateWithValidSmallNegativeNid() {
        int result = Nid.validate(VALID_NID_SMALL_NEGATIVE);
        assertEquals(VALID_NID_SMALL_NEGATIVE, result);
    }

    @Test
    public void testValidateThrowsExceptionForZero() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Nid.validate(INVALID_NID_ZERO)
        );
        assertTrue(exception.getMessage().contains("cannot be 0"));
        assertTrue(exception.getMessage().contains("reserved value"));
    }

    @Test
    public void testValidateThrowsExceptionForMaxValue() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Nid.validate(INVALID_NID_MAX)
        );
        assertTrue(exception.getMessage().contains("Integer.MAX_VALUE"));
        assertTrue(exception.getMessage().contains("reserved sentinel value"));
    }

    @Test
    public void testValidateThrowsExceptionForMinValue() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Nid.validate(INVALID_NID_MIN)
        );
        assertTrue(exception.getMessage().contains("Integer.MIN_VALUE"));
        assertTrue(exception.getMessage().contains("reserved sentinel value"));
    }

    @Test
    public void testValidateCanBeUsedInline() {
        // Demonstrates the idiomatic usage pattern
        int meaningNid = Nid.validate(VALID_NID_POSITIVE);
        assertEquals(VALID_NID_POSITIVE, meaningNid);
    }

    // ========== Tests for validate(int, String) ==========

    @Test
    public void testValidateWithContextValidNid() {
        String context = "Field meaning NID";
        int result = Nid.validate(VALID_NID_POSITIVE, context);
        assertEquals(VALID_NID_POSITIVE, result);
    }

    @Test
    public void testValidateWithContextThrowsExceptionForZero() {
        String context = "Concept NID";
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Nid.validate(INVALID_NID_ZERO, context)
        );
        assertTrue(exception.getMessage().contains(context));
        assertTrue(exception.getMessage().contains("cannot be 0"));
    }

    @Test
    public void testValidateWithContextThrowsExceptionForMaxValue() {
        String context = "Pattern NID";
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Nid.validate(INVALID_NID_MAX, context)
        );
        assertTrue(exception.getMessage().contains(context));
        assertTrue(exception.getMessage().contains("Integer.MAX_VALUE"));
    }

    @Test
    public void testValidateWithContextThrowsExceptionForMinValue() {
        String context = "Semantic NID";
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> Nid.validate(INVALID_NID_MIN, context)
        );
        assertTrue(exception.getMessage().contains(context));
        assertTrue(exception.getMessage().contains("Integer.MIN_VALUE"));
    }

    @Test
    public void testValidateWithContextCanBeUsedInline() {
        // Demonstrates the idiomatic usage pattern with context
        int meaningNid = Nid.validate(VALID_NID_NEGATIVE, "Field meaning NID");
        assertEquals(VALID_NID_NEGATIVE, meaningNid);
    }

    // ========== Tests for isValid(int) ==========

    @Test
    public void testIsValidReturnsTrueForValidPositiveNid() {
        assertTrue(Nid.isValid(VALID_NID_POSITIVE));
    }

    @Test
    public void testIsValidReturnsTrueForValidNegativeNid() {
        assertTrue(Nid.isValid(VALID_NID_NEGATIVE));
    }

    @Test
    public void testIsValidReturnsTrueForSmallPositiveNid() {
        assertTrue(Nid.isValid(VALID_NID_SMALL_POSITIVE));
    }

    @Test
    public void testIsValidReturnsTrueForSmallNegativeNid() {
        assertTrue(Nid.isValid(VALID_NID_SMALL_NEGATIVE));
    }

    @Test
    public void testIsValidReturnsFalseForZero() {
        assertFalse(Nid.isValid(INVALID_NID_ZERO));
    }

    @Test
    public void testIsValidReturnsFalseForMaxValue() {
        assertFalse(Nid.isValid(INVALID_NID_MAX));
    }

    @Test
    public void testIsValidReturnsFalseForMinValue() {
        assertFalse(Nid.isValid(INVALID_NID_MIN));
    }

    // ========== Tests for boundary values ==========

    @Test
    public void testBoundaryValueJustAboveZero() {
        assertTrue(Nid.isValid(1));
        assertEquals(1, Nid.validate(1));
    }

    @Test
    public void testBoundaryValueJustBelowZero() {
        assertTrue(Nid.isValid(-1));
        assertEquals(-1, Nid.validate(-1));
    }

    @Test
    public void testBoundaryValueJustBelowMaxValue() {
        int justBelowMax = Integer.MAX_VALUE - 1;
        assertTrue(Nid.isValid(justBelowMax));
        assertEquals(justBelowMax, Nid.validate(justBelowMax));
    }

    @Test
    public void testBoundaryValueJustAboveMinValue() {
        int justAboveMin = Integer.MIN_VALUE + 1;
        assertTrue(Nid.isValid(justAboveMin));
        assertEquals(justAboveMin, Nid.validate(justAboveMin));
    }

    // ========== Tests for stream operations ==========

    @Test
    public void testFilterValidNidsInStream() {
        List<Integer> nids = List.of(
                VALID_NID_POSITIVE,
                INVALID_NID_ZERO,
                VALID_NID_NEGATIVE,
                INVALID_NID_MAX,
                VALID_NID_SMALL_POSITIVE,
                INVALID_NID_MIN
        );

        List<Integer> validNids = nids.stream()
                .filter(Nid::isValid)
                .toList();

        assertEquals(3, validNids.size());
        assertTrue(validNids.contains(VALID_NID_POSITIVE));
        assertTrue(validNids.contains(VALID_NID_NEGATIVE));
        assertTrue(validNids.contains(VALID_NID_SMALL_POSITIVE));
    }

    @Test
    public void testCountValidNidsInStream() {
        long validCount = Stream.of(1, 0, -1, Integer.MAX_VALUE, 100, Integer.MIN_VALUE)
                .filter(Nid::isValid)
                .count();

        assertEquals(3, validCount);
    }

    @Test
    public void testValidateInStreamMapping() {
        List<Integer> validNids = List.of(1, 2, 3, 4, 5);

        List<Integer> validatedNids = validNids.stream()
                .map(Nid::validate)
                .toList();

        assertEquals(validNids, validatedNids);
    }

    @Test
    public void testStreamWithInvalidNidsThrowsException() {
        List<Integer> nidsWithInvalid = List.of(1, 2, 0, 4, 5);

        assertThrows(IllegalArgumentException.class, () -> {
            nidsWithInvalid.stream()
                    .map(Nid::validate)
                    .toList();
        });
    }

    // ========== Tests for practical usage scenarios ==========

    @Test
    public void testValidateAllReservedValuesThrowExceptions() {
        // Ensure all three reserved values throw exceptions
        assertThrows(IllegalArgumentException.class, () -> Nid.validate(0));
        assertThrows(IllegalArgumentException.class, () -> Nid.validate(Integer.MAX_VALUE));
        assertThrows(IllegalArgumentException.class, () -> Nid.validate(Integer.MIN_VALUE));
    }

    @Test
    public void testIsValidForAllReservedValues() {
        // Ensure all three reserved values return false
        assertFalse(Nid.isValid(0));
        assertFalse(Nid.isValid(Integer.MAX_VALUE));
        assertFalse(Nid.isValid(Integer.MIN_VALUE));
    }

    @Test
    public void testValidateAndIsValidConsistency() {
        // For any valid NID, validate() should not throw and isValid() should return true
        int[] validNids = {1, -1, 100, -100, 12345, -12345, Integer.MAX_VALUE - 1, Integer.MIN_VALUE + 1};

        for (int nid : validNids) {
            assertTrue(Nid.isValid(nid), "isValid should return true for: " + nid);
            assertDoesNotThrow(() -> Nid.validate(nid), "validate should not throw for: " + nid);
            assertEquals(nid, Nid.validate(nid), "validate should return the same value for: " + nid);
        }
    }

    @Test
    public void testValidateAndIsValidConsistencyForInvalidNids() {
        // For any invalid NID, validate() should throw and isValid() should return false
        int[] invalidNids = {0, Integer.MAX_VALUE, Integer.MIN_VALUE};

        for (int nid : invalidNids) {
            assertFalse(Nid.isValid(nid), "isValid should return false for: " + nid);
            assertThrows(IllegalArgumentException.class, () -> Nid.validate(nid),
                    "validate should throw for: " + nid);
        }
    }

    // ========== Sentinels ==========

    @Test
    public void sentinelsKeepTheirIntValuesWidened() {
        assertEquals(0, Nid.UNSET);
        assertEquals(Integer.MIN_VALUE, Nid.NONE);
        assertEquals(Integer.MAX_VALUE, Nid.NOT_APPLICABLE);
        assertEquals(-1, Nid.NOT_FOUND);
    }

    @Test
    public void noSentinelIsA64BitNid() {
        for (long sentinel : new long[]{Nid.UNSET, Nid.NONE, Nid.NONE_64, Nid.NOT_APPLICABLE,
                Nid.NOT_APPLICABLE_64, Nid.NOT_FOUND}) {
            assertFalse(Nid.isValid64(sentinel), "sentinel " + sentinel);
        }
    }

    @Test
    public void bothFormsOfASentinelAreTheSameSignal() {
        assertTrue(Nid.isNotApplicable(Integer.MAX_VALUE));
        assertTrue(Nid.isNotApplicable(Long.MAX_VALUE));
        assertTrue(Nid.isNone(Integer.MIN_VALUE));
        assertTrue(Nid.isNone(Long.MIN_VALUE));
        assertFalse(Nid.isNotApplicable(Integer.MIN_VALUE));
        assertFalse(Nid.isNone(Long.MAX_VALUE));
        assertFalse(Nid.isNotApplicable(Nid.compose64(Nid.MAX_SEQUENCE_64, Nid.MAX_SEQUENCE_64)));
    }

    @Test
    public void narrowCheckedMapsThe64BitSentinelsToTheIntsProvidersStore() {
        assertEquals(Integer.MAX_VALUE, Nid.narrowChecked(Nid.NOT_APPLICABLE_64));
        assertEquals(Integer.MIN_VALUE, Nid.narrowChecked(Nid.NONE_64));
        assertEquals(Integer.MAX_VALUE, Nid.narrowChecked(Nid.NOT_APPLICABLE));
        assertEquals(Integer.MIN_VALUE, Nid.narrowChecked(Nid.NONE));
    }

    // ========== narrowChecked ==========

    @Test
    public void narrowCheckedKeepsEveryWidenedInt() {
        int[] ints = {1, -1, 0, Integer.MIN_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE + 1, 0x00FF_FFFF, 0xFF00_0001};
        for (int value : ints) {
            long widened = value;
            assertEquals(value, Nid.narrowChecked(widened), "widened " + value);
        }
    }

    @Test
    public void narrowCheckedRefusesA64BitNid() {
        long nid = Nid.compose64(3, 7);
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> Nid.narrowChecked(nid));
        assertTrue(exception.getMessage().contains("64-bit nid"), exception.getMessage());
        assertTrue(exception.getMessage().contains("pattern sequence 3"), exception.getMessage());
    }

    @Test
    public void narrowCheckedRefusesValuesJustOutsideTheIntRange() {
        assertThrows(IllegalStateException.class, () -> Nid.narrowChecked(Integer.MAX_VALUE + 1L));
        assertThrows(IllegalStateException.class, () -> Nid.narrowChecked(Integer.MIN_VALUE - 1L));
        assertThrows(IllegalStateException.class, () -> Nid.narrowChecked(Long.MAX_VALUE - 1));
        assertThrows(IllegalStateException.class, () -> Nid.narrowChecked(Long.MIN_VALUE + 1));
    }

    // ========== 64-bit nids ==========

    @Test
    public void compose64PutsThePatternInTheUpperHalf() {
        assertEquals((1L << 32) | 1, Nid.compose64(1, 1));
        assertEquals(0x0000_0005_0000_0009L, Nid.compose64(5, 9));
        assertEquals(0x7FFF_FFFE_7FFF_FFFEL, Nid.compose64(Nid.MAX_SEQUENCE_64, Nid.MAX_SEQUENCE_64));
    }

    @Test
    public void halvesInvertCompose64() {
        int[][] pairs = {{1, 1}, {1, Nid.MAX_SEQUENCE_64}, {Nid.MAX_SEQUENCE_64, 1}, {255, 16_777_215}, {42, 1_000_000}};
        for (int[] pair : pairs) {
            long nid = Nid.compose64(pair[0], pair[1]);
            assertEquals(pair[0], Nid.patternSequence64(nid));
            assertEquals(pair[1], Nid.elementSequence64(nid));
            assertTrue(Nid.isValid64(nid));
            assertEquals(nid, Nid.validate64(nid));
        }
    }

    @Test
    public void compose64RefusesHalvesBelowOne() {
        assertThrows(IllegalArgumentException.class, () -> Nid.compose64(0, 1));
        assertThrows(IllegalArgumentException.class, () -> Nid.compose64(1, 0));
        assertThrows(IllegalArgumentException.class, () -> Nid.compose64(-1, 1));
        assertThrows(IllegalArgumentException.class, () -> Nid.compose64(1, Integer.MIN_VALUE));
        // Like 0, Integer.MAX_VALUE is never a sequence, so no 64-bit nid is Long.MAX_VALUE.
        assertThrows(IllegalArgumentException.class, () -> Nid.compose64(Integer.MAX_VALUE, 1));
        assertThrows(IllegalArgumentException.class, () -> Nid.compose64(1, Integer.MAX_VALUE));
        assertFalse(Nid.isValid64((5L << 32) | Integer.MAX_VALUE));
        assertFalse(Nid.isValid64(((long) Integer.MAX_VALUE << 32) | 5));
    }

    @Test
    public void every64BitNidIsPositiveAndOutsideTheIntRange() {
        long smallest = Nid.compose64(1, 1);
        assertTrue(smallest > Integer.MAX_VALUE);
        assertTrue(Nid.compose64(Nid.MAX_SEQUENCE_64, Nid.MAX_SEQUENCE_64) > 0);
    }

    @Test
    public void noWidenedIntIsAValid64BitNid() {
        int[] ints = {1, -1, 12345, -12345, Integer.MAX_VALUE - 1, Integer.MIN_VALUE + 1, 0xFF00_0001};
        for (int value : ints) {
            long widened = value;
            assertFalse(Nid.isValid64(widened), "widened " + value);
            assertThrows(IllegalArgumentException.class, () -> Nid.validate64(widened));
        }
    }

    @Test
    public void negativeHalvesAreNotValid64BitNids() {
        // A negative element half is reserved for snapshot handles; a negative pattern half is unassigned.
        assertFalse(Nid.isValid64((5L << 32) | 0x8000_0001L));
        assertFalse(Nid.isValid64(0x8000_0001_0000_0001L));
    }

    @Test
    public void longOrderIsPatternThenElementOrder() {
        assertTrue(Nid.compose64(1, Nid.MAX_SEQUENCE_64) < Nid.compose64(2, 1));
        assertTrue(Nid.compose64(2, 1) < Nid.compose64(2, 2));
    }
}
