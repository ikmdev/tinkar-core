package org.hl7.tinkar.common.util.ints2long;

public class IntsInLong {
    public static long ints2Long(int int1, int int2) {
        return (((long) int1) << 32) | (int2 & 0xffffffffL);
    }

    public static int int1FromLong(long combinedInts) {
        return (int) (combinedInts >> 32);
    }

    public static int int2FromLong(long combinedInts) {
        return (int) combinedInts;
    }
}
