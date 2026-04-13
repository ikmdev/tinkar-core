/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.common.util;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Utility methods for creating and initializing arrays.
 */
public class ArrayUtil {
    /** Private constructor to prevent instantiation. */
    private ArrayUtil() {}
    /**
     * Creates a new {@code long} array of the specified size and fills it with the given value.
     *
     * @param size the length of the array to create
     * @param fill the value to fill every element with
     * @return a new array where every element equals {@code fill}
     */
    public static long[] createAndFill(int size, long fill) {
        long[] arrayToFill = new long[size];
        Arrays.fill(arrayToFill, fill);
        return arrayToFill;
    }
    /**
     * Creates a new {@code int} array of the specified size and fills it with the given value.
     *
     * @param size the length of the array to create
     * @param fill the value to fill every element with
     * @return a new array where every element equals {@code fill}
     */
    public static int[] createAndFill(int size, int fill) {
        int[] arrayToFill = new int[size];
        Arrays.fill(arrayToFill, fill);
        return arrayToFill;
    }

    /**
     * Creates a new {@code int} array of the specified size and fills it with {@code -1}.
     *
     * @param size the length of the array to create
     * @return a new array where every element equals {@code -1}
     */
    public static int[] createAndFillWithMinusOne(int size) {
        int[] arrayToFill = new int[size];
        Arrays.fill(arrayToFill, -1);
        return arrayToFill;
    }

    /**
     * Returns the element at the given index, lazily initializing it from the supplier
     * if it is currently {@code null}.
     *
     * @param <T> the element type
     * @param array the array to read from and potentially write to
     * @param index the index of the element
     * @param supplier provides the value when the element is {@code null}
     * @return the existing or newly-created element at {@code index}
     */
    public static <T> T getIfAbsentPut(T[] array, int index, Supplier<T> supplier) {
        if (array[index] == null) {
            array[index] = supplier.get();
        }
        return array[index];
    }
}
