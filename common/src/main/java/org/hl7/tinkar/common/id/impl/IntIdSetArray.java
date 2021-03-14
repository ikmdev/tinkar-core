package org.hl7.tinkar.common.util.id.impl;

import org.hl7.tinkar.common.util.id.IntIdSet;

import java.util.Arrays;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

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
