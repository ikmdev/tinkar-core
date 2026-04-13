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
 * IntId0 is an optimization for IntList or IntSet of size 0.
 */
public abstract class IntId0 {

    /** Constructs an empty identifier collection. */
    protected IntId0() {}

    /** Shared empty array for zero-element collections. */
    public static final int[] elements = new int[0];

    /**
     * Always throws {@link IndexOutOfBoundsException} since this collection is empty.
     *
     * @param index the index (always invalid)
     * @return never returns normally
     * @throws IndexOutOfBoundsException always
     */
    public int get(int index) {
        throw new IndexOutOfBoundsException("Index: " + index + ", Size: 0");
    }

    /**
     * Returns zero.
     *
     * @return {@code 0}
     */
    public int size() {
        return 0;
    }

    /**
     * Does nothing since this collection is empty.
     *
     * @param consumer the action (not invoked)
     */
    public void forEach(IntConsumer consumer) {
        // nothing to do...
    }

    /**
     * Returns an empty {@link IntStream}.
     *
     * @return an empty stream
     */
    public IntStream intStream() {
        return IntStream.of(elements);
    }

    /**
     * Returns a shared empty array.
     *
     * @return an empty {@code int} array
     */
    public int[] toArray() {
        return elements;
    }

    /**
     * Always returns {@code false} since this collection is empty.
     *
     * @param value the value to test
     * @return {@code false}
     */
    public boolean contains(int value) {
        return false;
    }

    /**
     * Always returns {@code true} since this collection is empty.
     *
     * @return {@code true}
     */
    public boolean isEmpty() {
        return true;
    }

}
