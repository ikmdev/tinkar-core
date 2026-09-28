package dev.ikm.tinkar.common.id.impl;

/**
 * The nid layout of the open knowledge base — how a 32-bit nid packs a pattern
 * sequence and an element sequence (IKE-Network/ike-issues#1138).
 *
 * <p>Nids are persisted in a RocksDB knowledge base's entity bytes, so the layout
 * is a property of the database, not of the build. A database written with the
 * 6-bit layout (at most 63 patterns) is still opened and used in that layout; new
 * databases use the 8-bit layout (up to 255 patterns). The layout is recognized
 * on open by the pattern-of-patterns counter: {@value NidCodec8#PATTERN_PATTERN_SEQUENCE}
 * for 8-bit, which the 6-bit layout cannot produce.
 *
 * <p>One knowledge base is open per process, so the layout is process-wide:
 * the data provider {@linkplain #activate(NidLayout) activates} it when it opens a
 * database, and nid encoding and decoding go through {@link #active()}. Before
 * any database is opened the active layout is {@link #EIGHT_BIT}.
 */
public enum NidLayout {

    /** 6-bit pattern / 26-bit element ({@link NidCodec6}); the legacy layout, at most 63 patterns. */
    SIX_BIT("6-bit", NidCodec6.MAX_PATTERN_SEQUENCE) {
        @Override
        public int encode(int patternSequence, long elementSequence) {
            return NidCodec6.encode(patternSequence, elementSequence);
        }

        @Override
        public int decodePatternSequence(int nid) {
            return NidCodec6.decodePatternSequence(nid);
        }

        @Override
        public long decodeElementSequence(int nid) {
            return NidCodec6.decodeElementSequence(nid);
        }

        @Override
        public long longKeyForNid(int nid) {
            return NidCodec6.longKeyForNid(nid);
        }

        @Override
        public int nidForLongKey(long longKey) {
            return NidCodec6.nidForLongKey(longKey);
        }

        @Override
        public void validateNid(int nid) {
            NidCodec6.validateNid(nid);
        }
    },

    /** 8-bit pattern / 24-bit element ({@link NidCodec8}); up to 255 patterns. */
    EIGHT_BIT("8-bit", NidCodec8.PATTERN_PATTERN_SEQUENCE) {
        @Override
        public int encode(int patternSequence, long elementSequence) {
            return NidCodec8.encode(patternSequence, elementSequence);
        }

        @Override
        public int decodePatternSequence(int nid) {
            return NidCodec8.decodePatternSequence(nid);
        }

        @Override
        public long decodeElementSequence(int nid) {
            return NidCodec8.decodeElementSequence(nid);
        }

        @Override
        public long longKeyForNid(int nid) {
            return NidCodec8.longKeyForNid(nid);
        }

        @Override
        public int nidForLongKey(long longKey) {
            return NidCodec8.nidForLongKey(longKey);
        }

        @Override
        public void validateNid(int nid) {
            NidCodec8.validateNid(nid);
        }
    };

    private static volatile NidLayout active = EIGHT_BIT;

    private final String displayName;
    private final int patternPatternSequence;

    NidLayout(String displayName, int patternPatternSequence) {
        this.displayName = displayName;
        this.patternPatternSequence = patternPatternSequence;
    }

    /**
     * Returns the layout of the open knowledge base.
     *
     * @return the active layout; {@link #EIGHT_BIT} until a provider activates another
     */
    public static NidLayout active() {
        return active;
    }

    /**
     * Makes {@code layout} the process-wide layout. Called by the data provider
     * when it opens a database, before any nid is encoded or decoded.
     *
     * @param layout the layout of the database being opened
     */
    public static void activate(NidLayout layout) {
        active = layout;
    }

    /**
     * Returns the layout of a database, given the pattern sequences that have
     * element counters in it.
     *
     * @param patternSequencesWithCounters the pattern sequences with counters; empty
     *                                     for a new database
     * @return {@link #EIGHT_BIT} for a new database or one with a counter at
     *         {@value NidCodec8#PATTERN_PATTERN_SEQUENCE}; otherwise {@link #SIX_BIT}
     */
    public static NidLayout detect(java.util.Collection<Integer> patternSequencesWithCounters) {
        if (patternSequencesWithCounters.isEmpty()
                || patternSequencesWithCounters.contains(EIGHT_BIT.patternPatternSequence)) {
            return EIGHT_BIT;
        }
        return SIX_BIT;
    }

    /**
     * Returns a short name for messages and indicators, e.g. {@code "6-bit"}.
     *
     * @return the display name
     */
    public String displayName() {
        return displayName;
    }

    /**
     * Returns the pattern sequence of the pattern-of-patterns, under which every
     * pattern entity is keyed: 63 for 6-bit, 255 for 8-bit.
     *
     * @return the pattern-of-patterns sequence
     */
    public int patternPatternSequence() {
        return patternPatternSequence;
    }

    /**
     * Returns the largest pattern sequence an ordinary pattern may receive — one
     * below the pattern-of-patterns.
     *
     * @return the largest assignable pattern sequence
     */
    public int maxAssignablePatternSequence() {
        return patternPatternSequence - 1;
    }

    /**
     * Encodes (patternSequence, elementSequence) into a nid.
     *
     * @param patternSequence the pattern sequence
     * @param elementSequence the element sequence
     * @return the nid
     */
    public abstract int encode(int patternSequence, long elementSequence);

    /**
     * Extracts the pattern sequence from a nid.
     *
     * @param nid the nid
     * @return the pattern sequence
     */
    public abstract int decodePatternSequence(int nid);

    /**
     * Extracts the element sequence from a nid.
     *
     * @param nid the nid
     * @return the element sequence
     */
    public abstract long decodeElementSequence(int nid);

    /**
     * Converts a nid to the RocksDB long key {@code [patternSequence:16][elementSequence:48]}.
     *
     * @param nid the nid
     * @return the long key
     */
    public abstract long longKeyForNid(int nid);

    /**
     * Converts a RocksDB long key to a nid.
     *
     * @param longKey the long key
     * @return the nid
     */
    public abstract int nidForLongKey(long longKey);

    /**
     * Checks that a nid is one this layout can produce.
     *
     * @param nid the nid
     * @throws IllegalArgumentException if the nid is invalid for this layout
     */
    public abstract void validateNid(int nid);
}
