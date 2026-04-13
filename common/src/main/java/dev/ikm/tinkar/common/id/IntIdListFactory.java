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

import java.util.Collection;
import java.util.function.ToIntFunction;

/**
 * Factory for creating {@link IntIdList} instances with various element counts.
 */
public interface IntIdListFactory {
    /**
     * Returns an empty {@link IntIdList}.
     *
     * @return an empty list
     */
    IntIdList empty();

    /**
     * Returns an empty {@link IntIdList}.
     *
     * @return an empty list
     */
    IntIdList of();

    /**
     * Returns an {@link IntIdList} containing a single element.
     *
     * @param one the sole element
     * @return a list containing the element
     */
    IntIdList of(int one);

    /**
     * Returns an {@link IntIdList} containing two elements.
     *
     * @param one the first element
     * @param two the second element
     * @return a list containing the two elements in order
     */
    IntIdList of(int one, int two);

    /**
     * Returns an {@link IntIdList} containing all elements from the given list followed by the
     * additional elements.
     *
     * @param list the existing list
     * @param elements additional elements to append
     * @return a new list containing all elements
     */
    IntIdList of(IntIdList list, int... elements);

    /**
     * Returns an {@link IntIdList} by extracting an {@code int} from each element of the given
     * collection using the provided function.
     *
     * @param <T> the element type of the source collection
     * @param components the source collection
     * @param function the function to extract an {@code int} from each element
     * @return a list containing the extracted identifiers
     */
    default <T> IntIdList of(Collection<T> components, ToIntFunction<T> function) {
        return of(components.stream().mapToInt(component -> function.applyAsInt(component)).toArray());
    }

    /**
     * Returns an {@link IntIdList} containing the given elements.
     *
     * @param elements the elements for the list
     * @return a list containing the elements in order
     */
    IntIdList of(int... elements);

}
