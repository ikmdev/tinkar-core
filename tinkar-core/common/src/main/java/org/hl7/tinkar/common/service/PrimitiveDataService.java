package org.hl7.tinkar.common.service;

import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import org.eclipse.collections.api.block.procedure.primitive.IntProcedure;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.ListIterable;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ByteList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.eclipse.collections.impl.factory.primitive.ByteLists;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.LongSets;
import org.hl7.tinkar.common.id.PublicId;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.function.ObjIntConsumer;

public interface PrimitiveDataService {

    int FIRST_NID = Integer.MIN_VALUE + 1;

    static int nidForUuids(ConcurrentMap<UUID, Integer> uuidNidMap, NidGenerator nidGenerator, ImmutableList<UUID> uuidList) {
        switch (uuidList.size()) {
            case 0:
                throw new IllegalStateException("uuidList cannot be empty");
            case 1: {
                return valueOrGenerateAndPut(uuidList.get(0), uuidNidMap, nidGenerator);
            }
        }
        return valueOrGenerateForList(uuidList.toSortedList(), uuidNidMap, nidGenerator);
    }

    static int valueOrGenerateAndPut(UUID uuid,
                                     ConcurrentMap<UUID, Integer> uuidNidMap,
                                     NidGenerator nidGenerator) {
        Integer nid = uuidNidMap.get(uuid);
        if (nid != null) {
            return nid;
        }
        nid = uuidNidMap.computeIfAbsent(uuid, uuidKey -> nidGenerator.newNid());
        return nid;
    }

    static int valueOrGenerateForList(ListIterable<UUID> sortedUuidList,
                                      ConcurrentMap<UUID, Integer> uuidNidMap,
                                      NidGenerator nidGenerator) {
        boolean missingMap = false;
        int foundValue = Integer.MIN_VALUE;

        for (UUID uuid : sortedUuidList) {
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
            foundValue = valueOrGenerateAndPut(sortedUuidList.get(0), uuidNidMap, nidGenerator);
        }
        for (UUID uuid : sortedUuidList) {
            uuidNidMap.put(uuid, foundValue);
        }
        return foundValue;
    }

    static int nidForUuids(ConcurrentMap<UUID, Integer> uuidNidMap, NidGenerator nidGenerator, UUID... uuids) {
        switch (uuids.length) {
            case 0:
                throw new IllegalStateException("uuidList cannot be empty");
            case 1:
                return valueOrGenerateAndPut(uuids[0], uuidNidMap, nidGenerator);
        }
        Arrays.sort(uuids);
        return valueOrGenerateForList(Lists.immutable.of(uuids), uuidNidMap, nidGenerator);
    }

    /**
     * Merge bytes from concurrently created entities. Method is idempotent.
     * Versions will not be duplicated as a result of calling method multiple times.
     * <p>
     * Used for map.merge functions in concurrent maps.
     *
     * @param bytes1
     * @param bytes2
     * @return
     */
    static byte[] merge(byte[] bytes1, byte[] bytes2) {
        if (bytes1 == null) {
            return bytes2;
        }
        if (bytes2 == null) {
            return bytes1;
        }
        if (Arrays.equals(bytes1, bytes2)) {
            return bytes1;
        }
        try {
            MutableSet<ByteList> byteArraySet = Sets.mutable.empty();
            addToSet(bytes1, byteArraySet);
            addToSet(bytes2, byteArraySet);
            MutableList<ByteList> byteArrayList = byteArraySet.toList();

            byteArrayList.sort((o1, o2) -> {
                int minSize = Math.min(o1.size(), o2.size());
                for (int i = 0; i < minSize; i++) {
                    if (o1.get(i) != o2.get(i)) {
                        return Integer.compare(o1.get(i), o2.get(i));
                    }
                }
                return Integer.compare(o1.size(), o2.size());
            });

            ByteBuf byteBuf = ByteBufPool.allocate(bytes1.length + bytes2.length);
            byteBuf.writeInt(byteArrayList.size());
            boolean first = true;
            for (ByteList byteArray : byteArrayList) {
                byteBuf.writeInt(byteArray.size());
                byteArray.forEach(b -> {
                    byteBuf.put(b);
                });
                if (first) {
                    first = false;
                    // write the number of versions...
                    byteBuf.writeInt(byteArrayList.size() - 1);
                }

            }
            return byteBuf.asArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void addToSet(byte[] bytes, MutableSet<ByteList> byteArraySet) throws IOException {
        ByteBuf readBuf = ByteBuf.wrapForReading(bytes);
        int arrayCount = readBuf.readInt();
        for (int i = 0; i < arrayCount; i++) {
            int arraySize = readBuf.readInt();
            if (i == 0) {
                // The first array is the chronicle, and has a field for the number of versions...
                byte[] newArray = new byte[arraySize - 4];
                readBuf.read(newArray);
                byteArraySet.add(ByteLists.immutable.of(newArray));
                int versionCount = readBuf.readInt();
                if (versionCount != arrayCount - 1) {
                    throw new IllegalStateException("Malformed data. versionCount: " +
                            versionCount + " arrayCount: " + arrayCount);
                }
            } else {
                byte[] newArray = new byte[arraySize];
                readBuf.read(newArray);
                byteArraySet.add(ByteLists.immutable.of(newArray));
            }
        }
    }

    static long[] mergeCitations(long[] citation1, long[] citation2) {
        if (citation1 == null) {
            return citation2;
        }
        if (citation2 == null) {
            return citation1;
        }
        if (Arrays.equals(citation1, citation2)) {
            return citation1;
        }
        MutableLongSet citationSet = LongSets.mutable.of(citation1);
        citationSet.addAll(citation2);
        return citationSet.toSortedArray();
    }

    long writeSequence();

    void close();

    default int nidForPublicId(PublicId publicId) {
        return nidForUuids(publicId.asUuidArray());
    }

    int nidForUuids(UUID... uuids);

    int nidForUuids(ImmutableList<UUID> uuidList);

    void forEach(ObjIntConsumer<byte[]> action);

    void forEachParallel(ObjIntConsumer<byte[]> action);

    byte[] getBytes(int nid);

    /**
     * If the specified nid (native identifier -- an int) is not already associated
     * with a value or is associated with null, associates it with the given non-null value.
     * Otherwise, replaces the associated value with the results of a remapping function
     * (remapping function is provided the provider), or removes if the result is {@code null}.
     * This method may be of use when combining multiple mapped values for a nid.
     * For example, merging multiple versions of an entity, where each version is represented as a
     * byte[].
     *
     * @param nid                    native identifier (an int) with which the resulting value is to be associated
     * @param patternNid
     * @param referencedComponentNid if the bytes are for a semantic, the referenced component nid,
     *                               otherwise Integer.MAX_VALUE.
     * @param value                  the non-null value to be merged with the existing value
     *                               associated with the nid or, if no existing value or a null value
     *                               is associated with the nid, to be associated with the nid
     * @param sourceObject           object that is the source of the bytes to merge.
     * @return the new value associated with the specified nid, or null if no
     * value is associated with the nid
     */
    byte[] merge(int nid, int patternNid, int referencedComponentNid, byte[] value, Object sourceObject);

    PrimitiveDataSearchResult[] search(String query, int maxResultSize) throws Exception;

    /**
     * @param patternNid
     * @return
     */
    default int[] semanticNidsOfPattern(int patternNid) {
        MutableIntList intList = IntLists.mutable.empty();
        forEachSemanticNidOfPattern(patternNid, nid -> intList.add(nid));
        return intList.toArray();
    }

    void forEachSemanticNidOfPattern(int patternNid, IntProcedure procedure);

    void forEachPatternNid(IntProcedure procedure);

    void forEachConceptNid(IntProcedure procedure);

    void forEachStampNid(IntProcedure procedure);

    void forEachSemanticNid(IntProcedure procedure);

    default int[] semanticNidsForComponent(int componentNid) {
        MutableIntList intList = IntLists.mutable.empty();
        forEachSemanticNidForComponent(componentNid, nid -> intList.add(nid));
        return intList.toArray();
    }

    void forEachSemanticNidForComponent(int componentNid, IntProcedure procedure);

    default int[] semanticNidsForComponentOfPattern(int componentNid, int patternNid) {
        MutableIntList intList = IntLists.mutable.empty();
        forEachSemanticNidForComponentOfPattern(componentNid, patternNid, nid -> intList.add(nid));
        return intList.toArray();
    }

    void forEachSemanticNidForComponentOfPattern(int componentNid, int patternNid, IntProcedure procedure);

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
                default:
                    throw new UnsupportedOperationException("Can't handle token: " + token);
            }
        }
    }
}
