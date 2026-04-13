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


import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;

import java.util.function.IntFunction;

/**
 * An ordered list of primitive {@code int} identifiers that preserves insertion order
 * and allows duplicates.
 */
public interface IntIdList extends IdList, IntIdCollection {
    /**
     * Maps each identifier to an object using the given function and returns the results
     * as an {@link ImmutableList}.
     *
     * @param <T> the element type of the resulting list
     * @param function the mapping function from {@code int} to {@code T}
     * @return an immutable list containing the mapped values
     */
    default <T extends Object> ImmutableList<T> map(IntFunction<T> function) {
        MutableList<T> list = Lists.mutable.ofInitialCapacity(size());
        for (int i = 0; i < size(); i++) {
            list.add(function.apply(get(i)));
        }
        return list.toImmutable();
    }

    /**
     * Returns the identifier at the specified index.
     *
     * @param index the zero-based index of the identifier to return
     * @return the identifier at the specified index
     * @throws IndexOutOfBoundsException if the index is out of range
     */
    int get(int index);

    /**
     * {@inheritDoc}
     */
    default boolean notEmpty() {
        return !this.isEmpty();
    }

    /**
     * {@inheritDoc}
     */
    boolean isEmpty();

    /**
     * {@inheritDoc}
     */
    default IntIdList with(int... valuesToAdd) {
        return IntIds.list.of(this, valuesToAdd);
    }


}
