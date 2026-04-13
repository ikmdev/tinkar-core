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
package dev.ikm.tinkar.common.sets;

import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A thread-safe {@link Set} implementation backed by a {@link ConcurrentHashMap} key set.
 * This provides concurrent access semantics without requiring external synchronization.
 *
 * <p>Note: {@link #retainAll(Collection)} and {@link #removeAll(Collection)} are not supported
 * and will throw {@link UnsupportedOperationException}.
 *
 * @param <T> the type of elements maintained by this set
 */
public class ConcurrentHashSet<T extends Object> implements Set<T> {
     final ConcurrentHashMap.KeySetView<T,Boolean> keySet;

    /**
     * Constructs an empty {@code ConcurrentHashSet} with the specified initial capacity.
     *
     * @param initialSize the initial capacity of the backing map
     */
    public ConcurrentHashSet(int initialSize) {
        this.keySet = ConcurrentHashMap.newKeySet(initialSize);
    }

    /**
     * Constructs an empty {@code ConcurrentHashSet} with the default initial capacity.
     */
    public ConcurrentHashSet() {
        this.keySet = ConcurrentHashMap.newKeySet();
    }

    /**
     * Constructs a {@code ConcurrentHashSet} containing all elements from the given enumeration.
     *
     * @param keys the enumeration of elements to add to this set
     */
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
    public T[] toArray() {
        return keySet.toArray((T[]) new Object[keySet.size()]);
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
