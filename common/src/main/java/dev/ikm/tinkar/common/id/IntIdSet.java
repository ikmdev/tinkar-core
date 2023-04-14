package dev.ikm.tinkar.common.id;


import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.set.ImmutableSet;
import org.eclipse.collections.api.set.MutableSet;

import java.util.function.IntFunction;

public interface IntIdSet extends IdSet, IntIdCollection {

    default <T extends Object> ImmutableSet<T> map(IntFunction<T> function) {
        MutableSet<T> set = Sets.mutable.ofInitialCapacity(size());
        for (int nid : toArray()) {
            set.add(function.apply(nid));
        }
        return set.toImmutable();
    }

    default IntIdSet with(int... valuesToAdd) {
        return IntIds.set.of(this, valuesToAdd);
    }

}
