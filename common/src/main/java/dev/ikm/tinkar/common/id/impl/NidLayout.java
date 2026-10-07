package dev.ikm.tinkar.common.id.impl;

import dev.ikm.tinkar.common.id.Nid;

/**
 * The nid layout of the open knowledge base — how a 32-bit nid packs a pattern
 * sequence and an element sequence (IKE-Network/ike-issues#1138).
 *
 * <p>Nids are persisted in a RocksDB knowledge base's entity bytes, so the layout
 * is a property of the database, not of the build. A database written with the
 * 6-bit layout (at most 63 patterns) is still opened and used in that layout; new
 * databases use the 8-bit layout (up to 255 patterns). The layout is recognized
 * on open by the pattern-of-patterns counter: {@value NidCodec8#PATTERN_PATTERN_SEQUENCE}
 * for 8-bit, which the 6-bit layout cannot produce. The providers that assign nids
 * in sequence activate {@link #SEQUENTIAL} when they open. Each layout names the
 * {@linkplain #entityFormat() entity format} its store reads and writes.
 *
 * <p>One knowledge base is open per process, so the layout is process-wide:
 * the data provider {@linkplain #activate(NidLayout) activates} it when it opens a
 * database, and nid encoding and decoding go through {@link #active()}. Before
 * any database is opened the active layout is {@link #EIGHT_BIT}.
 */
public enum NidLayout {

    /** 6-bit pattern / 26-bit element ({@link NidCodec6}); the legacy layout, at most 63 patterns. */
    SIX_BIT("6-bit", NidCodec6.MAX_PATTERN_SEQUENCE, NidLayout.ENTITY_FORMAT_1) {
        @Override
        public int encode(int patternSequence, long elementSequence) {
            return NidCodec6.encode(patternSequence, elementSequence);
        }

        @Override
        public int decodePatternSequence(long nid) {
            return NidCodec6.decodePatternSequence(Nid.narrowChecked(nid));
        }

        @Override
        public long decodeElementSequence(long nid) {
            return NidCodec6.decodeElementSequence(Nid.narrowChecked(nid));
        }

        @Override
        public long rocksKeyForNid(long nid) {
            return NidCodec6.rocksKeyForNid(Nid.narrowChecked(nid));
        }

        @Override
        public int nidForRocksKey(long rocksKey) {
            return NidCodec6.nidForRocksKey(rocksKey);
        }

        @Override
        public void validateNid(long nid) {
            NidCodec6.validateNid(Nid.narrowChecked(nid));
        }
    },

    /** 8-bit pattern / 24-bit element ({@link NidCodec8}); up to 255 patterns. */
    EIGHT_BIT("8-bit", NidCodec8.PATTERN_PATTERN_SEQUENCE, NidLayout.ENTITY_FORMAT_1) {
        @Override
        public int encode(int patternSequence, long elementSequence) {
            return NidCodec8.encode(patternSequence, elementSequence);
        }

        @Override
        public int decodePatternSequence(long nid) {
            return NidCodec8.decodePatternSequence(Nid.narrowChecked(nid));
        }

        @Override
        public long decodeElementSequence(long nid) {
            return NidCodec8.decodeElementSequence(Nid.narrowChecked(nid));
        }

        @Override
        public long rocksKeyForNid(long nid) {
            return NidCodec8.rocksKeyForNid(Nid.narrowChecked(nid));
        }

        @Override
        public int nidForRocksKey(long rocksKey) {
            return NidCodec8.nidForRocksKey(rocksKey);
        }

        @Override
        public void validateNid(long nid) {
            NidCodec8.validateNid(Nid.narrowChecked(nid));
        }
    },

    /**
     * The nids of the providers that assign them in sequence, from
     * {@code PrimitiveDataService.FIRST_NID} ({@code Integer.MIN_VALUE + 1}) upward: the
     * spined-array, MVStore, ephemeral, gRPC and websocket providers, which activate it when they
     * open. A sequential nid does not carry its pattern: its pattern sequence is 0, and its
     * element sequence is its offset from {@code Integer.MIN_VALUE}, as in
     * {@link dev.ikm.tinkar.common.id.EntityKey#ofSequentialNid(long)}. There is no
     * pattern-of-patterns.
     */
    SEQUENTIAL("sequential", 0, NidLayout.ENTITY_FORMAT_1) {
        @Override
        public int encode(int patternSequence, long elementSequence) {
            if (patternSequence != 0) {
                throw new IllegalArgumentException("pattern sequence " + patternSequence
                        + ": a sequential nid does not carry a pattern, so its pattern sequence is 0");
            }
            if (elementSequence < 1 || elementSequence > MAX_SEQUENTIAL_ELEMENT) {
                throw new IllegalArgumentException("element sequence " + elementSequence
                        + " is out of range for a sequential nid: 1 through " + MAX_SEQUENTIAL_ELEMENT);
            }
            return (int) (elementSequence + Integer.MIN_VALUE);
        }

        @Override
        public int decodePatternSequence(long nid) {
            return 0;
        }

        @Override
        public long decodeElementSequence(long nid) {
            return ((long) Nid.narrowChecked(nid)) - Integer.MIN_VALUE;
        }

        @Override
        public long rocksKeyForNid(long nid) {
            return decodeElementSequence(nid);
        }

        @Override
        public int nidForRocksKey(long rocksKey) {
            return encode(KeyUtil.rocksKeyToPatternSequence(rocksKey), KeyUtil.rocksKeyToElementSequence(rocksKey));
        }

        @Override
        public void validateNid(long nid) {
            if (nid == 0 || nid <= Integer.MIN_VALUE || nid >= Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Forbidden nid value: " + nid);
            }
        }

        @Override
        public int patternPatternSequence() {
            throw new UnsupportedOperationException("a sequential store has no pattern-of-patterns");
        }

        @Override
        public int maxAssignablePatternSequence() {
            throw new UnsupportedOperationException("a sequential store does not assign pattern sequences");
        }
    };

    /**
     * Entity format 1: each nid reference written as four bytes, the format of every store so
     * far ({@code EntityRecordFactory.ENTITY_FORMAT_VERSION}).
     */
    public static final byte ENTITY_FORMAT_1 = 1;

    /** The largest element sequence of a sequential nid: that of {@code Integer.MAX_VALUE - 1}. */
    private static final long MAX_SEQUENTIAL_ELEMENT = ((long) Integer.MAX_VALUE - 1) - Integer.MIN_VALUE;

    private static volatile NidLayout active = EIGHT_BIT;

    private final String displayName;
    private final int patternPatternSequence;
    private final byte entityFormat;

    NidLayout(String displayName, int patternPatternSequence, byte entityFormat) {
        this.displayName = displayName;
        this.patternPatternSequence = patternPatternSequence;
        this.entityFormat = entityFormat;
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
     * Returns the entity format a store in this layout reads and writes: the format byte at the
     * head of its entity bytes. Every layout so far uses {@link #ENTITY_FORMAT_1}.
     *
     * @return the entity format
     */
    public byte entityFormat() {
        return entityFormat;
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
    public abstract int decodePatternSequence(long nid);

    /**
     * Extracts the element sequence from a nid.
     *
     * @param nid the nid
     * @return the element sequence
     */
    public abstract long decodeElementSequence(long nid);

    /**
     * Converts a nid to the RocksDB key {@code [patternSequence:16][elementSequence:48]}.
     *
     * @param nid the nid
     * @return the rocks key
     */
    public abstract long rocksKeyForNid(long nid);

    /**
     * Converts a RocksDB key to a nid.
     *
     * @param rocksKey the rocks key
     * @return the nid
     */
    public abstract int nidForRocksKey(long rocksKey);

    /**
     * Checks that a nid is one this layout can produce.
     *
     * @param nid the nid
     * @throws IllegalArgumentException if the nid is invalid for this layout
     */
    public abstract void validateNid(long nid);
}
