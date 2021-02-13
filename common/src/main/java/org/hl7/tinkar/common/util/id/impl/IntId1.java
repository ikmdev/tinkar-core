package org.hl7.tinkar.common.util.id.impl;

import org.hl7.tinkar.common.util.id.IntIdList;
import org.hl7.tinkar.common.util.id.IntIdSet;

import java.util.function.IntConsumer;
import java.util.stream.IntStream;

public class IntId1 implements IntIdSet, IntIdList {
    private final int element;

    public IntId1(int element) {
        this.element = element;
    }

    @Override
    public int size() {
        return 1;
    }

    @Override
    public void forEach(IntConsumer consumer) {
        consumer.accept(element);
    }

    @Override
    public IntStream intStream() {
        return IntStream.of(element);
    }

    @Override
    public int[] toArray() {
        return new int[] { element };
    }

    @Override
    public boolean contains(int value) {
        return value == element;
    }

    @Override
    public int get(int index) {
        if (index == 0) {
            return element;
        }
        throw new IndexOutOfBoundsException();
    }
}
