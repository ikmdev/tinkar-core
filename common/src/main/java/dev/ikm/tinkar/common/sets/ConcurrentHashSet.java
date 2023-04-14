package dev.ikm.tinkar.common.sets;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class ConcurrentHashSet<T extends Object> implements Set<T> {
     final ConcurrentHashMap.KeySetView<T,Boolean> keySet;

    public ConcurrentHashSet(int initialSize) {
        this.keySet = ConcurrentHashMap.newKeySet(initialSize);
    }

    public ConcurrentHashSet() {
        this.keySet = ConcurrentHashMap.newKeySet();
    }

    public ConcurrentHashSet(Enumeration<T> keys) {
        this.keySet = ConcurrentHashMap.newKeySet();
        while (keys.hasMoreElements()) {
            T value = keys.nextElement();
            keySet.add(value);
        }
    }

    @Override
    public int size() {
        return keySet.size();
    }

    @Override
    public boolean isEmpty() {
        return keySet.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return keySet.contains(o);
    }

    @Override
    public Iterator<T> iterator() {
        return keySet.iterator();
    }

    @Override
    public Integer[] toArray() {
        return keySet.toArray(new Integer[keySet.size()]);
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return keySet.toArray(a);
    }

    @Override
    public boolean add(T value) {
        return keySet.add(value);
    }

    @Override
    public boolean remove(Object o) {
        return keySet.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return keySet.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        return keySet.addAll(c);
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void clear() {
        keySet.clear();
    }
}
