package dev.ikm.tinkar.common.id;

import java.util.Collection;
import java.util.function.ToIntFunction;

public interface IntIdListFactory {
    IntIdList empty();

    IntIdList of();

    IntIdList of(int one);

    IntIdList of(int one, int two);

    IntIdList of(IntIdList list, int... elements);

    default <T> IntIdList of(Collection<T> components, ToIntFunction<T> function) {
        return of(components.stream().mapToInt(component -> function.applyAsInt(component)).toArray());
    }

    IntIdList of(int... elements);

}
