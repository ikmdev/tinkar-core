package org.hl7.tinkar.common.util.id.impl;

import org.eclipse.collections.impl.list.mutable.primitive.IntArrayList;
import org.hl7.tinkar.common.util.id.IntIdList;

import java.util.function.IntConsumer;
import java.util.stream.IntStream;

/**
 */
public final class IntIdListArray
        implements IntIdList
{
    private final int[] elements;

    public IntIdListArray(int... newElements)
    {
        this.elements = newElements;
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
        for (int element: elements) {
            if (value == element) {
                return true;
            }
        }
        return false;
    }

    @Override
    public int get(int index) {
        return elements[index];
    }
}
