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
 * Base class for two-element {@code int} identifier collections.
 */
public class IntId2 {
    /** The first element. */
    protected final int element;
    /** The second element. */
    protected final int element2;

    /**
     * Constructs a two-element collection.
     *
     * @param element the first element
     * @param element2 the second element
     */
    public IntId2(int element, int element2) {
        this.element = element;
        this.element2 = element2;
    }

    /**
     * Returns two.
     *
     * @return {@code 2}
     */
    public int size() {
        return 2;
    }

    /**
     * Performs the given action on both elements.
     *
     * @param consumer the action to perform
     */
    public void forEach(IntConsumer consumer) {
        consumer.accept(element);
        consumer.accept(element2);
    }

    /**
     * Returns a stream containing both elements.
     *
     * @return a stream of two elements
     */
    public IntStream intStream() {
        return IntStream.of(element, element2);
    }

    /**
     * Returns a two-element array.
     *
     * @return an array containing both elements
     */
    public int[] toArray() {
        return new int[]{element, element2};
    }

    /**
     * Returns {@code true} if the given value equals either element.
     *
     * @param value the value to test
     * @return {@code true} if the value matches either element
     */
    public boolean contains(int value) {
        if (value == element) {
            return true;
        }
        return value == element2;
    }
}
