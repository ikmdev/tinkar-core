package org.hl7.tinkar.service;

import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import org.eclipse.collections.api.block.procedure.Procedure2;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;

public interface PrimitiveDataService {

    enum RemoteOperations {
        NID_FOR_UUIDS(1),
        GET_BYTES(2),
        MERGE(3);

        public final byte token;

        RemoteOperations(int token) {
            this.token = (byte) token;
        }

        public static RemoteOperations fromToken(byte token) {
            switch (token) {
                case 1:
                    return NID_FOR_UUIDS;
                case 2:
                    return GET_BYTES;
                case 3:
                    return MERGE;
                default:throw new UnsupportedOperationException("Can't handle token: " + token);
            }
        }
    }

    int nidForUuids(UUID... uuids);

    int nidForUuids(ImmutableList<UUID> uuidList);

    void forEach(BiConsumer<Integer, byte[]> action);

    void forEachParallel(Procedure2<Integer, byte[]> action);

    byte[] getBytes(int nid);

    void close();

    /**
     * If the specified nid (native identifier -- an int) is not already associated
     * with a value or is associated with null, associates it with the given non-null value.
     * Otherwise, replaces the associated value with the results of a remapping function
     * (remapping function is provided the provider), or removes if the result is {@code null}.
     * This method may be of use when combining multiple mapped values for a nid.
     * For example, merging multiple versions of an entity, where each version is represented as a
     * byte[].
     *
     *
     * @param nid native identifier (an int) with which the resulting value is to be associated
     * @param value the non-null value to be merged with the existing value
     *        associated with the nid or, if no existing value or a null value
     *        is associated with the nid, to be associated with the nid
     * @return the new value associated with the specified nid, or null if no
     *         value is associated with the nid
     */
    byte[] merge(int nid, byte[] value);


    static int valueOrGenerateForList(ListIterable<UUID> sortedUuidList,
                                      ConcurrentMap<UUID, Integer> uuidNidMap,
                                      AtomicInteger nextNid) {
        boolean missingMap = false;
        int foundValue = Integer.MIN_VALUE;

        for (UUID uuid: sortedUuidList) {
            Integer nid = uuidNidMap.get(uuid);
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
            foundValue = valueOrGenerateAndPut(sortedUuidList.get(0), uuidNidMap, nextNid);
        }
        for (UUID uuid: sortedUuidList) {
            uuidNidMap.put(uuid, foundValue);
        }
        return foundValue;
    }

    static int nidForUuids(ConcurrentMap<UUID, Integer> uuidNidMap, AtomicInteger nextNid, ImmutableList<UUID> uuidList) {
        switch (uuidList.size()) {
            case 0:
                throw new IllegalStateException("uuidList cannot be empty");
            case 1: {
                return valueOrGenerateAndPut(uuidList.get(0), uuidNidMap, nextNid);
            }
        }
        return valueOrGenerateForList(uuidList.toSortedList(), uuidNidMap, nextNid);
    }

    static int valueOrGenerateAndPut(UUID uuid,
                                     ConcurrentMap<UUID, Integer> uuidNidMap,
                                     AtomicInteger nextNid) {
        Integer nid = uuidNidMap.get(uuid);
        if (nid != null) {
            return nid;
        }
        nid = uuidNidMap.computeIfAbsent(uuid, uuidKey -> nextNid.getAndIncrement());
        return nid;
    }

    static int nidForUuids(ConcurrentMap<UUID, Integer> uuidNidMap, AtomicInteger nextNid, UUID... uuids) {
        switch (uuids.length) {
            case 0:
                throw new IllegalStateException("uuidList cannot be empty");
            case 1:
                return valueOrGenerateAndPut(uuids[0], uuidNidMap, nextNid);
        }
        Arrays.sort(uuids);
        return valueOrGenerateForList(Lists.immutable.of(uuids), uuidNidMap, nextNid);
    }


    /**
     * Merge bytes from concurrently created entities. Method is idempotent.
     * Versions will not be duplicated as a result of calling method multiple times.
     *
     * Used for map.merge functions in concurrent maps.
     * @param bytes1
     * @param bytes2
     * @return
     */
    static byte[] merge(byte[] bytes1, byte[] bytes2) {
        if (Arrays.equals(bytes1, bytes2)) {
            return bytes1;
        }
        try {
            MutableSet<byte[]> byteArraySet = Sets.mutable.empty();
            addToSet(bytes1, byteArraySet);
            addToSet(bytes2, byteArraySet);
            MutableList<byte[]> byteArrayList = byteArraySet.toList();
            byteArrayList.sort(Arrays::compare);

            ByteBuf byteBuf = ByteBufPool.allocate(bytes1.length + bytes2.length);
            byteBuf.writeInt(byteArrayList.size());
            for (byte[] byteArray: byteArrayList) {
                byteBuf.writeInt(byteArray.length);
                byteBuf.put(byteArray);
            }
            return byteBuf.asArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void addToSet(byte[] bytes, MutableSet<byte[]> byteArraySet) throws IOException {
        ByteBuf readBuf = ByteBuf.wrapForReading(bytes);
        int arrayCount = readBuf.readInt();
        for (int i = 0; i < arrayCount; i++) {
            int arraySize = readBuf.readInt();
            byte[] newArray = new byte[arraySize];
            readBuf.read(newArray);
            byteArraySet.add(newArray);
        }
    }
}
