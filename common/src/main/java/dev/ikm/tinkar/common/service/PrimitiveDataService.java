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
package dev.ikm.tinkar.common.service;

import dev.ikm.tinkar.common.id.EntityKey;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.sets.ConcurrentHashSet;
import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ByteList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.api.list.primitive.MutableLongList;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.eclipse.collections.impl.factory.primitive.ByteLists;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.LongLists;
import org.eclipse.collections.impl.factory.primitive.LongSets;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface PrimitiveDataService {

    byte STAMP_DATA_TYPE = 7;

    ConcurrentHashSet<Long> canceledStampNids = new ConcurrentHashSet<>();

    /**
     * Merge bytes from concurrently created entities. Method is idempotent.
     * Versions will not be duplicated as a result of calling method multiple times.
     * <p>     * Used for map.merge functions in concurrent maps.
     *
     * @param oldBytes
     * @param newBytes
     * @return
     */
    /** The UUIDs of the component whose record these are: its chronology follows a 9 byte header. */
    private static MutableSet<UUID> recordUuids(byte[] recordBytes) {
        ByteBuf buf = ByteBuf.wrapForReading(recordBytes);
        buf.moveHead(9 + 1 + 4); // part count, first part size, format version; entity type token, nid
        MutableLongList longList = LongLists.mutable.empty();
        longList.add(buf.readLong());
        longList.add(buf.readLong());
        int additionalUuidLongs = buf.readByte();
        for (int i = 0; i < additionalUuidLongs; i++) {
            longList.add(buf.readLong());
        }
        return Sets.mutable.withAll(UuidUtil.toList(longList.toArray()).castToList());
    }

    private static long recordNid(byte[] recordBytes) {
        ByteBuf buf = ByteBuf.wrapForReading(recordBytes);
        buf.moveHead(9 + 1); // part count, first part size, format version; entity type token
        return buf.readInt();
    }

    static byte[] merge(byte[] oldBytes, byte[] newBytes) {
        if (oldBytes == null) {
            return newBytes;
        }
        if (newBytes == null) {
            return oldBytes;
        }
        if (Arrays.equals(oldBytes, newBytes)) {
            return oldBytes;
        }
        // Format 2 records, those of a 64-bit store, begin with their format byte; a format 1
        // record begins with the high byte of its part count, which is 0.
        if (EntityRecordFormat2.isFormat2(newBytes) || EntityRecordFormat2.isFormat2(oldBytes)) {
            return EntityRecordFormat2.merge(oldBytes, newBytes);
        }
        try {
            MutableSet<ByteList> byteArraySet = Sets.mutable.empty();
            MutableLongList stampList = LongLists.mutable.withInitialCapacity(16);
            byte entityFormat = newBytes[8];
            addToSet(newBytes, byteArraySet, stampList, entityFormat);
            addToSet(oldBytes, byteArraySet, stampList, entityFormat);
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
            // Remove canceled versions here
            if (byteArrayList.size() > 2) {
                MutableList<ByteList> chronologyByteLists = Lists.mutable.empty();
                MutableIntList indexesToRemove = IntLists.mutable.empty();
                for (int i = 0; i < byteArrayList.size(); i++) {
                    ByteList versionBytes = byteArrayList.get(i);
                    byte versionToken = versionBytes.get(0);
                    switch (versionToken) {
                        /*
                            CONCEPT_CHRONOLOGY((byte) 1, ConceptChronology.class),
                            PATTERN_CHRONOLOGY((byte) 2, PatternChronology.class),
                            SEMANTIC_CHRONOLOGY((byte) 3, SemanticChronology.class),
                            STAMP(STAMP_DATA_TYPE, Stamp.class)
                        */
                        case 1, 2, 3, STAMP_DATA_TYPE-> chronologyByteLists.add(versionBytes);

                        /*
                            CONCEPT_VERSION((byte) 4, ConceptVersion.class),
                            PATTERN_VERSION((byte) 5, PatternVersion.class),
                            SEMANTIC_VERSION((byte) 6, SemanticVersion.class)
                            A stamp's own versions (STAMP_VERSION, 25) are not collected: the
                            canceled version is what records that the stamp was canceled, and
                            removing them left a canceled stamp with no version at all.
                         */
                        case 4, 5, 6 -> {
                            long stampNid = ((versionBytes.get(1) & 0xFF) << 24) |
                                    ((versionBytes.get(2) & 0xFF) << 16) |
                                    ((versionBytes.get(3) & 0xFF) << 8) |
                                    ((versionBytes.get(4) & 0xFF) << 0);
                            if (PrimitiveData.get().isCanceledStampNid(stampNid)) {
                                // Garbage collection for canceled versions...
                                indexesToRemove.add(i);
                            }
                        }
                        default -> {
                            // Leave all versions. Need to retain canceled version if component is a stamp.
                        }
                    }
                }
                indexesToRemove.reverseThis().forEach(index -> byteArrayList.remove(index));

                // UUIDs were added...
                if (chronologyByteLists.size() > 1) {
                    // need to merge into one record for the chronology...
                    MutableSet<UUID> uuids = Sets.mutable.empty();
                    for (ByteList chronologyByteList : chronologyByteLists) {
                        MutableLongList longList = LongLists.mutable.empty();
                        ByteBuf chronologyBytes = ByteBuf.wrapForReading(chronologyByteList.toArray());
                        chronologyBytes.readByte(); // EntityType token
                        chronologyBytes.readInt(); // Entity nid
                        longList.add(chronologyBytes.readLong()); // Entity most significant bits
                        longList.add(chronologyBytes.readLong()); // Entity least significant bits
                        int additionalUuidLongs = chronologyBytes.readByte(); // Additional UUID longs...
                        for (int i = 0; i < additionalUuidLongs; i++) {
                            longList.add(chronologyBytes.readLong());
                        }
                        uuids.addAll(UuidUtil.toList(longList.toArray()).castToList());
                    }
                    ImmutableList<UUID> uuidList = uuids.toImmutableList();
                    // An existing component gaining UUIDs it did not hold is rare (a change set
                    // naming it under more UUIDs); the store holds them all now, and says so.
                    MutableSet<UUID> held = recordUuids(oldBytes);
                    MutableSet<UUID> added = uuids.reject(held::contains);
                    if (!added.isEmpty()) {
                        IdentityAdvisories.uuidsAdded(recordNid(oldBytes), held, added);
                    }
                    ByteBuf chronologyBytes = ByteBuf.wrapForReading(chronologyByteLists.get(0).toArray());
                    ByteBuf writeBuf = ByteBufPool.allocate(16 * uuidList.size() + chronologyBytes.array().length);
                    writeBuf.writeByte(chronologyBytes.readByte()); // EntityType token
                    writeBuf.writeInt(chronologyBytes.readInt()); // Entity nid
                    chronologyBytes.readLong(); // Discard msb
                    chronologyBytes.readLong(); // Discard lsb
                    int discardAdditionalUuidLongs = chronologyBytes.readByte();
                    for (int i = 0; i < discardAdditionalUuidLongs; i++) {
                        chronologyBytes.readLong();
                    }

                    // write the new UUIDs.
                    writeBuf.writeLong(uuidList.get(0).getMostSignificantBits());
                    writeBuf.writeLong(uuidList.get(0).getLeastSignificantBits());
                    int additionalUuidLongs = uuidList.size() * 2 - 2;
                    writeBuf.writeByte((byte) additionalUuidLongs);
                    for (int uuidIndex = 1; uuidIndex < uuidList.size(); uuidIndex++) {
                        writeBuf.writeLong(uuidList.get(uuidIndex).getMostSignificantBits());
                        writeBuf.writeLong(uuidList.get(uuidIndex).getLeastSignificantBits());
                    }
                    while (chronologyBytes.canRead()) {
                        writeBuf.writeByte(chronologyBytes.readByte());
                    }
                    byteArrayList.removeAll(chronologyByteLists);
                    byteArrayList.add(0, ByteLists.immutable.of(writeBuf.asArray()));
                }
            }

            ByteBuf byteBuf = ByteBufPool.allocate(oldBytes.length + newBytes.length);
            byteBuf.writeInt(byteArrayList.size());
            boolean first = true;
            for (ByteList byteArray : byteArrayList) {
                if (first) {
                    // Add 4 to have room for the number of versions.
                    // Add 1 for the entity format token
                    byteBuf.writeInt(byteArray.size() + 5);
                    byteBuf.writeByte(entityFormat);
                    byteArray.forEach(b -> {
                        byteBuf.put(b);
                    });
                    // write the number of versions...
                    byteBuf.writeInt(byteArrayList.size() - 1);
                    first = false;
                } else {
                    byteBuf.writeInt(byteArray.size());
                    byteArray.forEach(b -> {
                        byteBuf.put(b);
                    });
                }
            }
            return byteBuf.asArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * @param bytes
     * @param byteArraySet
     * @param stampsInSet  represents the stamps already added to the set. If two versions with the same stamp are being merged,
     *                     the newer version must be the merged version, as it represents a newer edit. There is an assumption that
     *                     the edits of a single version under a single stamp value are sequential, not concurrent.
     * @throws IOException
     */
    private static void addToSet(byte[] bytes, MutableSet<ByteList> byteArraySet, MutableLongList stampsInSet,
                                 byte entityFormat) throws IOException {
        ByteBuf readBuf = ByteBuf.wrapForReading(bytes);
        boolean stampDataType = bytes[9] == STAMP_DATA_TYPE;
        int arrayCount = readBuf.readInt();
        for (int i = 0; i < arrayCount; i++) {
            int arraySize = readBuf.readInt();
            if (i == 0) {
                byte localEntityFormat = readBuf.readByte();
                if (localEntityFormat != entityFormat) {
                    throw new IllegalStateException("All entities should be the same format. Found: " + entityFormat + " != " + localEntityFormat);
                }
                // The first array is the chronicle, and has a field for the number of versions...
                // Add one for the entityFormat token.
                byte[] newArray = new byte[arraySize - 5];
                readBuf.read(newArray);
                byteArraySet.add(ByteLists.immutable.of(newArray));
                int versionCount = readBuf.readInt();
                if (versionCount != arrayCount - 1) {
                    throw new IllegalStateException("Malformed data. versionCount: " +
                            versionCount + " arrayCount: " + arrayCount);
                }
                // Version count is not included as the version count may change as a result of merge.
                // It must be added back in after sorting unique versions.
            } else {
                byte[] newArray = new byte[arraySize];
                readBuf.read(newArray);
                if (stampDataType) {
                    byteArraySet.add(ByteLists.immutable.of(newArray));
                } else {
                    long stampNid = ((newArray[1] & 0xFF) << 24) |
                            ((newArray[2] & 0xFF) << 16) |
                            ((newArray[3] & 0xFF) << 8) |
                            ((newArray[4] & 0xFF) << 0);
                    if (stampsInSet.contains(stampNid)) {
                        // Don't add, a newer version already exists (assuming addToSet is called in order of newest to oldest bytearray)
                        // There should be no concurrent editing on versions with the same stamp.
                    } else {
                        byteArraySet.add(ByteLists.immutable.of(newArray));
                        stampsInSet.add(stampNid);
                    }
                }
            }
        }
    }

    default boolean isCanceledStampNid(long stampNid) {
        return canceledStampNids.contains(stampNid);
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

    /**
     * Resolves the {@link PublicId} a nid was minted from, using the provider's
     * identity map alone — the component need not be present as an entity. The inverse
     * of {@link #nidForPublicId(PublicId)}. Providers that do not maintain a reverse
     * identity mapping may leave the default, which signals the capability is absent.
     *
     * @param nid the nid to resolve
     * @return the public id the nid was minted from
     * @throws UnsupportedOperationException if this provider cannot reverse-resolve nids
     * @throws IllegalStateException         if the nid was never minted in this store
     */
    default PublicId publicIdForNid(long nid) {
        throw new UnsupportedOperationException(
                "This primitive-data provider does not maintain a reverse (nid to public id) identity map");
    }

    default long nidForPublicId(PublicId publicId) {
        return nidForUuids(publicId.asUuidArray());
    }

    long nidForUuids(UUID... uuids);

    long nidForUuids(ImmutableList<UUID> uuidList);

    boolean hasUuid(UUID uuid);

    boolean hasPublicId(PublicId publicId);

    PrimitiveDataSearchResult[] search(String query, int maxResultSize) throws Exception;

    /**
     * Highlight an arbitrary text against the same parsed query the index would
     * use. See {@link SearchService#highlight(String, String)} for matching
     * semantics and intended use.
     *
     * @param query the search query string
     * @param text the text to highlight
     * @return {@code text} with matched tokens wrapped in {@code <B>...</B>},
     *         or the original {@code text} when there are no matches
     * @throws Exception if an error occurs during query parsing or highlighting
     */
    String highlight(String query, String text) throws Exception;

    CompletableFuture<Void> recreateLuceneIndex() throws Exception;

    default void addCanceledStampNid(long stampNid) {
        canceledStampNids.add(stampNid);
    }

    /**
     * @return user-friendly name for this data service
     */
    String name();

    /**
     * Gets or creates an EntityKey for the given pattern and entity.
     * <p>     * Default implementation for providers using sequential NIDs (SpinedArray, MVStore, Ephemeral).
     * These providers don't encode pattern information in the NID, so this returns a
     * {@link EntityKey.SequentialNidEntityKey} that wraps the sequential NID directly.
     * <p>     * Providers using pattern-encoded NIDs (e.g., RocksDB) should override this method.
     */
    default EntityKey getEntityKey(PublicId patternId, PublicId entityId) {
        return EntityKey.ofSequentialNid(nidForUuids(entityId.asUuidArray()));
    }

    /**
     * Gets an EntityKey for the given UUID if it exists.
     * <p>     * Default implementation for providers using sequential NIDs.
     */
    default Optional<EntityKey> getEntityKey(UUID uuid) {
        if (hasUuid(uuid)) {
            return Optional.of(EntityKey.ofSequentialNid(nidForUuids(uuid)));
        }
        return Optional.empty();
    }

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

    /**
     * Returns whether this provider requires multi-pass import for correct NID assignment.
     * <p>     * Providers that encode pattern information in NIDs (like RocksDB) return {@code true}
     * because they need to discover all patterns in the first pass before assigning 
     * pattern-encoded NIDs in the second pass.
     * <p>     * Providers using sequential NIDs (SpinedArray, MVStore, Ephemeral) return {@code false}
     * because NIDs are assigned on-demand without pattern encoding.
     * 
     * @return true if multi-pass import is required, false for single-pass
     */
    default boolean requiresMultiPassImport() {
        return false;  // Default: sequential NID providers don't need multi-pass
    }

    /**
     * Signals the data provider to suppress per-entity indexing.
     * Set to {@code true} before bulk import and {@code false} after,
     * so that the index can be rebuilt in a single batch pass.
     */
    default void setLoadPhase(boolean loadPhase) {
        // Default no-op for providers that don't index inline.
    }

    class CacheProvider implements CachingService {

        @Override
        public void reset() {
            canceledStampNids.clear();
        }
    }

}
