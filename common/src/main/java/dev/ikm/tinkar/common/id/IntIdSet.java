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


import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.MutableSet;

import java.util.function.IntFunction;

/**
 * A set of unique primitive {@code int} identifiers with no guaranteed ordering.
 */
public interface IntIdSet extends IdSet, IntIdCollection {

    /**
     * Maps each identifier to an object using the given function and returns the results
     * as an {@link ImmutableSet}.
     *
     * @param <T> the element type of the resulting set
     * @param function the mapping function from {@code int} to {@code T}
     * @return an immutable set containing the mapped values
     */
    default <T extends Object> ImmutableSet<T> map(IntFunction<T> function) {
        MutableSet<T> set = Sets.mutable.ofInitialCapacity(size());
        for (int nid : toArray()) {
            set.add(function.apply(nid));
        }
        return set.toImmutable();
    }

    /**
     * {@inheritDoc}
     */
    default IntIdSet with(int... valuesToAdd) {
        return IntIds.set.of(this, valuesToAdd);
    }

}
