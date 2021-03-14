package org.hl7.tinkar.common.util.id.impl;

import java.util.function.IntConsumer;
import java.util.stream.IntStream;

public class IntId1  {
    private final int element;

    public IntId1(int element) {
        this.element = element;
    }

    public int size() {
        return 1;
    }

    public void forEach(IntConsumer consumer) {
        consumer.accept(element);
    }

    public IntStream intStream() {
        return IntStream.of(element);
    }

    public int[] toArray() {
        return new int[] { element };
    }

    public boolean contains(int value) {
        return value == element;
    }

    public int get(int index) {
        if (index == 0) {
            return element;
        }
        throw new IndexOutOfBoundsException();
    }
}
