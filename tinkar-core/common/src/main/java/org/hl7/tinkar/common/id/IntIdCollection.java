package org.hl7.tinkar.common.id;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

public interface IntIdCollection extends IdCollection {

    IntStream intStream();

    boolean contains(int value);

    default boolean notEmpty() {
        return !isEmpty();
    }

    boolean isEmpty();

    default <T extends Object> T[] mapToArray(IntFunction<T> function, Class<T> clazz) {
        T[] array = (T[]) Array.newInstance(clazz, size());
        int[] nids = toArray();
        for (int i = 0; i < array.length; i++) {
            array[i] = function.apply(nids[i]);
        }
        return array;
    }

    int[] toArray();

    default <T extends Object> List<T> mapToList(IntFunction<T> function) {
        ArrayList<T> list = new ArrayList<>(size());
        forEach(nid -> list.add(function.apply(nid)));
        return list;
    }

    void forEach(IntConsumer consumer);

    default <T extends Object> Set<T> mapToSet(IntFunction<T> function) {
        HashSet<T> set = new HashSet<>(size());
        forEach(nid -> set.add(function.apply(nid)));
        return set;
    }

}
