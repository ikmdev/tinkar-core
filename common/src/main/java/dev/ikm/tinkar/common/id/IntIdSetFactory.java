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
 * Factory for creating {@link IntIdSet} instances with various element counts.
 */
public interface IntIdSetFactory {
    /**
     * Returns an empty {@link IntIdSet}.
     *
     * @return an empty set
     */
    IntIdSet empty();

    /**
     * Returns an empty {@link IntIdSet}.
     *
     * @return an empty set
     */
    IntIdSet of();

    /**
     * Returns an {@link IntIdSet} containing a single element.
     *
     * @param one the sole element
     * @return a set containing the element
     */
    IntIdSet of(int one);

    /**
     * Returns an {@link IntIdSet} containing two elements.
     *
     * @param one the first element
     * @param two the second element
     * @return a set containing the two elements
     */
    IntIdSet of(int one, int two);

    /**
     * Returns an {@link IntIdSet} from elements that are already sorted.
     *
     * @param elements the pre-sorted elements
     * @return a set containing the elements
     */
    IntIdSet ofAlreadySorted(int... elements);

    /**
     * Returns an {@link IntIdSet} containing all elements from the given set plus the additional
     * elements.
     *
     * @param ids the existing set
     * @param elements additional elements to include
     * @return a new set containing all elements
     */
    IntIdSet of(IntIdSet ids, int... elements);

    /**
     * Returns an {@link IntIdSet} by extracting an {@code int} from each element of the given
     * collection using the provided function.
     *
     * @param <T> the element type of the source collection
     * @param components the source collection
     * @param function the function to extract an {@code int} from each element
     * @return a set containing the extracted identifiers
     */
    default <T> IntIdSet of(Collection<T> components, ToIntFunction<T> function) {
        return of(components.stream().mapToInt(component -> function.applyAsInt(component)).toArray());
    }

    /**
     * Returns an {@link IntIdSet} containing the given elements.
     *
     * @param elements the elements for the set
     * @return a set containing the elements
     */
    IntIdSet of(int... elements);
}
