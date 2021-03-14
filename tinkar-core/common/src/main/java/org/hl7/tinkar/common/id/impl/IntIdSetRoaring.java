package org.hl7.tinkar.common.id.impl;

import org.hl7.tinkar.common.id.IntIdSet;
import org.roaringbitmap.RoaringBitmap;
import java.util.Arrays;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

public class IntIdSetRoaring extends RoaringBitmap implements IntIdSet {
    private IntIdSetRoaring() {
    }

    public static IntIdSet newIntIdSet(int... newElements) {
        Arrays.sort(newElements);
        IntIdSetRoaring roaring = new IntIdSetRoaring();
        roaring.add(newElements);
        return roaring;
    }

    public static IntIdSet newIntIdSetAlreadySorted(int... newElements) {
        IntIdSetRoaring roaring = new IntIdSetRoaring();
        roaring.add(newElements);
        return roaring;
    }

    @Override
    public int size() {
        return this.getCardinality();
    }

    @Override
    public void forEach(IntConsumer consumer) {
        forEach(new ConsumerAdaptor(consumer));
    }

    @Override
    public IntStream intStream() {
        return stream();
    }

    private static class ConsumerAdaptor implements org.roaringbitmap.IntConsumer {
        java.util.function.IntConsumer adaptee;

        public ConsumerAdaptor(IntConsumer adaptee) {
            this.adaptee = adaptee;
        }

        @Override
        public void accept(int value) {
            this.adaptee.accept(value);
        }
    }
}
