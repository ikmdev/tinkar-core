package dev.ikm.tinkar.common.id.impl;

/**
 * Packs and unpacks an 8-bit {@code patternSequence} and a 24-bit
 * {@code elementSequence} into a 32-bit {@code nid} (ike-issues#1138).
 *
 * <p>Bit layout (MSB → LSB):</p>
 * <pre>{@code
 * [8-bit patternSequence][24-bit elementSequence]
 * }</pre>
 *
 * <dl>
 *   <dt>patternSequence (P)</dt>
 *   <dd>Unsigned, 1..255 (8 bits; 0 is disallowed). {@value #PATTERN_PATTERN_SEQUENCE}
 *       is reserved for the pattern-of-patterns — the pattern whose elements
 *       are the patterns themselves — so ordinary patterns are
 *       1..{@value #MAX_ASSIGNABLE_PATTERN_SEQUENCE}.</dd>
 *   <dt>elementSequence (E)</dt>
 *   <dd>Unsigned, 1..16,777,215 (2^24 - 1), packed directly into the lower
 *       24 bits; 0 is disallowed.</dd>
 * </dl>
 *
 * <p>Encoding and decoding:</p>
 * <pre>{@code
 * nid = (P << 24) | E
 * P   = (nid >>> 24) & 0xFF
 * E   =  nid         & 0x00FF_FFFF
 * }</pre>
 *
 * <p>Forbidden nid values (never produced):</p>
 * <ul>
 *   <li>{@code 0}: unreachable because P ≠ 0.</li>
 *   <li>{@code Integer.MIN_VALUE (0x8000_0000)}: would be {@code (P=128, E=0)},
 *       unreachable because E ≥ 1.</li>
 *   <li>{@code Integer.MAX_VALUE (0x7FFF_FFFF)}: is {@code (P=127, E=2^24 - 1)};
 *       that pair is rejected.</li>
 * </ul>
 *
 * <p>Nids are persisted in the RocksDB knowledge base's entity bytes, so the
 * layout is part of the stored format. A database written with the 6-bit
 * layout ({@code NidCodec6}: 63 patterns) cannot be read with this one; it is
 * recognized on open by the absence of pattern sequence
 * {@value #PATTERN_PATTERN_SEQUENCE}, which the 6-bit layout cannot produce.
 *
 * {@snippet lang="java":
 * int nid = NidCodec8.encode(5, 123_456L);
 * int p = NidCodec8.decodePatternSequence(nid);   // 5
 * long e = NidCodec8.decodeElementSequence(nid); // 123_456
 * }
 */
public final class NidCodec8 {
    private static final int PATTERN_BITS = 8;
    private static final int ELEMENT_BITS = 24;
    private static final int PATTERN_MASK = (1 << PATTERN_BITS) - 1;   // 0xFF
    private static final int ELEMENT_MASK = (1 << ELEMENT_BITS) - 1;   // 0x00FF_FFFF

    /** Largest encodable pattern sequence. */
    public static final int MAX_PATTERN_SEQUENCE = PATTERN_MASK;

    /** Largest encodable element sequence (2^24 - 1). */
    public static final long MAX_ELEMENT_SEQUENCE = ELEMENT_MASK;

    /**
     * Pattern sequence of the pattern-of-patterns. Fixed — never derived from
     * {@link #MAX_PATTERN_SEQUENCE} and never assigned to an ordinary pattern —
     * because its presence is what identifies a database as 8-bit.
     */
    public static final int PATTERN_PATTERN_SEQUENCE = 255;

    /** Largest pattern sequence an ordinary pattern may receive. */
    public static final int MAX_ASSIGNABLE_PATTERN_SEQUENCE = PATTERN_PATTERN_SEQUENCE - 1;

    /** The one pattern sequence whose all-ones element would encode {@code Integer.MAX_VALUE}. */
    private static final int MAX_VALUE_PATTERN_SEQUENCE = Integer.MAX_VALUE >>> ELEMENT_BITS; // 127

    private NidCodec8() { }

    /**
     * Encodes (patternSequence, elementSequence) into a 32-bit nid.
     *
     * @param patternSequence pattern sequence, 1..{@value #MAX_PATTERN_SEQUENCE}
     * @param elementSequence element sequence, 1..2^24 - 1
     * @return the nid
     * @throws IllegalArgumentException if either sequence is out of range, or the
     *         pair is {@code (127, 2^24 - 1)}, which would encode {@code Integer.MAX_VALUE}
     */
    public static int encode(int patternSequence, long elementSequence) {
        if (patternSequence <= 0 || patternSequence > MAX_PATTERN_SEQUENCE) {
            throw new IllegalArgumentException(
                    "patternSequence out of range (1.." + MAX_PATTERN_SEQUENCE + "): " + patternSequence);
        }
        if (elementSequence <= 0 || elementSequence > MAX_ELEMENT_SEQUENCE) {
            throw new IllegalArgumentException(
                    "elementSequence out of range (1..16,777,215): " + elementSequence);
        }
        if (patternSequence == MAX_VALUE_PATTERN_SEQUENCE && elementSequence == MAX_ELEMENT_SEQUENCE) {
            throw new IllegalArgumentException(
                    "Forbidden pair for MAX_VALUE mapping: (pattern=" + patternSequence
                            + ", element=" + elementSequence + ")");
        }
        return (patternSequence << ELEMENT_BITS) | (int) elementSequence;
    }

    /**
     * Extracts the pattern sequence from a nid.
     *
     * @param nid the nid
     * @return the pattern sequence
     */
    public static int decodePatternSequence(int nid) {
        return (nid >>> ELEMENT_BITS) & PATTERN_MASK;
    }

    /**
     * Extracts the element sequence from a nid.
     *
     * @param nid the nid
     * @return the element sequence
     */
    public static long decodeElementSequence(int nid) {
        return nid & ELEMENT_MASK;
    }

    /**
     * Converts a nid to the 64-bit RocksDB key {@code [patternSequence:16][elementSequence:48]}.
     *
     * @param nid the nid
     * @return the long key
     */
    public static long longKeyForNid(int nid) {
        return KeyUtil.patternSequenceElementSequenceToLongKey(
                decodePatternSequence(nid), decodeElementSequence(nid));
    }

    /**
     * Converts a 64-bit RocksDB key back to a nid.
     *
     * @param longKey the long key
     * @return the nid
     * @throws IllegalArgumentException if the key's sequences do not fit this layout
     */
    public static int nidForLongKey(long longKey) {
        return encode(KeyUtil.longKeyToPatternSequence(longKey),
                KeyUtil.longKeyToElementSequence(longKey));
    }

    /**
     * Checks that a nid is one this codec can produce.
     *
     * @param nid the nid
     * @throws IllegalArgumentException if the nid is forbidden or decodes out of range
     */
    public static void validateNid(int nid) {
        if (nid == 0 || nid == Integer.MIN_VALUE || nid == Integer.MAX_VALUE) {
            throw new IllegalArgumentException("Forbidden nid value: " + nid);
        }
        int pattern = decodePatternSequence(nid);
        if (pattern <= 0) {
            throw new IllegalArgumentException("Decoded patternSequence out of range: " + pattern);
        }
        long element = decodeElementSequence(nid);
        if (element <= 0) {
            throw new IllegalArgumentException("Decoded elementSequence out of range: " + element);
        }
    }
}
