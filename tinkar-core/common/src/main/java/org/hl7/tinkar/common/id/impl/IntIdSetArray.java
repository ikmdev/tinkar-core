package org.hl7.tinkar.common.id.impl;

import org.hl7.tinkar.common.id.IntIdSet;

import java.util.Arrays;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

/**

 https://dirtyhandscoding.wordpress.com/2017/08/25/performance-comparison-linear-search-vs-binary-search/

 The cost of setting up a sort, or a branching structure for a binary search, or a set structure for small sets
 is greater than just iterating through an array. So I chose to use direct iteration for lookup for lists < 32 elements
 in size. I don’t think there will ever be a case when the public id has > 32 UUIDs inside.
 */
public class IntIdSetArray
        implements IntIdSet
{
    private final int[] elements;

    private IntIdSetArray(int... newElements)
    {
        this.elements = newElements;
    }

    public static IntIdSetArray newIntIdSet(int... newElements) {
        Arrays.sort(newElements);
        return new IntIdSetArray(newElements);
    }

    public static IntIdSetArray newIntIdSetAlreadySorted(int... newElements) {
        return new IntIdSetArray(newElements);
    }


    @Override
    public int size() {
        return elements.length;
    }

    @Override
    public void forEach(IntConsumer consumer) {
        for (int element: elements) {
            consumer.accept(element);
        }
    }

    @Override
    public IntStream intStream() {
        return IntStream.of(elements);
    }

    @Override
    public int[] toArray() {
        return elements;
    }

    @Override
    public boolean contains(int value) {
        // for small lists, iteration is faster search than binary search because of less branching.
        if (elements.length < 32) {
            for (int element: elements) {
                if (value == element) {
                    return true;
                }
            }
            return false;
        }

        return Arrays.binarySearch(elements, value) >= 0;
    }
}
