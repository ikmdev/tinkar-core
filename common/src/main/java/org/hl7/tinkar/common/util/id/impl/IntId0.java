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
final class IntId0 implements IntIdList, IntIdSet
{
    static final IntId0 INSTANCE = new IntId0();
    static final int[] elements = new int[0];

    @Override
    public int get(int index)
    {
        throw new IndexOutOfBoundsException("Index: " + index + ", Size: 0");
    }

    @Override
    public int size() {
        return 0;
    }

    @Override
    public void forEach(IntConsumer consumer) {
        // nothing to do...
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
        return false;
    }
}
