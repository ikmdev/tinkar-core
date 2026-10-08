package dev.ikm.tinkar.common.id;

import dev.ikm.tinkar.common.id.impl.KeyUtil;
import dev.ikm.tinkar.common.id.impl.NidLayout;

/**
 * Represents a unique key for an entity, combining various sequences to produce a single key.
 * This interface is used to define and validate entity keys based on specified sequence constraints.
 */
public interface EntityKey {
    long MAX_48BIT_UNSIGNED = (1L << 48) - 1;
    int MAX_16BIT_UNSIGNED = (1 << 16) - 1;

    /**
     * The RocksDB store key, as the active {@link NidLayout} composes it: in the legacy layouts
     * {@code [patternSequence:16][elementSequence:48]}, which is not a nid (a typical one passes
     * {@link Nid#isValid64(long)}, so one passed as a nid shows up only as a lookup that finds
     * nothing; hence the name); in the 64-bit layout the nid itself. Used only by rocks-kb, to
     * key its column families (design {@code design-2026-09-30-64-bit-nids}).
     *
     * @return the rocks key
     */
    long rocksKey();

    /**
     * The pattern sequence: 16 bits in the legacy layouts, 31 in the 64-bit one.
     * @return an int &gt; 0 and at most {@value Nid#MAX_SEQUENCE_64}
     */
    int patternSequence();

    /**
     * The element sequence: 48 bits in the legacy layouts, 31 in the 64-bit one.
     * @return a long &gt; 0 and &lt; 2^48 (281,474,976,710,656)
     */
    long elementSequence();

    /**
     * Returns the unique native identifier (NID) for this entity key.
     * The implementation of this method provides an integer ID representing
     * the entity in unique terms within its context. Native identifiers start at
     * {@code dev.ikm.tinkar.common.service.SequentialNids.FIRST_NID} and increment by 1 for each new entity.
     *
     * @return the nid, widened from the {@code int} a 6-bit, 8-bit, or sequential store holds
     */
    default long nid() {
        return NidLayout.active().encode(patternSequence(), elementSequence());
    }

    default byte[] toBytes() {
        return KeyUtil.entityKeyToBytes(rocksKey());
    }

    default EntityKey fromBytes(byte[] bytes) {
        return EntityKey.ofRocksKey(KeyUtil.byteArrayToLong(bytes));
    }

    /** The store key as eight big-endian bytes; the same bytes in every layout. */
    default byte[] key() {
        return KeyUtil.longToByteArray(rocksKey());
    }

    static EntityKey of(int patternSequence, long elementSequence) {
        return new EntityKeyRecord(patternSequence, elementSequence);
    }

    static EntityKey ofNid(long nid) {
        return new EntityKeyRecord(NidLayout.active().decodePatternSequence(nid), NidLayout.active().decodeElementSequence(nid));
    }

    static EntityKey ofRocksKey(long rocksKey) {
        return new EntityKeyRecord(rocksKey);
    }

    default byte[] patternSequenceAsByteArray() {
        return KeyUtil.toTwoBytesBigEndian(patternSequence());
    }

    interface EntityVersionKey extends EntityKey {
        /**
         * The stamp sequence. A 32-bit unsigned number.
         * @return an int &gt;= 0 and &lt; 2^32 (4,294,967,296)
         */
        int stampSequence();
        default byte[] key() {
            return KeyUtil.elementVersionKey(patternSequence(), elementSequence(), stampSequence());
        }

        static EntityVersionKey of(int patternSequence, long elementSequence, int stampSequence) {
            return new EntityVersionKeyRecord(patternSequence, elementSequence, stampSequence);
        }
    }

    record EntityKeyRecord(int patternSequence, long elementSequence) implements EntityKey {
        @Override
        public long rocksKey() {
            return NidLayout.active().rocksKey(patternSequence(), elementSequence());
        }
        public EntityKeyRecord {
            checkPatternSequence(patternSequence());
            checkElementSequence(elementSequence());
        }
        public EntityKeyRecord(long rocksKey) {
            this(NidLayout.active().patternSequenceOfRocksKey(rocksKey), NidLayout.active().elementSequenceOfRocksKey(rocksKey));
        }
    }

    record EntityVersionKeyRecord(int patternSequence, long elementSequence, int stampSequence) implements EntityVersionKey {
        @Override
        public long rocksKey() {
            return KeyUtil.patternSequenceElementSequenceToRocksKey(patternSequence(), elementSequence());
        }
    }

    static void checkElementSequence(long elementSequence) {
        if (elementSequence < 0 || elementSequence > MAX_48BIT_UNSIGNED) {
            throw new IllegalArgumentException("elementSequence is out of range: " + elementSequence);
        }
    }

    /** A pattern sequence is non-negative and at most {@value Nid#MAX_SEQUENCE_64}; each layout narrows the range further when it encodes. */
    static void checkPatternSequence(int patternSequence) {
        if (patternSequence < 0 || patternSequence > Nid.MAX_SEQUENCE_64) {
            throw new IllegalArgumentException("patternSequence is out of range: " + patternSequence);
        }
    }

    static void checkStampSequence(int stampSequence) {
        if (stampSequence < 0) {
            throw new IllegalArgumentException("stampSequence is out of range: " + stampSequence);
        }
    }

    static void checkRocksKey(long rocksKey) {
        checkElementSequence(KeyUtil.rocksKeyToElementSequence(rocksKey));
        checkPatternSequence(KeyUtil.rocksKeyToPatternSequence(rocksKey));
    }

    /**
     * Special EntityKey implementation for providers that use sequential NIDs
     * (SpinedArray, MVStore, Ephemeral) rather than pattern-encoded NIDs.
     * <p>     * This implementation stores the NID directly and returns it unchanged from {@link #nid()},
     * bypassing the pattern-encoded nid layout. The pattern sequence is always 0 (indicating
     * "not pattern-encoded") and the element sequence equals the NID value offset to be positive.
     * <p>     * This allows the EntityKey API to be satisfied while maintaining compatibility with
     * existing sequential NID assignment in non-RocksDB providers.
     */
    record SequentialNidEntityKey(int sequentialNid) implements EntityKey {
        /**
         * Pattern sequence 0 indicates this is a sequential NID, not pattern-encoded.
         */
        @Override
        public int patternSequence() {
            return 0;
        }

        /**
         * Returns the NID as a positive element sequence for API compatibility.
         * The actual NID is stored separately and returned directly from {@link #nid()}.
         */
        @Override
        public long elementSequence() {
            // Convert negative NID space to positive element sequence
            // FIRST_NID (MIN_VALUE + 1) maps to 1, etc.
            return ((long) sequentialNid) - Integer.MIN_VALUE;
        }

        @Override
        public long rocksKey() {
            // Pack as [0:16][elementSequence:48] for consistency
            return elementSequence() & MAX_48BIT_UNSIGNED;
        }

        /**
         * Returns the original sequential NID directly, bypassing NidLayout.active().
         */
        @Override
        public long nid() {
            return sequentialNid;
        }
    }

    /**
     * Creates an EntityKey that wraps a sequential NID (for non-pattern-encoded providers).
     * Use this for SpinedArray, MVStore, and Ephemeral providers.
     *
     * @param nid the sequential NID from a non-pattern-encoded provider
     * @return an EntityKey that preserves the NID unchanged
     */
    static EntityKey ofSequentialNid(long nid) {
        return new SequentialNidEntityKey(Nid.narrowChecked(nid));
    }

}
