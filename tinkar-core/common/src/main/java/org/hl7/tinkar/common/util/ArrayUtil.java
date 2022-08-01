package org.hl7.tinkar.common.util;

import java.util.Arrays;
import java.util.function.Supplier;

public class ArrayUtil {
    public static long[] createAndFill(int size, long fill) {
        long[] arrayToFill = new long[size];
        Arrays.fill(arrayToFill, fill);
        return arrayToFill;
    }
    public static int[] createAndFill(int size, int fill) {
        int[] arrayToFill = new int[size];
        Arrays.fill(arrayToFill, fill);
        return arrayToFill;
    }

    public static int[] createAndFillWithMinusOne(int size) {
        int[] arrayToFill = new int[size];
        Arrays.fill(arrayToFill, -1);
        return arrayToFill;
    }

    public static <T> T getIfAbsentPut(T[] array, int index, Supplier<T> supplier) {
        if (array[index] == null) {
            array[index] = supplier.get();
        }
        return array[index];
    }
}
