package dev.ikm.tinkar.common.id.impl;

import dev.ikm.tinkar.common.id.EntityKey;import dev.ikm.tinkar.common.util.uuid.UuidUtil;

import java.util.UUID;

public class KeyUtil {
    public static int byteArrayToInt(byte[] b) {
        return ((b[0] & 0xFF) << 24) |
                ((b[1] & 0xFF) << 16) |
                ((b[2] & 0xFF) << 8)  |
                (b[3] & 0xFF);
    }
    public static byte[] intToByteArray(int value) {
        return new byte[] {
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value
        };
    }

    public static long byteArrayToLong(byte[] b) {
        return ((long)(b[0] & 0xFF) << 56) |
                ((long)(b[1] & 0xFF) << 48) |
                ((long)(b[2] & 0xFF) << 40) |
                ((long)(b[3] & 0xFF) << 32) |
                ((long)(b[4] & 0xFF) << 24) |
                ((long)(b[5] & 0xFF) << 16) |
                ((long)(b[6] & 0xFF) << 8)  |
                ((long)(b[7] & 0xFF));
    }

    public static byte[] uuidsToByteArray(UUID... uuids) {
        return longArrayToByteArray(UuidUtil.asArray());
    }
    public static UUID[] byteArrayToUuids(byte[] bytes) {
        return UuidUtil.toArray(toLongArray(bytes));
    }

    public static byte[] toTwoBytesBigEndian(int value) {
        int v = value & 0xFFFF; // keep only 16 LSBs
        return new byte[] {
                (byte) (v >>> 8),
                (byte) v
        };
    }

    public static byte[] longToByteArray(long value) {
        return new byte[] {
                (byte) (value >>> 56),
                (byte) (value >>> 48),
                (byte) (value >>> 40),
                (byte) (value >>> 32),
                (byte) (value >>> 24),
                (byte) (value >>> 16),
                (byte) (value >>> 8),
                (byte) value
        };
    }
    public static byte[] longArrayToByteArray(long[] longs) {
        byte[] bytes = new byte[longs.length * 8];
        for (int i = 0; i < longs.length; i++) {
            long value = longs[i];
            int offset = i * 8;
            bytes[offset]     = (byte) (value >>> 56);
            bytes[offset + 1] = (byte) (value >>> 48);
            bytes[offset + 2] = (byte) (value >>> 40);
            bytes[offset + 3] = (byte) (value >>> 32);
            bytes[offset + 4] = (byte) (value >>> 24);
            bytes[offset + 5] = (byte) (value >>> 16);
            bytes[offset + 6] = (byte) (value >>> 8);
            bytes[offset + 7] = (byte) value;
        }
        return bytes;
    }
    public static long[] toLongArray(byte[] bytes) {
        if (bytes.length % 8 != 0) {
            throw new IllegalArgumentException("Byte array size must be a multiple of 8.");
        }
        long[] longs = new long[bytes.length / 8];
        for (int i = 0; i < longs.length; i++) {
            int offset = i * 8;
            longs[i] = ((long) (bytes[offset]     & 0xFF) << 56) |
                    ((long) (bytes[offset + 1] & 0xFF) << 48) |
                    ((long) (bytes[offset + 2] & 0xFF) << 40) |
                    ((long) (bytes[offset + 3] & 0xFF) << 32) |
                    ((long) (bytes[offset + 4] & 0xFF) << 24) |
                    ((long) (bytes[offset + 5] & 0xFF) << 16) |
                    ((long) (bytes[offset + 6] & 0xFF) << 8)  |
                    ((long) (bytes[offset + 7] & 0xFF));
        }
        return longs;
    }
    public static long patternSequenceElementSequenceToLongKey(int patternSequence, long elementSequence) {
        return ((long) patternSequence << 48) | (elementSequence & 0xFFFFFFFFFFFFL);
    }
    public static byte[] patternSequenceElementSequenceToKey(int patternSequence, long elementSequence) {
        EntityKey.checkPatternSequence(patternSequence);
        EntityKey.checkElementSequence(elementSequence);

        byte[] result = new byte[8];
        // 16 bits (2 bytes) for patternSequence — big-endian
        result[0] = (byte) (patternSequence >>> 8);
        result[1] = (byte) patternSequence;

        // 48 bits (6 bytes) for elementSequence — big-endian
        result[2] = (byte) (elementSequence >>> 40);
        result[3] = (byte) (elementSequence >>> 32);
        result[4] = (byte) (elementSequence >>> 24);
        result[5] = (byte) (elementSequence >>> 16);
        result[6] = (byte) (elementSequence >>> 8);
        result[7] = (byte) elementSequence;
        return result;
    }
    public static byte[] elementVersionKey(long longKey, int stampSequence) {
        EntityKey.checkLongKey(longKey);
        EntityKey.checkStampSequence(stampSequence);

        byte[] result = new byte[13];

        // 64 bits (8 bytes) for longKey — big-endian
        longKeyToBytes(longKey, result);
        // byte indicating version
        result[8] = 1;
        // stampSequence
        result[9] = (byte) (stampSequence >>> 24);
        result[10] = (byte) (stampSequence >>> 16);
        result[11] = (byte) (stampSequence >>> 8);
        result[12] = (byte) stampSequence;
        return result;
    }

    /**
     * Add the version index becuase all stamp versions have the same stamp sequence.
     * @param longKey
     * @param stampSequence
     * @param versionIndex
     * @return
     */
    public static byte[] stampVersionKey(long longKey, int stampSequence, byte versionIndex) {
        EntityKey.checkLongKey(longKey);
        EntityKey.checkStampSequence(stampSequence);

        byte[] result = new byte[14];

        // 64 bits (8 bytes) for longKey — big-endian
        longKeyToBytes(longKey, result);
        // byte indicating stampversion
        result[8] = 2;
        // stampSequence
        result[9] = (byte) (stampSequence >>> 24);
        result[10] = (byte) (stampSequence >>> 16);
        result[11] = (byte) (stampSequence >>> 8);
        result[12] = (byte) stampSequence;
        result[13] = (byte) versionIndex;
        return result;
    }

    private static void longKeyToBytes(long longKey, byte[] result) {
        result[0] = (byte) (longKey >>> 56);
        result[1] = (byte) (longKey >>> 48);
        result[2] = (byte) (longKey >>> 40);
        result[3] = (byte) (longKey >>> 32);
        result[4] = (byte) (longKey >>> 24);
        result[5] = (byte) (longKey >>> 16);
        result[6] = (byte) (longKey >>> 8);
        result[7] = (byte) longKey;
    }

    public static byte[] elementVersionKey(int patternSequence, long elementSequence, int stampSequence) {
        return elementVersionKey(patternSequenceElementSequenceToLongKey(patternSequence, elementSequence), stampSequence);
    }

    public static int longKeyToPatternSequence(long longKey) {
        return (int) ((longKey >>> 48) & 0xFFFF);
    }

    public static long longKeyToElementSequence(long longKey) {
        return longKey & 0xFFFFFFFFFFFFL;
    }

    public static UUID byteArrayToUuid(byte[] uuidBytes) {
        return UuidUtil.getUuidFromRawBytes(uuidBytes);
    }

    public static byte[] uuidToByteArray(UUID uuid) {
        return UuidUtil.getRawBytes(uuid);
    }

    public static byte[] entityKeyToBytes(long longKey) {
        EntityKey.checkLongKey(longKey);
        byte[] result = new byte[8];
        // 64 bits (8 bytes) for longKey — big-endian
        longKeyToBytes(longKey, result);
        return result;
    }

    public static EntityKey entityKeyToBytes(byte[] entityKeyBytes) {
        return EntityKey.ofLongKey(byteArrayToLong(entityKeyBytes));
    }

    public static byte[] entityReferencingSemanticKey(EntityKey entityKey, EntityKey referencingEntityKey) {
        long baseLong = entityKey.longKey();
        long refLong = referencingEntityKey.longKey();

        // Validate long keys (consistent with other utilities)
        EntityKey.checkLongKey(baseLong);
        EntityKey.checkLongKey(refLong);

        byte[] result = new byte[16];

        // baseLong (8 bytes, big-endian)
        result[0] = (byte) (baseLong >>> 56);
        result[1] = (byte) (baseLong >>> 48);
        result[2] = (byte) (baseLong >>> 40);
        result[3] = (byte) (baseLong >>> 32);
        result[4] = (byte) (baseLong >>> 24);
        result[5] = (byte) (baseLong >>> 16);
        result[6] = (byte) (baseLong >>> 8);
        result[7] = (byte) baseLong;

        // refLong (8 bytes, big-endian)
        result[8]  = (byte) (refLong >>> 56);
        result[9]  = (byte) (refLong >>> 48);
        result[10] = (byte) (refLong >>> 40);
        result[11] = (byte) (refLong >>> 32);
        result[12] = (byte) (refLong >>> 24);
        result[13] = (byte) (refLong >>> 16);
        result[14] = (byte) (refLong >>> 8);
        result[15] = (byte) refLong;

        return result;
    }

    public static EntityKey referencingEntityKeyFromEntityReferencingSemanticKey(byte[] compoundKey) {
        if (compoundKey == null || compoundKey.length != 16) {
            throw new IllegalArgumentException("compoundKey must be exactly 16 bytes");
        }

        // Offsets:
        // [0..7]   -> entityKey.longKey()
        // [8..15]  -> referencingEntityKey.longKey()

        long refLongKey =
                ((long) (compoundKey[8]  & 0xFF) << 56) |
                        ((long) (compoundKey[9]  & 0xFF) << 48) |
                        ((long) (compoundKey[10] & 0xFF) << 40) |
                        ((long) (compoundKey[11] & 0xFF) << 32) |
                        ((long) (compoundKey[12] & 0xFF) << 24) |
                        ((long) (compoundKey[13] & 0xFF) << 16) |
                        ((long) (compoundKey[14] & 0xFF) << 8)  |
                        ((long) (compoundKey[15] & 0xFF));

        // Validate and construct
        EntityKey.checkLongKey(refLongKey);
        return EntityKey.ofLongKey(refLongKey);
    }

}
