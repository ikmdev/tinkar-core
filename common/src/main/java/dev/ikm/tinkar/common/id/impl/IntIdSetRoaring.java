package dev.ikm.tinkar.common.id.impl;

import dev.ikm.tinkar.common.id.IntIdSet;
import dev.ikm.tinkar.common.service.PrimitiveData;
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
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof IntIdSet intIdSet) {
            if (this.size() != intIdSet.size()) {
                return false;
            }
            if (intIdSet instanceof IntIdSetRoaring intIdSetRoaring) {
                return IntIdSetRoaring.this.equals(intIdSetRoaring);
            }
            int[] elements1 = this.toArray();
            Arrays.sort(elements1);
            int[] elements2 = intIdSet.toArray();
            Arrays.sort(elements2);


            return Arrays.equals(elements1, elements2);
        }
        return false;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("IntIdSet[");
        boolean limited = size() > TO_STRING_LIMIT;

        intStream().limit(TO_STRING_LIMIT).forEach(nid -> {
            sb.append(PrimitiveData.textWithNid(nid)).append(", ");
        });
        if (limited) {
            sb.append("..., ");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.setCharAt(sb.length() - 1, ']');
        return sb.toString();
    }

    @Override
    public int size() {
        return this.getCardinality();
    }

    @Override
    public IntStream intStream() {
        return stream();
    }

    @Override
    public void forEach(IntConsumer consumer) {
        forEach(new ConsumerAdaptor(consumer));
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
