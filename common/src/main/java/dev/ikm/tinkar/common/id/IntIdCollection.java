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
package dev.ikm.tinkar.common.id;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

/**
 * A collection of primitive {@code int} identifiers, extending {@link IdCollection} with
 * stream, containment, and mapping operations.
 */
public interface IntIdCollection extends IdCollection {

    /**
     * Returns a sequential {@link IntStream} over the identifiers in this collection.
     *
     * @return an {@code IntStream} of the identifiers
     */
    IntStream intStream();

    /**
     * Returns {@code true} if this collection contains the specified value.
     *
     * @param value the value to test for membership
     * @return {@code true} if the value is present
     */
    boolean contains(int value);

    /**
     * Returns {@code true} if this collection is not empty.
     *
     * @return {@code true} if {@link #size()} is greater than zero
     */
    default boolean notEmpty() {
        return !isEmpty();
    }

    /**
     * Returns {@code true} if this collection contains no identifiers.
     *
     * @return {@code true} if this collection is empty
     */
    boolean isEmpty();

    /**
     * Maps each identifier to an object using the given function and returns the results as an array.
     *
     * @param <T> the element type of the resulting array
     * @param function the mapping function from {@code int} to {@code T}
     * @param clazz the component type of the resulting array
     * @return an array containing the mapped values
     */
    default <T extends Object> T[] mapToArray(IntFunction<T> function, Class<T> clazz) {
        T[] array = (T[]) Array.newInstance(clazz, size());
        int[] nids = toArray();
        for (int i = 0; i < array.length; i++) {
            array[i] = function.apply(nids[i]);
        }
        return array;
    }

    /**
     * Returns the identifiers in this collection as a primitive {@code int} array.
     *
     * @return an array of the identifiers
     */
    int[] toArray();

    /**
     * Maps each identifier to an object using the given function and returns the results as a {@link List}.
     *
     * @param <T> the element type of the resulting list
     * @param function the mapping function from {@code int} to {@code T}
     * @return a list containing the mapped values
     */
    default <T extends Object> List<T> mapToList(IntFunction<T> function) {
        ArrayList<T> list = new ArrayList<>(size());
        forEach(nid -> list.add(function.apply(nid)));
        return list;
    }

    /**
     * Performs the given action for each identifier in this collection.
     *
     * @param consumer the action to perform for each identifier
     */
    void forEach(IntConsumer consumer);

    /**
     * Maps each identifier to an object using the given function and returns the results as a {@link Set}.
     *
     * @param <T> the element type of the resulting set
     * @param function the mapping function from {@code int} to {@code T}
     * @return a set containing the mapped values
     */
    default <T extends Object> Set<T> mapToSet(IntFunction<T> function) {
        HashSet<T> set = new HashSet<>(size());
        forEach(nid -> set.add(function.apply(nid)));
        return set;
    }

    /**
     * Returns a new collection containing all identifiers in this collection plus the specified values.
     *
     * @param valuesToAdd the values to add
     * @return a new collection with the additional values
     */
    IntIdCollection with(int... valuesToAdd);


}
