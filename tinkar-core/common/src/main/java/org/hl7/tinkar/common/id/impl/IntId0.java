package org.hl7.tinkar.common.id.impl;

import java.util.function.IntConsumer;
import java.util.stream.IntStream;

/**
 * IntId0 is an optimization for IntList or IntSet of size 0.
 */
public abstract class IntId0
{
    public static final int[] elements = new int[0];

    public int get(int index)
    {
        throw new IndexOutOfBoundsException("Index: " + index + ", Size: 0");
    }

    public int size() {
        return 0;
    }

    public void forEach(IntConsumer consumer) {
        // nothing to do...
    }

    public IntStream intStream() {
        return IntStream.of(elements);
    }

    public int[] toArray() {
        return elements;
    }

    public boolean contains(int value) {
        return false;
    }

    public boolean isEmpty() {
        return false;
    }

}
