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
 * The envelope of an entity record in format 3, the format of a 64-bit store
 * (IKE-Network/ike-issues#1313, after format 2 of design {@code design-2026-10-07-64-bit-rocks-store}
 * and IKE-Network/ike-issues#1258). The store holds one such record per entity, chronology and
 * versions in one value, and treats it as opaque bytes; this class is what the store's merge and
 * the entity codec agree on.
 *
 * <p>A record is
 * <pre>
 *   [byte 3]                              the format
 *   [varint length][chronology bytes]
 *   [varint count]([varint length][version bytes])*
 * </pre>
 * and a chronology begins with its identity,
 * <pre>
 *   [varint patternSequence][varint elementSequence]      the entity's nid, its two halves
 *   [long msb][long lsb]                                  its first UUID
 *   [varint n][long &times; n]                               n further longs, two per further UUID
 * </pre>
 * followed by what the entity codec writes for the entity's kind.
 *
 * <p>The nid gives the kind: under the 64-bit layout a pattern's nid has the pattern-of-patterns'
 * sequence as its pattern half, a concept's the concept pattern's, a STAMP's the stamp pattern's,
 * and a semantic's its own pattern's element sequence, so a semantic's pattern is
 * {@code compose64(1, its pattern half)}. Nothing in a record says its kind or a semantic's
 * pattern; the writer refuses a record whose nid does not give them, which a nid minted for a
 * component of another kind would be (IKE-Network/ike-issues#1313).
 *
 * <p>A reference whose kind fixes its pattern half (a version's stamp, a STAMP's concepts, a
 * pattern's concepts) is a <em>typed</em> reference: the element half alone, a varint. The
 * writer refuses a typed reference whose pattern half is not the one its kind fixes. Every
 * other reference is the nid's two halves. Every nid is a 64-bit nid, both halves from 1 through
 * {@value Nid#MAX_SEQUENCE_64}; the writer refuses any other. Varints are the unsigned LEB128 of
 * activej's {@link ByteBuf}; counts and lengths are never negative.
 *
 * <p>A format 1 record begins with the high byte of its part count, which is 0, so the first
 * byte tells the formats apart. A record that begins with 2 is one of format 2, retired on
 * 2026-10-21 before any store but a test's held it, and is refused.
 */
public final class EntityRecordFormat3 {

    /** The format byte at the head of every record. */
    public static final byte FORMAT = 3;

    /** The format byte of the retired format 2. */
    public static final byte RETIRED_FORMAT_2 = 2;

    /** The type token of a concept chronology. */
    public static final byte CONCEPT_TOKEN = 1;
    /** The type token of a pattern chronology. */
    public static final byte PATTERN_TOKEN = 2;
    /** The type token of a semantic chronology. */
    public static final byte SEMANTIC_TOKEN = 3;
    /** The type token of a STAMP. */
    public static final byte STAMP_TOKEN = PrimitiveDataService.STAMP_DATA_TYPE;

    private EntityRecordFormat3() {
    }

    /**
     * Whether a record is in format 3.
     *
     * @param record the record, possibly null
     * @return true if it begins with the format 3 byte
     */
    public static boolean isFormat3(byte[] record) {
        return record != null && record.length > 0 && record[0] == FORMAT;
    }

    /**
     * Refuses a record not in format 3, naming a record of the retired format 2 as one.
     *
     * @param record the record
     * @throws IllegalArgumentException if the record is not in format 3
     */
    public static void requireFormat3(byte[] record) {
        if (!isFormat3(record)) {
            throw notFormat3(record);
        }
    }

    private static IllegalArgumentException notFormat3(byte[] record) {
        if (record != null && record.length > 0 && record[0] == RETIRED_FORMAT_2) {
            return new IllegalArgumentException("A record of entity format 2, retired on 2026-10-21 "
                    + "(IKE-Network/ike-issues#1313): rebuild the store from its change sets");
        }
        return new IllegalArgumentException("Not a format 3 entity record: "
                + (record == null ? "null" : record.length == 0 ? "empty" : "first byte " + record[0]));
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

    /**
     * Writes a typed reference: the element half alone, the pattern half being the one the
     * reference's kind fixes.
     *
     * @param buf             the buffer
     * @param nid             a 64-bit nid
     * @param patternSequence the pattern half the reference's kind fixes
     * @throws IllegalArgumentException if the nid is not a valid 64-bit nid, or its pattern half
     *                                  is not the one the reference's kind fixes
     */
    public static void writeTypedNid(ByteBuf buf, long nid, int patternSequence) {
        Nid.validate64(nid);
        if (Nid.patternSequence64(nid) != patternSequence) {
            throw new IllegalArgumentException("A reference to " + kindWord(patternSequence) + " names "
                    + DiagnosticText.component(nid) + ", which its nid makes " + kindWord(Nid.patternSequence64(nid))
                    + " (IKE-Network/ike-issues#1313)");
        }
        buf.writeVarInt(Nid.elementSequence64(nid));
    }

    /**
     * Reads a typed reference written by {@link #writeTypedNid}.
     *
     * @param buf             the buffer
     * @param patternSequence the pattern half the reference's kind fixes
     * @return the nid
     */
    public static long readTypedNid(ByteBuf buf, int patternSequence) {
        return Nid.compose64(patternSequence, buf.readVarInt());
    }

    private static String kindWord(int patternSequence) {
        return switch (patternSequence) {
            case Nid.PATTERN_OF_PATTERNS_SEQUENCE_64 -> "a pattern";
            case Nid.CONCEPT_SEQUENCE_64 -> "a concept";
            case Nid.STAMP_SEQUENCE_64 -> "a STAMP";
            default -> "a semantic of pattern sequence " + patternSequence;
        };
    }

    /** Writes a reference to a concept: {@link #writeTypedNid} under the concept pattern. */
    public static void writeConceptNid(ByteBuf buf, long nid) {
        writeTypedNid(buf, nid, Nid.CONCEPT_SEQUENCE_64);
    }

    /** Reads a reference written by {@link #writeConceptNid}. */
    public static long readConceptNid(ByteBuf buf) {
        return readTypedNid(buf, Nid.CONCEPT_SEQUENCE_64);
    }

    /** Writes a reference to a STAMP: {@link #writeTypedNid} under the stamp pattern. */
    public static void writeStampNid(ByteBuf buf, long nid) {
        writeTypedNid(buf, nid, Nid.STAMP_SEQUENCE_64);
    }

    /** Reads a reference written by {@link #writeStampNid}. */
    public static long readStampNid(ByteBuf buf) {
        return readTypedNid(buf, Nid.STAMP_SEQUENCE_64);
    }

    /** Writes a reference to a pattern: {@link #writeTypedNid} under the pattern-of-patterns. */
    public static void writePatternNid(ByteBuf buf, long nid) {
        writeTypedNid(buf, nid, Nid.PATTERN_OF_PATTERNS_SEQUENCE_64);
    }

    /** Reads a reference written by {@link #writePatternNid}. */
    public static long readPatternNid(ByteBuf buf) {
        return readTypedNid(buf, Nid.PATTERN_OF_PATTERNS_SEQUENCE_64);
    }

    /**
     * The type token a nid gives: a pattern's, a concept's or a STAMP's for the three fixed
     * pattern halves, else a semantic's.
     *
     * @param nid a 64-bit nid
     * @return the chronology type token
     */
    public static byte kindToken(long nid) {
        return switch (Nid.patternSequence64(nid)) {
            case Nid.PATTERN_OF_PATTERNS_SEQUENCE_64 -> PATTERN_TOKEN;
            case Nid.CONCEPT_SEQUENCE_64 -> CONCEPT_TOKEN;
            case Nid.STAMP_SEQUENCE_64 -> STAMP_TOKEN;
            default -> SEMANTIC_TOKEN;
        };
    }

    /**
     * The pattern a semantic's nid gives: the pattern whose element sequence is the nid's pattern
     * half; 0 for a nid of a fixed pattern half, which no semantic has.
     *
     * @param nid a 64-bit nid
     * @return the pattern's nid, or 0
     */
    public static long impliedPatternNid(long nid) {
        int half = Nid.patternSequence64(nid);
        return half >= Nid.FIRST_SEMANTIC_SEQUENCE_64 ? Nid.compose64(Nid.PATTERN_OF_PATTERNS_SEQUENCE_64, half) : 0;
    }

    // ---------------------------------------------------------------- scalars

    /** Writes an int as a zigzag varint: small magnitudes, either sign, take few bytes. */
    public static void writeSignedVarInt(ByteBuf buf, int value) {
        buf.writeVarInt((value << 1) ^ (value >> 31));
    }

    /** Reads an int written by {@link #writeSignedVarInt}. */
    public static int readSignedVarInt(ByteBuf buf) {
        int value = buf.readVarInt();
        return (value >>> 1) ^ -(value & 1);
    }

    /** Writes a long as a zigzag varint. */
    public static void writeSignedVarLong(ByteBuf buf, long value) {
        buf.writeVarLong((value << 1) ^ (value >> 63));
    }

    /** Reads a long written by {@link #writeSignedVarLong}. */
    public static long readSignedVarLong(ByteBuf buf) {
        long value = buf.readVarLong();
        return (value >>> 1) ^ -(value & 1);
    }

    // ---------------------------------------------------------------- the identity at the head of a chronology

    /**
     * Checks that a nid gives an entity's kind and, for a semantic, its pattern: what a format 3
     * record relies on, since it writes neither.
     *
     * @param token      the entity's chronology type token
     * @param nid        the entity's nid
     * @param patternNid a semantic's pattern nid; ignored for any other kind
     * @throws IllegalArgumentException if the nid gives another kind or pattern
     */
    public static void requireKindOfNid(byte token, long nid, long patternNid) {
        Nid.validate64(nid);
        if (token != kindToken(nid) || (token == SEMANTIC_TOKEN && patternNid != impliedPatternNid(nid))) {
            throw new IllegalArgumentException("Entity " + DiagnosticText.component(nid) + " is "
                    + kindWord(Nid.patternSequence64(nid)) + " by its nid, but the entity is of type " + token
                    + (token == SEMANTIC_TOKEN ? ", a semantic of pattern " + DiagnosticText.component(patternNid) : "")
                    + ": its nid was minted for a component of another kind (IKE-Network/ike-issues#1313)");
        }
    }

    /**
     * Writes a chronology's identity: the nid, the first UUID, and the further UUID longs.
     *
     * @param buf                  the buffer
     * @param nid                  the entity's nid
     * @param mostSignificantBits  the first UUID's most significant bits
     * @param leastSignificantBits the first UUID's least significant bits
     * @param additionalUuidLongs  two longs per further UUID, or null
     */
    public static void writeIdentity(ByteBuf buf, long nid, long mostSignificantBits,
                                     long leastSignificantBits, long[] additionalUuidLongs) {
        writeNid(buf, nid);
        buf.writeLong(mostSignificantBits);
        buf.writeLong(leastSignificantBits);
        int count = additionalUuidLongs == null ? 0 : additionalUuidLongs.length;
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeLong(additionalUuidLongs[i]);
        }
    }

    /**
     * A chronology's identity: its type token and, for a semantic, its pattern's nid, both as the
     * nid gives them; its nid and UUIDs; and where the rest of the chronology begins.
     */
    public record Identity(byte token, long nid, long mostSignificantBits, long leastSignificantBits,
                           long[] additionalUuidLongs, long patternNid, int restOffset) {

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
     * Reads the identity at the head of a chronology part.
     *
     * @param chronology the chronology part
     * @return its identity
     */
    public static Identity readIdentity(byte[] chronology) {
        return readIdentity(ByteBuf.wrapForReading(chronology));
    }

    /**
     * Reads the identity at the buffer's head, leaving the head after it: the reader's way to the
     * identity in place, without first taking the record apart. The identity's
     * {@linkplain Identity#restOffset() rest offset} is the buffer's head after it.
     *
     * @param buf the buffer, its head at a chronology
     * @return the identity
     */
    public static Identity readIdentity(ByteBuf buf) {
        long nid = readNid(buf);
        long msb = buf.readLong();
        long lsb = buf.readLong();
        long[] additional = new long[buf.readVarInt()];
        for (int i = 0; i < additional.length; i++) {
            additional[i] = buf.readLong();
        }
        return new Identity(kindToken(nid), nid, msb, lsb, additional, impliedPatternNid(nid), buf.head());
    }

    /** The type token of a chronology part: the one its nid gives. */
    static byte chronologyToken(byte[] chronology) {
        return kindToken(readNid(ByteBuf.wrapForReading(chronology)));
    }

    // ---------------------------------------------------------------- the envelope

    /** A record's parts as stored: the chronology, then its versions. */
    public record Parts(byte[] chronology, ImmutableList<byte[]> versions) {
    }

    /**
     * Takes a record apart, copying each part out of it: for the merge, which reassembles parts.
     * A read decodes in place from {@link #chronologyReader} instead.
     *
     * @param record a format 3 record
     * @return its chronology and versions
     * @throws IllegalArgumentException if the record is not in format 3
     */
    public static Parts parts(byte[] record) {
        if (!isFormat3(record)) {
            throw notFormat3(record);
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
     * A record without its version under one stamp, read from the envelope alone: what a store
     * holds once that version is withdrawn
     * ({@link dev.ikm.tinkar.common.service.internal.EntityStore#mergeReplacingVersion}).
     *
     * @param record   a format 3 record, not a STAMP's
     * @param stampNid the stamp whose version to drop
     * @return the record without that version; {@code record} itself when it holds no version
     *         under the stamp; null when that version was its only one
     */
    public static byte[] withoutVersion(byte[] record, long stampNid) {
        Parts parts = parts(record);
        List<byte[]> kept = new ArrayList<>(parts.versions().size());
        for (byte[] version : parts.versions()) {
            if (stampNid(version) != stampNid) {
                kept.add(version);
            }
        }
        if (kept.size() == parts.versions().size()) {
            return record;
        }
        return kept.isEmpty() ? null : assemble(parts.chronology(), kept);
    }

    /**
     * The nid of the entity a record holds.
     *
     * @param record a format 3 record
     * @return the entity's nid
     */
    public static long nid(byte[] record) {
        return readNid(chronologyReader(record));
    }

    /**
     * The UUIDs of the entity a record holds.
     *
     * @param record a format 3 record
     * @return its UUIDs, the first one first
     */
    public static ImmutableList<UUID> uuids(byte[] record) {
        return readIdentity(chronologyReader(record)).uuids();
    }

    /**
     * The type token of the entity a record holds.
     *
     * @param record a format 3 record
     * @return the chronology's type token
     */
    public static byte chronologyType(byte[] record) {
        return kindToken(readNid(chronologyReader(record)));
    }

    /**
     * A buffer over a record, its head at the chronology: past the format byte and the
     * chronology's length. A read in place goes on from here, so nothing is copied out of the
     * record (IKE-Network/ike-issues#1313).
     *
     * @param record a format 3 record
     * @return the buffer
     * @throws IllegalArgumentException if the record is not in format 3
     */
    public static ByteBuf chronologyReader(byte[] record) {
        if (!isFormat3(record)) {
            throw notFormat3(record);
        }
        ByteBuf buf = ByteBuf.wrapForReading(record);
        buf.readByte();
        buf.readVarInt();
        return buf;
    }

    /**
     * The stamp nid of a version part of any entity but a STAMP: the typed reference at its head.
     *
     * @param version a version part
     * @return its stamp's nid
     */
    public static long stampNid(byte[] version) {
        return readStampNid(ByteBuf.wrapForReading(version));
    }

    /**
     * The time of a STAMP's version part: after its state, as {@code EntityCodec3} writes a STAMP
     * version, a varint of the time's bits.
     *
     * @param version a version part of a STAMP
     * @return its time: epoch milliseconds, {@link Long#MAX_VALUE} uncommitted, {@link Long#MIN_VALUE} canceled
     */
    public static long stampVersionTime(byte[] version) {
        ByteBuf buf = ByteBuf.wrapForReading(version);
        readConceptNid(buf);                              // state
        return buf.readVarLong();
    }

    /**
     * A stamp has at most two versions: the uncommitted placeholder ({@link Long#MAX_VALUE}) and
     * one terminal version, committed or canceled. The merge is total and order-free, so where
     * two records disagree it heals rather than fails: a committed version beats a canceled one
     * (content may sit under a committed stamp, never under a canceled one), the earliest of
     * several committed times wins (the same whichever record arrived first), and one placeholder
     * is kept. Anything dropped is reported through {@link IdentityAdvisories#stampVersionsDropped}.
     *
     * @param chronology the stamp's chronology bytes, for the advisory
     * @param versions   the merged version parts, in their stored order
     * @return the versions to keep, in the same order
     */
    static List<byte[]> canonicalStampVersions(byte[] chronology, List<byte[]> versions) {
        if (versions.size() <= 1) {
            return versions;
        }
        byte[] placeholder = null;
        byte[] terminal = null;
        long terminalTime = Long.MIN_VALUE;
        for (byte[] version : versions) {
            long time = stampVersionTime(version);
            if (time == Long.MAX_VALUE) {
                if (placeholder == null) {
                    placeholder = version;
                }
            } else if (terminal == null
                    || (terminalTime == Long.MIN_VALUE && time != Long.MIN_VALUE)
                    || (time != Long.MIN_VALUE && time < terminalTime)) {
                terminal = version;
                terminalTime = time;
            }
        }
        List<byte[]> kept = new ArrayList<>(2);
        List<Long> dropped = new ArrayList<>();
        for (byte[] version : versions) {
            if (version == placeholder || version == terminal) {
                kept.add(version);
            } else {
                dropped.add(stampVersionTime(version));
            }
        }
        if (!dropped.isEmpty()) {
            List<Long> keptTimes = new ArrayList<>(kept.size());
            for (byte[] version : kept) {
                keptTimes.add(stampVersionTime(version));
            }
            IdentityAdvisories.stampVersionsDropped(readIdentity(chronology).nid(), keptTimes, dropped);
        }
        return kept;
    }

    // ---------------------------------------------------------------- merge

    /**
     * Merges two records of one entity, as {@link PrimitiveDataService#merge} does for format 1:
     * the union of their versions, a version under a stamp already present in the newer record
     * yielding to the newer one, the versions of canceled stamps dropped when more than one
     * version remains (never for a stamp entity, whose canceled version is what records the
     * cancellation), a stamp entity held to its two-version shape by
     * {@link #canonicalStampVersions}, and the chronology's UUIDs unioned, with an advisory when
     * the entity gains UUIDs it did not hold. The versions are stored in their unsigned byte order, so the result
     * does not depend on the order the records were merged in, and a merge of equal records is
     * the record itself.
     *
     * @param oldBytes the record stored, or null
     * @param newBytes the record being written, or null
     * @return the merged record
     * @throws IllegalStateException if the records are not both in format 3
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
        if (!isFormat3(oldBytes) || !isFormat3(newBytes)) {
            throw new IllegalStateException("A store holds one entity format: merging a format "
                    + formatOf(oldBytes) + " record with a format " + formatOf(newBytes) + " one");
        }
        Parts older = parts(oldBytes);
        Parts newer = parts(newBytes);
        byte[] chronology = Arrays.equals(older.chronology(), newer.chronology())
                ? older.chronology()
                : mergeChronologies(older.chronology(), newer.chronology());
        boolean stampEntity = chronologyToken(chronology) == STAMP_TOKEN;

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
        if (stampEntity) {
            kept = canonicalStampVersions(chronology, kept);
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
        ByteBuf buf = ByteBuf.wrapForWriting(new byte[older.length + 2 * 8 * added.size() + 32]);
        writeIdentity(buf, held.nid(), held.mostSignificantBits(), held.leastSignificantBits(), additional);
        buf.write(older, held.restOffset(), rest);
        return Arrays.copyOf(buf.array(), buf.tail());
    }
}
