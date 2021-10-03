package org.hl7.tinkar.provider.spinedarray;

import org.eclipse.collections.impl.map.mutable.ConcurrentHashMap;

import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

public class ConcurrentIntegerHashSet implements Set<Integer> {
    final ConcurrentHashMap<Integer, Integer> hashMap;

    public ConcurrentIntegerHashSet(int initialSize) {
        this.hashMap = ConcurrentHashMap.newMap(initialSize);
    }

    public ConcurrentIntegerHashSet() {
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
    public Iterator<Integer> iterator() {
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
    public boolean add(Integer integer) {
        return null == hashMap.put(integer, integer);
    }

    @Override
    public boolean remove(Object o) {
        return o == hashMap.remove(o);
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return hashMap.containsAll(c);
    }

    @Override
    public boolean addAll(Collection<? extends Integer> c) {
        boolean changed = false;
        for (Integer integer : c) {
            if (hashMap.put(integer, integer) == null) {
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
