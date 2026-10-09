package dev.ikm.tinkar.common.id.impl;

import dev.ikm.tinkar.common.id.Nid;

import static dev.ikm.tinkar.common.id.Nid.MAX_SEQUENCE_64;

/**
 * The nid layout of the open knowledge base: how a nid holds a pattern sequence and an element
 * sequence, and how a store keys an entity (IKE-Network/ike-issues#1138, #1258).
 *
 * <p>Nids are persisted in a RocksDB knowledge base's entity bytes, so the layout is a property
 * of the database, not of the build. A database written with the 6-bit layout (at most 63
 * patterns) is still opened and used in that layout, as is one written with the 8-bit layout
 * (up to 255 patterns); a new database uses the 64-bit layout, in which a nid is a {@code long}
 * holding the pattern sequence in its upper 32 bits and the element sequence in its lower 32,
 * and is the store's key as it is (design {@code design-2026-10-07-64-bit-rocks-store}). The two
 * legacy layouts are recognized on open by the pattern-of-patterns counter:
 * {@value NidCodec8#PATTERN_PATTERN_SEQUENCE} for 8-bit, which the 6-bit layout cannot produce;
 * a 64-bit store names its layout in its own column family. The providers that assign nids in
 * sequence activate {@link #SEQUENTIAL} when they open. Each layout names the
 * {@linkplain #entityFormat() entity format} its store reads and writes.
 *
 * <p>One knowledge base is open per process, so the layout is process-wide:
 * the data provider {@linkplain #activate(NidLayout) activates} it when it opens a
 * database, and nid encoding and decoding go through {@link #active()}. Before
 * any database is opened the active layout is {@link #EIGHT_BIT}.
 */
public enum NidLayout {

    /** 6-bit pattern / 26-bit element ({@link NidCodec6}); the legacy layout, at most 63 patterns. */
    SIX_BIT("6-bit", NidCodec6.MAX_PATTERN_SEQUENCE, NidLayout.ENTITY_FORMAT_1, 2) {
        @Override
        public long encode(int patternSequence, long elementSequence) {
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
        public long nidForRocksKey(long rocksKey) {
            return NidCodec6.nidForRocksKey(rocksKey);
        }

        @Override
        public void validateNid(long nid) {
            NidCodec6.validateNid(Nid.narrowChecked(nid));
        }

        @Override
        public long maxElementSequence() {
            return NidCodec6.MAX_ELEMENT_SEQUENCE;
        }
    },

    /** 8-bit pattern / 24-bit element ({@link NidCodec8}); up to 255 patterns. */
    EIGHT_BIT("8-bit", NidCodec8.PATTERN_PATTERN_SEQUENCE, NidLayout.ENTITY_FORMAT_1, 2) {
        @Override
        public long encode(int patternSequence, long elementSequence) {
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
        public long nidForRocksKey(long rocksKey) {
            return NidCodec8.nidForRocksKey(rocksKey);
        }

        @Override
        public void validateNid(long nid) {
            NidCodec8.validateNid(Nid.narrowChecked(nid));
        }

        @Override
        public long maxElementSequence() {
            return NidCodec8.MAX_ELEMENT_SEQUENCE;
        }
    },

    /**
     * The nids of the providers that assign them in sequence, from
     * {@code PrimitiveDataService.FIRST_NID} ({@code Integer.MIN_VALUE + 1}) upward: the
     * spined-array (persistent or ephemeral) and gRPC providers, which activate it when they
     * open. A sequential nid does not carry its pattern: its pattern sequence is 0, and its
     * element sequence is its offset from {@code Integer.MIN_VALUE}, as in
     * {@link dev.ikm.tinkar.common.id.EntityKey#ofSequentialNid(long)}. There is no
     * pattern-of-patterns.
     */
    SEQUENTIAL("sequential", 0, NidLayout.ENTITY_FORMAT_1, 0) {
        @Override
        public long encode(int patternSequence, long elementSequence) {
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
        public long nidForRocksKey(long rocksKey) {
            return encode(KeyUtil.rocksKeyToPatternSequence(rocksKey), KeyUtil.rocksKeyToElementSequence(rocksKey));
        }

        @Override
        public void validateNid(long nid) {
            if (nid == 0 || nid <= Integer.MIN_VALUE || nid >= Integer.MAX_VALUE) {
                throw new IllegalArgumentException("Forbidden nid value: " + nid);
            }
        }

        @Override
        public long rocksKey(int patternSequence, long elementSequence) {
            return elementSequence & 0xFFFF_FFFF_FFFFL;
        }

        @Override
        public long maxElementSequence() {
            return MAX_SEQUENTIAL_ELEMENT;
        }

        @Override
        public int patternPatternSequence() {
            throw new UnsupportedOperationException("a sequential store has no pattern-of-patterns");
        }

        @Override
        public int maxAssignablePatternSequence() {
            throw new UnsupportedOperationException("a sequential store does not assign pattern sequences");
        }
    },

    /**
     * The 64-bit layout of a new Rocks store (design {@code design-2026-10-07-64-bit-rocks-store}):
     * a nid is {@link Nid#compose64}, the pattern sequence in the upper 32 bits and the element
     * sequence in the lower, both from 1 through {@value Nid#MAX_SEQUENCE_64}; the store's key is
     * the nid itself, eight bytes big-endian, so {@code long} order, key order and
     * pattern-then-element order are one order. The pattern-of-patterns is sequence 1, with the
     * concept pattern at its element 2 and the stamp pattern at 3. Entities are written in
     * {@linkplain #ENTITY_FORMAT_2 format 2}.
     */
    SIXTY_FOUR_BIT("64-bit", 1, NidLayout.ENTITY_FORMAT_2, 4) {
        @Override
        public long encode(int patternSequence, long elementSequence) {
            if (elementSequence < 1 || elementSequence > Nid.MAX_SEQUENCE_64) {
                throw new IllegalArgumentException("element sequence " + elementSequence
                        + " is out of range: a 64-bit nid's halves run from 1 through " + MAX_SEQUENCE_64);
            }
            return Nid.compose64(patternSequence, (int) elementSequence);
        }

        @Override
        public int decodePatternSequence(long nid) {
            return Nid.patternSequence64(nid);
        }

        @Override
        public long decodeElementSequence(long nid) {
            return Nid.elementSequence64(nid);
        }

        @Override
        public long rocksKeyForNid(long nid) {
            return nid;
        }

        @Override
        public long nidForRocksKey(long rocksKey) {
            return rocksKey;
        }

        @Override
        public void validateNid(long nid) {
            Nid.validate64(nid);
        }

        @Override
        public long rocksKey(int patternSequence, long elementSequence) {
            return encode(patternSequence, elementSequence);
        }

        @Override
        public int patternSequenceOfRocksKey(long rocksKey) {
            return Nid.patternSequence64(rocksKey);
        }

        @Override
        public long elementSequenceOfRocksKey(long rocksKey) {
            return Nid.elementSequence64(rocksKey);
        }

        @Override
        public long maxElementSequence() {
            return Nid.MAX_SEQUENCE_64;
        }

        @Override
        public int maxAssignablePatternSequence() {
            return Nid.MAX_SEQUENCE_64;
        }
    };

    /**
     * Entity format 1: each nid reference written as four bytes, the format of every 6-bit, 8-bit
     * and sequential store ({@code EntityRecordFactory.ENTITY_FORMAT_VERSION}).
     */
    public static final byte ENTITY_FORMAT_1 = 1;

    /**
     * Entity format 2: the format of a 64-bit store. Each nid reference is its two halves as
     * varints, counts and lengths are varints, an id set is grouped by pattern, and UUIDs keep
     * their fixed width ({@code EntityRecordFormat2}).
     */
    public static final byte ENTITY_FORMAT_2 = 2;

    /** The largest element sequence of a sequential nid: that of {@code Integer.MAX_VALUE - 1}. */
    private static final long MAX_SEQUENTIAL_ELEMENT = ((long) Integer.MAX_VALUE - 1) - Integer.MIN_VALUE;

    private static volatile NidLayout active = EIGHT_BIT;

    private final String displayName;
    private final int patternPatternSequence;
    private final byte entityFormat;
    private final int patternPrefixLength;

    NidLayout(String displayName, int patternPatternSequence, byte entityFormat, int patternPrefixLength) {
        this.displayName = displayName;
        this.patternPatternSequence = patternPatternSequence;
        this.entityFormat = entityFormat;
        this.patternPrefixLength = patternPrefixLength;
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
     * Returns the layout of a legacy database, given the pattern sequences that have element
     * counters in it. A 64-bit store is not detected this way: it names its layout in its own
     * column family, and its counters never reach these values.
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
     * head of its entity bytes.
     *
     * @return the entity format
     */
    public byte entityFormat() {
        return entityFormat;
    }

    /**
     * Returns the pattern sequence of the pattern-of-patterns, under which every
     * pattern entity is keyed: 63 for 6-bit, 255 for 8-bit, 1 for 64-bit.
     *
     * @return the pattern-of-patterns sequence
     */
    public int patternPatternSequence() {
        return patternPatternSequence;
    }

    /**
     * Returns the largest pattern sequence an ordinary pattern may receive: one below the
     * pattern-of-patterns in the legacy layouts, {@value Nid#MAX_SEQUENCE_64} in the 64-bit one.
     *
     * @return the largest assignable pattern sequence
     */
    public int maxAssignablePatternSequence() {
        return patternPatternSequence - 1;
    }

    /**
     * Returns the largest element sequence a pattern may hold in this layout.
     *
     * @return the largest element sequence
     */
    public abstract long maxElementSequence();

    /**
     * Returns how many leading bytes of a store key hold the pattern sequence: 2 in the legacy
     * layouts, whose key is {@code [patternSequence:16][elementSequence:48]}, 4 in the 64-bit
     * layout, whose key is the nid. A range of one pattern's keys shares that prefix.
     *
     * @return the pattern prefix length in bytes
     */
    public int patternPrefixLength() {
        return patternPrefixLength;
    }

    /**
     * Encodes (patternSequence, elementSequence) into a nid.
     *
     * @param patternSequence the pattern sequence
     * @param elementSequence the element sequence
     * @return the nid, widened from the {@code int} a legacy layout packs
     */
    public abstract long encode(int patternSequence, long elementSequence);

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
     * Converts a nid to the store's key: {@code [patternSequence:16][elementSequence:48]} in
     * the legacy layouts, the nid itself in the 64-bit layout.
     *
     * @param nid the nid
     * @return the rocks key
     */
    public abstract long rocksKeyForNid(long nid);

    /**
     * Converts a store key to a nid.
     *
     * @param rocksKey the rocks key
     * @return the nid
     */
    public abstract long nidForRocksKey(long rocksKey);

    /**
     * Composes a store key from the two sequences.
     *
     * @param patternSequence the pattern sequence
     * @param elementSequence the element sequence
     * @return the rocks key
     */
    public long rocksKey(int patternSequence, long elementSequence) {
        return KeyUtil.patternSequenceElementSequenceToRocksKey(patternSequence, elementSequence);
    }

    /**
     * The pattern sequence a store key holds.
     *
     * @param rocksKey the rocks key
     * @return its pattern sequence
     */
    public int patternSequenceOfRocksKey(long rocksKey) {
        return KeyUtil.rocksKeyToPatternSequence(rocksKey);
    }

    /**
     * The element sequence a store key holds.
     *
     * @param rocksKey the rocks key
     * @return its element sequence
     */
    public long elementSequenceOfRocksKey(long rocksKey) {
        return KeyUtil.rocksKeyToElementSequence(rocksKey);
    }

    /**
     * Checks that a nid is one this layout can produce.
     *
     * @param nid the nid
     * @throws IllegalArgumentException if the nid is invalid for this layout
     */
    public abstract void validateNid(long nid);
}
