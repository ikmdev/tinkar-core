package org.hl7.tinkar.common.util.id.impl;

import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.hl7.tinkar.common.util.id.IntIdList;
import org.hl7.tinkar.common.util.id.IntIdSet;

import java.util.function.IntConsumer;
import java.util.stream.IntStream;

/**
 * IntId0 is an optimization for {@link ImmutableIntList} of size 0.
 * This file was automatically generated from template file immutablePrimitiveEmptyList.stg.
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
}
