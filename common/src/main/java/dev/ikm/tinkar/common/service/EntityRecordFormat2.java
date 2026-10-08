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

import dev.ikm.tinkar.common.id.Nid;
import io.activej.bytebuf.ByteBuf;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.MutableSet;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.eclipse.collections.impl.factory.primitive.LongSets;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;
import java.util.function.LongPredicate;

/**
 * The envelope of an entity record in format 2, the format of a 64-bit store (design
 * {@code design-2026-10-07-64-bit-rocks-store}; IKE-Network/ike-issues#1258). The store holds
 * one such record per entity, chronology and versions in one value, and treats it as opaque
 * bytes; this class is what the store's merge and the entity codec agree on.
 *
 * <p>A record is
 * <pre>
 *   [byte 2]                              the format
 *   [varint length][chronology bytes]
 *   [varint count]([varint length][version bytes])*
 * </pre>
 * and a chronology begins with its identity,
 * <pre>
 *   [byte type token]
 *   [varint patternSequence][varint elementSequence]      the entity's nid, its two halves
 *   [long msb][long lsb]                                  its first UUID
 *   [varint n][long × n]                                  n further longs, two per further UUID
 * </pre>
 * followed by what the entity codec writes for the entity's kind. A version begins with its
 * type token and its stamp's nid as two varints. Every nid in a format 2 record is a 64-bit nid,
 * both halves from 1 through {@value Nid#MAX_SEQUENCE_64}; the writer refuses any other.
 * Varints are the unsigned LEB128 of activej's {@link ByteBuf}; counts and lengths are never
 * negative, and a nid's halves are positive.
 *
 * <p>A format 1 record begins with the high byte of its part count, which is 0, so the first
 * byte tells the formats apart.
 */
public final class EntityRecordFormat2 {

    /** The format byte at the head of every record. */
    public static final byte FORMAT = 2;

    private EntityRecordFormat2() {
    }

    /**
     * Whether a record is in format 2.
     *
     * @param record the record, possibly null
     * @return true if it begins with the format 2 byte
     */
    public static boolean isFormat2(byte[] record) {
        return record != null && record.length > 0 && record[0] == FORMAT;
    }

    // ---------------------------------------------------------------- nids

    /**
     * Writes a nid as its two halves, each a varint.
     *
     * @param buf the buffer
     * @param nid a 64-bit nid
     * @throws IllegalArgumentException if the nid is not a valid 64-bit nid
     */
    public static void writeNid(ByteBuf buf, long nid) {
        Nid.validate64(nid);
        buf.writeVarInt(Nid.patternSequence64(nid));
        buf.writeVarInt(Nid.elementSequence64(nid));
    }

    /**
     * Reads a nid written by {@link #writeNid}.
     *
     * @param buf the buffer
     * @return the nid
     */
    public static long readNid(ByteBuf buf) {
        int patternSequence = buf.readVarInt();
        int elementSequence = buf.readVarInt();
        return Nid.compose64(patternSequence, elementSequence);
    }

    // ---------------------------------------------------------------- the identity at the head of a chronology

    /**
     * Writes a chronology's identity after its type token: the nid, the first UUID, and the
     * further UUID longs.
     *
     * @param buf                 the buffer, its type token already written
     * @param nid                 the entity's nid
     * @param mostSignificantBits the first UUID's most significant bits
     * @param leastSignificantBits the first UUID's least significant bits
     * @param additionalUuidLongs two longs per further UUID, or null
     */
    public static void writeIdentity(ByteBuf buf, long nid, long mostSignificantBits, long leastSignificantBits,
                                     long[] additionalUuidLongs) {
        writeNid(buf, nid);
        buf.writeLong(mostSignificantBits);
        buf.writeLong(leastSignificantBits);
        int count = additionalUuidLongs == null ? 0 : additionalUuidLongs.length;
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeLong(additionalUuidLongs[i]);
        }
    }

    /** A chronology's identity: its type token, nid, and UUIDs, and where the rest of the chronology begins. */
    public record Identity(byte token, long nid, long mostSignificantBits, long leastSignificantBits,
                           long[] additionalUuidLongs, int restOffset) {

        /** Every UUID, the first then the further ones. */
        public ImmutableList<UUID> uuids() {
            MutableList<UUID> uuids = Lists.mutable.withInitialCapacity(1 + additionalUuidLongs.length / 2);
            uuids.add(new UUID(mostSignificantBits, leastSignificantBits));
            for (int i = 0; i + 1 < additionalUuidLongs.length; i += 2) {
                uuids.add(new UUID(additionalUuidLongs[i], additionalUuidLongs[i + 1]));
            }
            return uuids.toImmutable();
        }
    }

    /**
     * Reads the identity at the head of a chronology part, type token included.
     *
     * @param chronology the chronology part
     * @return its identity
     */
    public static Identity readIdentity(byte[] chronology) {
        ByteBuf buf = ByteBuf.wrapForReading(chronology);
        byte token = buf.readByte();
        long nid = readNid(buf);
        long msb = buf.readLong();
        long lsb = buf.readLong();
        int count = buf.readVarInt();
        long[] additional = new long[count];
        for (int i = 0; i < count; i++) {
            additional[i] = buf.readLong();
        }
        return new Identity(token, nid, msb, lsb, additional, buf.head());
    }

    // ---------------------------------------------------------------- the envelope

    /** A record's parts as stored: the chronology, then its versions. */
    public record Parts(byte[] chronology, ImmutableList<byte[]> versions) {
    }

    /**
     * Takes a record apart.
     *
     * @param record a format 2 record
     * @return its chronology and versions
     * @throws IllegalArgumentException if the record is not in format 2
     */
    public static Parts parts(byte[] record) {
        if (!isFormat2(record)) {
            throw new IllegalArgumentException("Not a format 2 entity record: "
                    + (record == null ? "null" : "first byte " + record[0]));
        }
        ByteBuf buf = ByteBuf.wrapForReading(record);
        buf.readByte();
        byte[] chronology = new byte[buf.readVarInt()];
        buf.read(chronology);
        int versionCount = buf.readVarInt();
        MutableList<byte[]> versions = Lists.mutable.withInitialCapacity(versionCount);
        for (int i = 0; i < versionCount; i++) {
            byte[] version = new byte[buf.readVarInt()];
            buf.read(version);
            versions.add(version);
        }
        return new Parts(chronology, versions.toImmutable());
    }

    /**
     * Puts a record together.
     *
     * @param chronology the chronology part
     * @param versions   the version parts, in the order to store them
     * @return the record
     */
    public static byte[] assemble(byte[] chronology, List<byte[]> versions) {
        int size = 1 + 5 + chronology.length + 5;
        for (byte[] version : versions) {
            size += 5 + version.length;
        }
        ByteBuf buf = ByteBuf.wrapForWriting(new byte[size]);
        buf.writeByte(FORMAT);
        buf.writeVarInt(chronology.length);
        buf.write(chronology);
        buf.writeVarInt(versions.size());
        for (byte[] version : versions) {
            buf.writeVarInt(version.length);
            buf.write(version);
        }
        return Arrays.copyOf(buf.array(), buf.tail());
    }

    /**
     * The nid of the entity a record holds.
     *
     * @param record a format 2 record
     * @return the entity's nid
     */
    public static long nid(byte[] record) {
        ByteBuf buf = ByteBuf.wrapForReading(record);
        buf.readByte();
        buf.readVarInt();   // the chronology's length
        buf.readByte();     // its type token
        return readNid(buf);
    }

    /**
     * The UUIDs of the entity a record holds.
     *
     * @param record a format 2 record
     * @return its UUIDs, the first one first
     */
    public static ImmutableList<UUID> uuids(byte[] record) {
        return readIdentity(parts(record).chronology()).uuids();
    }

    /**
     * The stamp nid of a version part: the two varints after its type token.
     *
     * @param version a version part
     * @return its stamp's nid
     */
    public static long stampNid(byte[] version) {
        ByteBuf buf = ByteBuf.wrapForReading(version);
        buf.readByte();
        return readNid(buf);
    }

    // ---------------------------------------------------------------- merge

    /**
     * Merges two records of one entity, as {@link PrimitiveDataService#merge} does for format 1:
     * the union of their versions, a version under a stamp already present in the newer record
     * yielding to the newer one, the versions of canceled stamps dropped when more than one
     * version remains (never for a stamp entity, whose canceled version is what records the
     * cancellation), and the chronology's UUIDs unioned, with an advisory when the entity gains
     * UUIDs it did not hold. The versions are stored in their unsigned byte order, so the result
     * does not depend on the order the records were merged in, and a merge of equal records is
     * the record itself.
     *
     * @param oldBytes the record stored, or null
     * @param newBytes the record being written, or null
     * @return the merged record
     * @throws IllegalStateException if the records are not both in format 2
     */
    public static byte[] merge(byte[] oldBytes, byte[] newBytes) {
        return merge(oldBytes, newBytes, stampNid -> PrimitiveData.get().isCanceledStampNid(stampNid));
    }

    /**
     * {@link #merge(byte[], byte[])} with the canceled stamps named by a predicate.
     *
     * @param oldBytes      the record stored, or null
     * @param newBytes      the record being written, or null
     * @param canceledStamp whether a stamp nid is that of a canceled stamp
     * @return the merged record
     */
    public static byte[] merge(byte[] oldBytes, byte[] newBytes, LongPredicate canceledStamp) {
        if (oldBytes == null) {
            return newBytes;
        }
        if (newBytes == null || Arrays.equals(oldBytes, newBytes)) {
            return oldBytes;
        }
        if (!isFormat2(oldBytes) || !isFormat2(newBytes)) {
            throw new IllegalStateException("A store holds one entity format: merging a format "
                    + formatOf(oldBytes) + " record with a format " + formatOf(newBytes) + " one");
        }
        Parts older = parts(oldBytes);
        Parts newer = parts(newBytes);
        byte[] chronology = Arrays.equals(older.chronology(), newer.chronology())
                ? older.chronology()
                : mergeChronologies(older.chronology(), newer.chronology());
        boolean stampEntity = chronology[0] == PrimitiveDataService.STAMP_DATA_TYPE;

        TreeSet<byte[]> versions = new TreeSet<>(Arrays::compareUnsigned);
        MutableLongSet stampsPresent = LongSets.mutable.empty();
        // The newer record first, so that under one stamp its version is the one kept.
        for (ImmutableList<byte[]> source : List.of(newer.versions(), older.versions())) {
            for (byte[] version : source) {
                if (stampEntity || stampsPresent.add(stampNid(version))) {
                    versions.add(version);
                }
            }
        }
        List<byte[]> kept = new ArrayList<>(versions);
        if (!stampEntity && kept.size() > 1) {
            kept.removeIf(version -> canceledStamp.test(stampNid(version)));
        }
        return assemble(chronology, kept);
    }

    private static String formatOf(byte[] record) {
        return record.length > 8 && record[0] == 0 ? "1" : Byte.toString(record[0]);
    }

    /**
     * The older chronology with the UUIDs of the newer one it did not hold appended, the rest of
     * it unchanged; an advisory names what was added.
     */
    private static byte[] mergeChronologies(byte[] older, byte[] newer) {
        Identity held = readIdentity(older);
        Identity incoming = readIdentity(newer);
        MutableSet<UUID> heldUuids = Sets.mutable.withAll(held.uuids());
        MutableSet<UUID> added = Sets.mutable.empty();
        for (UUID uuid : incoming.uuids()) {
            if (!heldUuids.contains(uuid)) {
                added.add(uuid);
            }
        }
        if (added.isEmpty()) {
            return older;
        }
        IdentityAdvisories.uuidsAdded(held.nid(), heldUuids, added);
        long[] additional = new long[held.additionalUuidLongs().length + 2 * added.size()];
        System.arraycopy(held.additionalUuidLongs(), 0, additional, 0, held.additionalUuidLongs().length);
        int i = held.additionalUuidLongs().length;
        for (UUID uuid : incoming.uuids()) {
            if (added.contains(uuid)) {
                additional[i++] = uuid.getMostSignificantBits();
                additional[i++] = uuid.getLeastSignificantBits();
            }
        }
        int rest = older.length - held.restOffset();
        ByteBuf buf = ByteBuf.wrapForWriting(new byte[older.length + 2 * 8 * added.size() + 5]);
        buf.writeByte(held.token());
        writeIdentity(buf, held.nid(), held.mostSignificantBits(), held.leastSignificantBits(), additional);
        buf.write(older, held.restOffset(), rest);
        return Arrays.copyOf(buf.array(), buf.tail());
    }
}
