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

import java.util.function.IntConsumer;
import java.util.stream.IntStream;

/**
 * Base class for single-element {@code int} identifier collections.
 */
public class IntId1 {
    /** The single element in this collection. */
    protected final int element;

    /**
     * Constructs a single-element collection.
     *
     * @param element the sole element
     */
    public IntId1(int element) {
        this.element = element;
    }

    /**
     * Returns one.
     *
     * @return {@code 1}
     */
    public int size() {
        return 1;
    }

    /**
     * Performs the given action on the single element.
     *
     * @param consumer the action to perform
     */
    public void forEach(IntConsumer consumer) {
        consumer.accept(element);
    }

    /**
     * Returns a stream containing the single element.
     *
     * @return a stream of one element
     */
    public IntStream intStream() {
        return IntStream.of(element);
    }

    /**
     * Returns a single-element array.
     *
     * @return an array containing the element
     */
    public int[] toArray() {
        return new int[]{element};
    }

    /**
     * Returns {@code true} if the given value equals the single element.
     *
     * @param value the value to test
     * @return {@code true} if the value matches
     */
    public boolean contains(int value) {
        return value == element;
    }

    /**
     * Returns the element at the specified index; only index zero is valid.
     *
     * @param index the index (must be zero)
     * @return the element
     * @throws IndexOutOfBoundsException if the index is not zero
     */
    public int get(int index) {
        if (index == 0) {
            return element;
        }
        throw new IndexOutOfBoundsException();
    }

    /**
     * Always returns {@code false} since this collection contains one element.
     *
     * @return {@code false}
     */
    public boolean isEmpty() {
        return false;
    }

}
