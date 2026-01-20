package dev.ikm.tinkar.common.id.impl;

/**
 * Packs and unpacks a 6-bit {@code patternSequence} and a 26-bit
 * {@code elementSequence} into a 32-bit {@code nid}.
 *
 * <p>Bit layout (MSB → LSB):</p>
 * <pre>{@code
 * [6-bit patternSequence][26-bit elementIndex]
 * }</pre>
 *
 * <dl>
 *   <dt>patternSequence (P)</dt>
 *   <dd>Unsigned, 1..63 (6 bits; 0 is disallowed)</dd>
 *   <dt>elementSequence (E)</dt>
 *   <dd>Unsigned, 1..67,108,864 (inclusive), represented as
 *       {@code elementIndex = E - 1} in 26 bits</dd>
 *   <dt>elementIndex (I)</dt>
 *   <dd>{@code I = E - 1}, range 0..(2^26 - 1) = 0..67,108,863</dd>
 * </dl>
 *
 * <p>Encoding (compose):</p>
 * <pre>{@code
 * I   = E - 1
 * nid = (P << 26) | I
 * }</pre>
 *
 * <p>Decoding (extract):</p>
 * <pre>{@code
 * P = (nid >>> 26) & 0x3F
 * I =  nid         & 0x03FF_FFFF
 * E = I + 1
 * }</pre>
 *
 * <p>Ranges and capacities:</p>
 * <ul>
 *   <li>P ∈ [1, 63] → 63 usable patterns</li>
 *   <li>I ∈ [0, 2^26 - 1] → E ∈ [1, 2^26] = [1, 67,108,864]</li>
 * </ul>
 *
 * <p>Forbidden nid values (must not be produced):</p>
 * <ul>
 *   <li>{@code 0}: unreachable because P ≠ 0</li>
 *   <li>{@code Integer.MIN_VALUE (0x8000_0000)}:
 *       maps to {@code (P=32, E=1)} → reject this pair</li>
 *   <li>{@code Integer.MAX_VALUE (0x7FFF_FFFF)}:
 *       maps to {@code (P=31, E=67,108,864)} → reject this pair</li>
 * </ul>
 *
 * @apiNote
 * The mapping is injective except for the two excluded pairs above. Per-pattern
 * issuance stays sequential because {@code E} increments linearly within the
 * lower 26 bits.
 *
 * @implNote
 * Masks and shifts are fixed-width and safe:
 * <ul>
 *   <li>{@code PATTERN_MASK = 0x3F}</li>
 *   <li>{@code ELEMENT_MASK = 0x03FF_FFFF}</li>
 * </ul>
 *
 * Examples:
 * {@snippet lang="java":
 * // Encode example
 * int nid = NidCodec6.encode(5, 123_456L);
 *
 * // Decode example
 * int p = NidCodec6.decodePatternSequence(nid);   // 5
 * long e = NidCodec6.decodeElementSequence(nid); // 123_456
 * }
 */
public final class NidCodec6 {
    // Layout constants
    private static final int PATTERN_BITS = 6;                 // P ∈ [1, 63]
    private static final int ELEMENT_BITS = 26;                // element ∈ [0, 2^26 - 1]
    private static final int PATTERN_MASK = (1 << PATTERN_BITS) - 1;   // 0x3F
    private static final int ELEMENT_MASK = (1 << ELEMENT_BITS) - 1;   // 0x03FF_FFFF
    public static final int MAX_PATTERN_SEQUENCE = (int) ((1L << PATTERN_BITS) - 1);
    public static final long MAX_ELEMENT_SEQUENCE = ((1L << ELEMENT_BITS) - 1);

    private NidCodec6() { }

    /**
     * Encodes (patternSequence, elementSequence) into a 32-bit nid.
     * Inputs must start at 1; elementSequence=0 is forbidden.
     * Also forbids the single upper-edge pair (31, 2^26 - 1) to avoid Integer.MAX_VALUE.
     */
    public static int encode(int patternSequence, long elementSequence) {
        if (patternSequence <= 0 || patternSequence > PATTERN_MASK) {
            throw new IllegalArgumentException(
                    "patternSequence out of range (1..63): " + patternSequence
            );
        }
        // Inputs start at 1, and never attain the absolute maximum value.
        if (elementSequence <= 0 || elementSequence > MAX_ELEMENT_SEQUENCE) { // [1 .. 2^26 - 1]
            throw new IllegalArgumentException(
                    "elementSequence out of range (1..67,108,863): " + elementSequence
            );
        }

        // Forbid the only remaining MAX_VALUE producer under direct packing:
        // pattern=31 and element lane all 1s (2^26 - 1).
        if (patternSequence == 31 && elementSequence == MAX_ELEMENT_SEQUENCE) {
            throw new IllegalArgumentException(
                    "Forbidden pair for MAX_VALUE mapping: (pattern=31, element=" + elementSequence + ")"
            );
        }

        int elementBits = (int) (elementSequence & ELEMENT_MASK); // direct pack, no -1
        int nid = (patternSequence << ELEMENT_BITS) | elementBits;

        // Defensive check against Integer.MIN_VALUE (should be unreachable because elementSequence>=1):
        if (nid == Integer.MIN_VALUE) {
            throw new IllegalStateException("Unexpected MIN_VALUE nid after encoding");
        }
        return nid;
    }

    /**
     * Extracts patternSequence from nid.
     */
    public static int decodePatternSequence(int nid) {
        return (nid >>> ELEMENT_BITS) & PATTERN_MASK;
    }

    /**
     * Extracts elementSequence from nid.
     * Direct mapping (no +1): elementSequence ∈ [1 .. 2^26 - 1]
     */
    public static long decodeElementSequence(int nid) {
        return (long) (nid & ELEMENT_MASK);
    }

    /**
     * 64-bit key: [patternSequence:16][elementSequence:48]
     */
    public static long longKeyForNid(int nid) {
        int pattern = (nid >>> ELEMENT_BITS) & PATTERN_MASK;
        long element = (nid & ELEMENT_MASK); // direct, no +1
        return (((long) pattern) << 48) | (element & 0xFFFF_FFFF_FFFFL);
    }

    public static void validateNid(int nid) {
        if (nid == 0 || nid == Integer.MIN_VALUE || nid == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Forbidden nid value: " + nid);
        }
        int pattern = decodePatternSequence(nid);
        if (pattern <= 0 || pattern > PATTERN_MASK) {
            throw new IllegalArgumentException("Decoded patternSequence out of range: " + pattern);
        }
        long elementSeq = decodeElementSequence(nid);
        if (elementSeq <= 0 || elementSeq > MAX_ELEMENT_SEQUENCE) { // [1 .. 2^26 - 1]
            throw new IllegalArgumentException("Decoded elementSequence out of range: " + elementSeq);
        }
        // Also forbid (31, 2^26 - 1) by value check
        if (pattern == 31 && elementSeq == MAX_ELEMENT_SEQUENCE) {
            throw new IllegalArgumentException("Decoded forbidden pair for MAX_VALUE mapping");
        }
    }

    public static int nidForLongKey(long longKey) {
        int pattern = KeyUtil.longKeyToPatternSequence(longKey);
        long element = KeyUtil.longKeyToElementSequence(longKey);
        return encode(pattern, element);
    }
}