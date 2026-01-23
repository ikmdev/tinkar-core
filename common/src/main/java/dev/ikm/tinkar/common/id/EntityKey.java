package dev.ikm.tinkar.common.id;

import dev.ikm.tinkar.common.id.impl.KeyUtil;
import dev.ikm.tinkar.common.id.impl.NidCodec6;

/**
 * Represents a unique key for an entity, combining various sequences to produce a single key.
 * This interface is used to define and validate entity keys based on specified sequence constraints.
 */
public interface EntityKey {
    long MAX_48BIT_UNSIGNED = (1L << 48) - 1;
    int MAX_16BIT_UNSIGNED = (1 << 16) - 1;

    /**
     * Combines the pattern sequence and element sequence into a single long key. Necessary
     * until we get JEP 401: Value Classes, then we can make the entity key a value class.
     * @return
     */
    public long longKey();

    /**
     * The pattern sequence. A 16-bit unsigned number.
     * @return an int > 0 and < 2^16 (65,536)
     */
    int patternSequence();

    /**
     * The element sequence. A 48-bit unsigned number.
     * @return an int > 0 and < 2^48 (281,474,976,710,656)
     */
    long elementSequence();

    /**
     * Returns the unique native identifier (NID) for this entity key.
     * The implementation of this method provides an integer ID representing
     * the entity in unique terms within its context. Native identifiers start at
     * {@code dev.ikm.tinkar.common.service.PrimitiveDataService.FIRST_NID} and increment by 1 for each new entity.
     *
     * @return an integer representing the unique identifier (NID)
     */
    default int nid() {
        return NidCodec6.encode(patternSequence(), elementSequence());
    }

    default byte[] toBytes() {
        return KeyUtil.entityKeyToBytes(longKey());
    }

    default EntityKey fromBytes(byte[] bytes) {
        return EntityKey.ofLongKey(KeyUtil.byteArrayToLong(bytes));
    }

    default byte[] key() {
        return KeyUtil.patternSequenceElementSequenceToKey(patternSequence(), elementSequence());
    }

    static EntityKey of(int patternSequence, long elementSequence) {
        return new EntityKeyRecord(patternSequence, elementSequence);
    }

    static EntityKey ofNid(int nid) {
        return new EntityKeyRecord(NidCodec6.decodePatternSequence(nid), NidCodec6.decodeElementSequence(nid));
    }

    static EntityKey ofLongKey(long longKey) {
        return new EntityKeyRecord(longKey);
    }

    default byte[] patternSequenceAsByteArray() {
        return KeyUtil.toTwoBytesBigEndian(patternSequence());
    }

    interface EntityVersionKey extends EntityKey {
        /**
         * The stamp sequence. A 32-bit unsigned number.
         * @return an int >= 0 and < 2^32 (4,294,967,296)
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
        public long longKey() {
            return KeyUtil.patternSequenceElementSequenceToLongKey(patternSequence(), elementSequence());
        }
        public EntityKeyRecord {
            checkPatternSequence(patternSequence());
            checkElementSequence(elementSequence());
        }
        public EntityKeyRecord(long longKey) {
            this(KeyUtil.longKeyToPatternSequence(longKey), KeyUtil.longKeyToElementSequence(longKey));
        }
    }

    record EntityVersionKeyRecord(int patternSequence, long elementSequence, int stampSequence) implements EntityVersionKey {
        @Override
        public long longKey() {
            return KeyUtil.patternSequenceElementSequenceToLongKey(patternSequence(), elementSequence());
        }
    }

    static void checkElementSequence(long elementSequence) {
        if (elementSequence < 0 || elementSequence > MAX_48BIT_UNSIGNED) {
            throw new IllegalArgumentException("elementSequence is out of range: " + elementSequence);
        }
    }

    static void checkPatternSequence(int patternSequence) {
        if (patternSequence < 0 || patternSequence > MAX_16BIT_UNSIGNED) {
            throw new IllegalArgumentException("patternSequence is out of range: " + patternSequence);
        }
    }

    static void checkStampSequence(int stampSequence) {
        if (stampSequence < 0) {
            throw new IllegalArgumentException("stampSequence is out of range: " + stampSequence);
        }
    }

    static void checkLongKey(long longKey) {
        checkElementSequence(KeyUtil.longKeyToElementSequence(longKey));
        checkPatternSequence(KeyUtil.longKeyToPatternSequence(longKey));
    }

    /**
     * Special EntityKey implementation for providers that use sequential NIDs
     * (SpinedArray, MVStore, Ephemeral) rather than pattern-encoded NIDs.
     * <p>
     * This implementation stores the NID directly and returns it unchanged from {@link #nid()},
     * bypassing the NidCodec6 encoding. The pattern sequence is always 0 (indicating
     * "not pattern-encoded") and the element sequence equals the NID value offset to be positive.
     * </p>
     * <p>
     * This allows the EntityKey API to be satisfied while maintaining compatibility with
     * existing sequential NID assignment in non-RocksDB providers.
     * </p>
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
        public long longKey() {
            // Pack as [0:16][elementSequence:48] for consistency
            return elementSequence() & MAX_48BIT_UNSIGNED;
        }

        /**
         * Returns the original sequential NID directly, bypassing NidCodec6.
         */
        @Override
        public int nid() {
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
    static EntityKey ofSequentialNid(int nid) {
        return new SequentialNidEntityKey(nid);
    }

}
