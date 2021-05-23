package org.hl7.tinkar.common.id;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.iterator.IntIterator;
import org.eclipse.collections.api.list.MutableList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.stream.IntStream;

public interface IntIdCollection extends IdCollection {

    void forEach(IntConsumer consumer);

    IntStream intStream();

    int[] toArray();

    boolean contains(int value);

    boolean isEmpty();

    default boolean notEmpty() {
        return !isEmpty();
    }


    default <T extends Object> List<T> mapToList(IntFunction<T> function) {
        ArrayList<T> list = new ArrayList<>(size());
        forEach(nid -> list.add(function.apply(nid)));
        return list;
    }

    default <T extends Object> Set<T> mapToSet(IntFunction<T> function) {
        HashSet<T> set = new HashSet<>(size());
        forEach(nid -> set.add(function.apply(nid)));
        return set;
    }

}
