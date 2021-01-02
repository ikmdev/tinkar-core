package org.hl7.tinkar.service;

import org.eclipse.collections.api.block.procedure.Procedure2;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.ListIterable;
import org.hl7.tinkar.util.UuidUtil;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;

public interface PrimitiveDataService {
    /**
     * If the specified nid (native identifier -- an int) is not already associated with a value or is
     * associated with null, associates it with the given non-null value.
     * Otherwise, replaces the associated value with the results of the given
     * remapping function, or removes if the result is {@code null}. This
     * method may be of use when combining multiple mapped values for a nid.
     * For example, to either create or append a {@code String msg} to a
     * value mapping:
     *
     * <pre> {@code
     * map.merge(nid, msg, String::concat)
     * }</pre>
     *
     * <p>If the remapping function returns {@code null}, the mapping is removed.
     * If the remapping function itself throws an (unchecked) exception, the
     * exception is rethrown, and the current mapping is left unchanged.
     *
     * <p>The remapping function should not modify this map during computation.
     *
     * @implSpec
     * The default implementation is equivalent to performing the following
     * steps for this {@code map}, then returning the current value or
     * {@code null} if absent:
     *
     * <pre> {@code
     * V oldValue = map.get(nid);
     * V newValue = (oldValue == null) ? value :
     *              remappingFunction.apply(oldValue, value);
     * if (newValue == null)
     *     map.remove(nid);
     * else
     *     map.put(nid, newValue);
     * }</pre>
     *
     * <p>The default implementation makes no guarantees about detecting if the
     * remapping function modifies this map during computation and, if
     * appropriate, reporting an error. Non-concurrent implementations should
     * override this method and, on a best-effort basis, throw a
     * {@code ConcurrentModificationException} if it is detected that the
     * remapping function modifies this map during computation. Concurrent
     * implementations should override this method and, on a best-effort basis,
     * throw an {@code IllegalStateException} if it is detected that the
     * remapping function modifies this map during computation and as a result
     * computation would never complete.
     *
     * <p>The default implementation makes no guarantees about synchronization
     * or atomicity properties of this method. Any implementation providing
     * atomicity guarantees must override this method and document its
     * concurrency properties. In particular, all implementations of
     * subinterface {@link java.util.concurrent.ConcurrentMap} must document
     * whether the remapping function is applied once atomically only if the
     * value is not present.
     *
     * @param nid native identifier (an int) with which the resulting value is to be associated
     * @param value the non-null value to be merged with the existing value
     *        associated with the nid or, if no existing value or a null value
     *        is associated with the nid, to be associated with the nid
     * @param remappingFunction the remapping function to recompute a value if
     *        present
     * @return the new value associated with the specified nid, or null if no
     *         value is associated with the nid
     * @throws UnsupportedOperationException if the {@code put} operation
     *         is not supported by this map
     *         (<a href="{@docRoot}/java.base/java/util/Collection.html#optional-restrictions">optional</a>)
     * @throws ClassCastException if the class of the specified nid or value
     *         prevents it from being stored in this map
     *         (<a href="{@docRoot}/java.base/java/util/Collection.html#optional-restrictions">optional</a>)
     * @throws IllegalArgumentException if some property of the specified nid
     *         or value prevents it from being stored in this map
     *         (<a href="{@docRoot}/java.base/java/util/Collection.html#optional-restrictions">optional</a>)
     * @throws NullPointerException if the specified nid is null and this map
     *         does not support null nids or the value or remappingFunction is
     *         null
     * @since 1.8
     */
    byte[] merge(int nid, byte[] value,
                 BiFunction<byte[], byte[], byte[]> remappingFunction);

    void forEach(BiConsumer<Integer, byte[]> action);

    void forEachParallel(Procedure2<Integer, byte[]> action);

    byte[] getBytes(int nid);

    ConcurrentMap<UUID, Integer> uuidNidMap();
    AtomicInteger nextNid();

    default int valueOrGenerateForList(ListIterable<UUID> sortedUuidList) {
        boolean missingMap = false;
        int foundValue = Integer.MIN_VALUE;

        for (UUID uuid: sortedUuidList) {
            Integer nid = uuidNidMap().get(uuid);
            if (nid == null) {
                missingMap = true;
            } else {
                if (foundValue == Integer.MIN_VALUE) {
                    foundValue = nid;
                } else {
                    if (foundValue != nid) {
                        StringBuilder sb = new StringBuilder();
                        sb.append("Multiple nids for: ");
                        sb.append(sortedUuidList);
                        sb.append(" first value: ").append(foundValue);
                        sb.append(" second value: ").append(nid);
                        throw new IllegalStateException(sb.toString());
                    }
                }
            }
        }
        if (!missingMap) {
            return foundValue;
        }
        if (foundValue == Integer.MIN_VALUE) {
            foundValue = valueOrGenerateAndPut(sortedUuidList.get(0));
        }
        for (UUID uuid: sortedUuidList) {
            uuidNidMap().put(uuid, foundValue);
        }
        return foundValue;
    }

    default int valueOrGenerateAndPut(UUID uuid) {
        Integer nid = uuidNidMap().get(uuid);
        if (nid != null) {
            return nid;
        }
        nid = uuidNidMap().computeIfAbsent(uuid, uuidKey -> nextNid().getAndIncrement());
        return nid;
    }

    default int nidForUuids(UUID... uuids) {
        switch (uuids.length) {
            case 0:
                throw new IllegalStateException("uuidList cannot be empty");
            case 1:
                return valueOrGenerateAndPut(uuids[0]);
        }
        Arrays.sort(uuids);
        return valueOrGenerateForList(Lists.immutable.of(uuids));
    }

    default int nidForUuids(ImmutableList<UUID> uuidList) {
        switch (uuidList.size()) {
            case 0:
                throw new IllegalStateException("uuidList cannot be empty");
            case 1: {
                return valueOrGenerateAndPut(uuidList.get(0));
            }
        }
        return valueOrGenerateForList(uuidList.toSortedList());
    }

    void close();

}
