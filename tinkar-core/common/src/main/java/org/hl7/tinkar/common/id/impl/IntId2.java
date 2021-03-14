package org.hl7.tinkar.common.id.impl;

import java.util.function.IntConsumer;
import java.util.stream.IntStream;

public class IntId2 {
    protected final int element;
    protected final int element2;

    public IntId2(int element, int element2) {
        if (element == element2) {
            throw new IllegalStateException("Duplicate values in set: " + element);
        }
        this.element = element;
        this.element2 = element2;
    }

    public int size() {
        return 2;
    }

    public void forEach(IntConsumer consumer) {
        consumer.accept(element);
        consumer.accept(element2);
    }

    public IntStream intStream() {
        return IntStream.of(element, element2);
    }

    public int[] toArray() {
        return new int[] { element, element2 };
    }

    public boolean contains(int value) {
        if (value == element) {
            return true;
        }
        return value == element2;
    }
}
