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

import dev.ikm.tinkar.common.id.LongIdCollection;
import dev.ikm.tinkar.common.id.LongIdSet;
import dev.ikm.tinkar.common.service.PrimitiveData;

import java.util.Arrays;
import java.util.function.LongConsumer;
import java.util.stream.LongStream;

/**
 * https://dirtyhandscoding.wordpress.com/2017/08/25/performance-comparison-linear-search-vs-binary-search/
 * <p>The cost of setting up a sort, or a branching structure for a binary search, or a set structure for small sets
 * is greater than just iterating through an array. So I chose to use direct iteration for lookup for lists &lt; 32 elements
 * in size. I don’t think there will ever be a case when the public id has &gt; 32 UUIDs inside.
 */
public class LongIdSetArray
        implements LongIdSet {
    private final long[] elements;

    private LongIdSetArray(long... newElements) {
        this.elements = newElements;
    }

    public static LongIdSetArray newLongIdSet(long... newElements) {
        return new LongIdSetArray(newElements);
    }

    public static LongIdSetArray newLongIdSetAlreadySorted(long... newElements) {
        return new LongIdSetArray(newElements);
    }


    @Override
    public int size() {
        return elements.length;
    }

    @Override
    public LongStream longStream() {
        return LongStream.of(elements);
    }

    @Override
    public boolean contains(long value) {
        // for small lists, iteration is faster search than binary search because of less branching.
        if (elements.length < 32) {
            for (long element : elements) {
                if (value == element) {
                    return true;
                }
            }
            return false;
        }

        long[] clone = elements.clone();
        Arrays.sort(clone);
        return Arrays.binarySearch(clone, value) >= 0;
    }

    @Override
    public boolean isEmpty() {
        return elements.length == 0;
    }

    @Override
    public long[] toArray() {
        return elements;
    }

    @Override
    public void forEach(LongConsumer consumer) {
        for (long element : elements) {
            consumer.accept(element);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof LongIdSet longIdSet) {
            if (elements.length != longIdSet.size()) {
                return false;
            }

            long[] elements1 = elements.clone();
            long[] elements2;
            if (longIdSet instanceof LongIdSetArray longIdSetArray) {
                elements2 = longIdSetArray.elements.clone();
            } else {
                elements2 = longIdSet.toArray().clone();
            }
            Arrays.sort(elements1);
            Arrays.sort(elements2);

            return Arrays.equals(elements1, elements2);
        }
        return false;
    }

    @Override
    public int hashCode() {
        int h = 0;
        for (long element : elements) {
            h += LongIdCollection.hashOf(element);
        }
        return h;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("LongIdSet[");
        for (int i = 0; i < elements.length && i <= TO_STRING_LIMIT; i++) {
            sb.append(PrimitiveData.textWithNid(elements[i])).append(", ");
            if (i == TO_STRING_LIMIT) {
                sb.append("..., ");
            }
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.setCharAt(sb.length() - 1, ']');
        return sb.toString();
    }
}
