package org.hl7.tinkar.common.id;

import java.util.Collection;
import java.util.function.ToIntFunction;

public interface IntIdSetFactory {
    IntIdSet empty();

    IntIdSet of();

    IntIdSet of(int one);

    IntIdSet of(int one, int two);

    IntIdSet ofAlreadySorted(int... elements);

    IntIdSet of(IntIdSet ids, int... elements);

    default <T> IntIdSet of(Collection<T> components, ToIntFunction<T> function) {
        return of(components.stream().mapToInt(component -> function.applyAsInt(component)).toArray());
    }

    IntIdSet of(int... elements);
}
