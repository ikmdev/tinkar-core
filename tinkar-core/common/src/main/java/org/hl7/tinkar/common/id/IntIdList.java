package org.hl7.tinkar.common.id;


import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;

import java.util.function.IntFunction;

public interface IntIdList extends IdList, IntIdCollection {
    default <T extends Object> ImmutableList<T> map(IntFunction<T> function) {
        MutableList<T> list = Lists.mutable.ofInitialCapacity(size());
        for (int i = 0; i < size(); i++) {
            list.add(function.apply(get(i)));
        }
        return list.toImmutable();
    }

    int get(int index);

    default boolean notEmpty() {
        return !this.isEmpty();
    }

    boolean isEmpty();

    default IntIdList with(int... valuesToAdd) {
        return IntIds.list.of(this, valuesToAdd);
    }


}
