/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.common.id.impl;

import dev.ikm.tinkar.common.id.LongIdSet;
import dev.ikm.tinkar.common.service.PrimitiveData;
import org.roaringbitmap.longlong.Roaring64Bitmap;

import java.util.Arrays;
import java.util.function.LongConsumer;
import java.util.stream.LongStream;

/**
 * A large {@link LongIdSet}, as a 64-bit roaring bitmap. Its order is unsigned, as the 32-bit
 * bitmap's was, so a set of nids widened from {@code int} iterates in the order it did before:
 * the non-negative ids ascending, then the negative ones ascending.
 */
public class LongIdSetRoaring extends Roaring64Bitmap implements LongIdSet {
    private LongIdSetRoaring() {
    }

    public static LongIdSet newLongIdSet(long... newElements) {
        Arrays.sort(newElements);
        LongIdSetRoaring roaring = new LongIdSetRoaring();
        roaring.add(newElements);
        return roaring;
    }

    public static LongIdSet newLongIdSetAlreadySorted(long... newElements) {
        LongIdSetRoaring roaring = new LongIdSetRoaring();
        roaring.add(newElements);
        return roaring;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof LongIdSet longIdSet) {
            if (this.size() != longIdSet.size()) {
                return false;
            }
            if (longIdSet instanceof LongIdSetRoaring longIdSetRoaring) {
                return super.equals(longIdSetRoaring);
            }
            long[] elements1 = this.toArray();
            Arrays.sort(elements1);
            long[] elements2 = longIdSet.toArray().clone();
            Arrays.sort(elements2);
            return Arrays.equals(elements1, elements2);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("LongIdSet[");
        boolean limited = size() > TO_STRING_LIMIT;
        longStream().limit(TO_STRING_LIMIT).forEach(nid -> {
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
        return this.getIntCardinality();
    }

    @Override
    public LongStream longStream() {
        return LongStream.of(toArray());
    }

    @Override
    public void forEach(LongConsumer consumer) {
        forEach(new ConsumerAdaptor(consumer));
    }

    private static class ConsumerAdaptor implements org.roaringbitmap.longlong.LongConsumer {
        final LongConsumer adaptee;

        ConsumerAdaptor(LongConsumer adaptee) {
            this.adaptee = adaptee;
        }

        @Override
        public void accept(long value) {
            this.adaptee.accept(value);
        }
    }
}
