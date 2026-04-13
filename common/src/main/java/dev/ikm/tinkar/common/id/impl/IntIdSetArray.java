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

import dev.ikm.tinkar.common.id.IntIdSet;
import dev.ikm.tinkar.common.service.PrimitiveData;

import java.util.Arrays;
import java.util.function.IntConsumer;
import java.util.stream.IntStream;

/**
 * https://dirtyhandscoding.wordpress.com/2017/08/25/performance-comparison-linear-search-vs-binary-search/
 * <p>The cost of setting up a sort, or a branching structure for a binary search, or a set structure for small sets
 * is greater than just iterating through an array. So I chose to use direct iteration for lookup for lists &lt; 32 elements
 * in size. I don’t think there will ever be a case when the public id has &gt; 32 UUIDs inside.
 */
public class IntIdSetArray
        implements IntIdSet {
    /** The backing array of elements. */
    private final int[] elements;

    /**
     * Constructs a set backed by the given array.
     *
     * @param newElements the elements for this set
     */
    private IntIdSetArray(int... newElements) {
        this.elements = newElements;
    }

    /**
     * Creates a new {@code IntIdSetArray} from the given elements.
     *
     * @param newElements the elements
     * @return a new set containing the elements
     */
    public static IntIdSetArray newIntIdSet(int... newElements) {
        return new IntIdSetArray(newElements);
    }

    /**
     * Creates a new {@code IntIdSetArray} from elements that are already sorted.
     *
     * @param newElements the pre-sorted elements
     * @return a new set containing the elements
     */
    public static IntIdSetArray newIntIdSetAlreadySorted(int... newElements) {
        return new IntIdSetArray(newElements);
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return elements.length;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public IntStream intStream() {
        return IntStream.of(elements);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean contains(int value) {
        // for small lists, iteration is faster search than binary search because of less branching.
        if (elements.length < 32) {
            for (int element : elements) {
                if (value == element) {
                    return true;
                }
            }
            return false;
        }

        int[] clone = elements.clone();
        Arrays.sort(clone);
        return Arrays.binarySearch(clone, value) >= 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEmpty() {
        return elements.length == 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int[] toArray() {
        return elements;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void forEach(IntConsumer consumer) {
        for (int element : elements) {
            consumer.accept(element);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj instanceof IntIdSet intIdSet) {
            if (elements.length != intIdSet.size()) {
                return false;
            }

            int[] elements1 = elements.clone();
            int[] elements2;
            if (intIdSet instanceof IntIdSetArray intIdSetArray) {
                elements2 = intIdSetArray.elements.clone();
            } else {
                elements2 = intIdSet.toArray().clone();
            }
            Arrays.sort(elements1);
            Arrays.sort(elements2);

            return Arrays.equals(elements1, elements2);
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int hashCode() {
        int h = 0;
        for (int element : elements) {
            h += element;
        }
        return h;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("IntIdSet[");
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
