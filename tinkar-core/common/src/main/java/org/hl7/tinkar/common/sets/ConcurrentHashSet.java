package org.hl7.tinkar.common.sets;

import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;

import java.util.Collection;
import java.util.Iterator;
import java.util.Objects;
import java.util.Set;

public class ConcurrentHashSet<T extends Object> implements Set<T> {
    final ConcurrentHashMap<T, T> hashMap;

    public ConcurrentHashSet(int initialSize) {
        this.hashMap = ConcurrentHashMap.newMap(initialSize);
    }

    public ConcurrentHashSet() {
        this.hashMap = ConcurrentHashMap.newMap();
    }

    @Override
    public int size() {
        return hashMap.size();
    }

    @Override
    public boolean isEmpty() {
        return hashMap.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        return hashMap.containsKey(o);
    }

    @Override
    public Iterator<T> iterator() {
        return hashMap.iterator();
    }

    @Override
    public Integer[] toArray() {
        return hashMap.toArray(new Integer[hashMap.size()]);
    }

    @Override
    public <T> T[] toArray(T[] a) {
        return hashMap.toArray(a);
    }

    @Override
    public boolean add(T value) {
        return null == hashMap.put(value, value);
    }

    @Override
    public boolean remove(Object o) {
        Object existingObject = hashMap.remove(o);
        return Objects.equals(o, existingObject);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return hashMap.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends T> c) {
        boolean changed = false;
        for (T value : c) {
            if (hashMap.put(value, value) == null) {
                changed = true;
            }
        }
        return changed;
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
        hashMap.clear();
    }
}
