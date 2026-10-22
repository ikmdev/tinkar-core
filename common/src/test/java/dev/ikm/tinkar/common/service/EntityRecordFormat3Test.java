package dev.ikm.tinkar.common.service;

import dev.ikm.tinkar.common.id.Nid;
import io.activej.bytebuf.ByteBuf;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The format 3 envelope: its parts, its identity, its typed references, and the merge of two records of one entity. */
class EntityRecordFormat3Test {

    private static final long CONCEPT = Nid.compose64(2, 17);
    private static final long STAMP_A = Nid.compose64(3, 1);
    private static final long STAMP_B = Nid.compose64(3, 2);
    private static final long STAMP_C = Nid.compose64(3, 3);
    private static final UUID UUID_1 = UUID.fromString("0f6c6a6e-6b1d-4c0c-8b8e-3f1a2b3c4d01");
    private static final UUID UUID_2 = UUID.fromString("0f6c6a6e-6b1d-4c0c-8b8e-3f1a2b3c4d02");

    /** A chronology: the nid gives the kind, so the token only checks that it does. */
    private static byte[] chronology(byte token, long nid, UUID... uuids) {
        assertEquals(token, EntityRecordFormat3.kindToken(nid), "the nid gives the kind");
        ByteBuf buf = ByteBuf.wrapForWriting(new byte[256]);
        long[] additional = new long[2 * (uuids.length - 1)];
        for (int i = 1; i < uuids.length; i++) {
            additional[2 * (i - 1)] = uuids[i].getMostSignificantBits();
            additional[2 * (i - 1) + 1] = uuids[i].getLeastSignificantBits();
        }
        EntityRecordFormat3.writeIdentity(buf, nid, uuids[0].getMostSignificantBits(), uuids[0].getLeastSignificantBits(), additional);
        buf.writeInt(0xCAFE); // the rest of the chronology, whatever the kind writes
        return Arrays.copyOf(buf.array(), buf.tail());
    }

    /** A version of an entity other than a STAMP: its stamp as a typed reference, then a payload. */
    private static byte[] version(byte token, long stampNid, int payload) {
        ByteBuf buf = ByteBuf.wrapForWriting(new byte[64]);
        EntityRecordFormat3.writeStampNid(buf, stampNid);
        buf.writeInt(payload);
        return Arrays.copyOf(buf.array(), buf.tail());
    }

    @Test
    void aRecordComesApartAsItWasAssembled() {
        byte[] chronology = chronology((byte) 1, CONCEPT, UUID_1, UUID_2);
        byte[] v1 = version((byte) 4, STAMP_A, 1);
        byte[] v2 = version((byte) 4, STAMP_B, 2);
        byte[] record = EntityRecordFormat3.assemble(chronology, List.of(v1, v2));

        assertTrue(EntityRecordFormat3.isFormat3(record));
        assertEquals(3, record[0]);
        EntityRecordFormat3.Parts parts = EntityRecordFormat3.parts(record);
        assertArrayEquals(chronology, parts.chronology());
        assertEquals(2, parts.versions().size());
        assertArrayEquals(v1, parts.versions().get(0));
        assertArrayEquals(v2, parts.versions().get(1));
        assertEquals(CONCEPT, EntityRecordFormat3.nid(record));
        assertEquals(List.of(UUID_1, UUID_2), EntityRecordFormat3.uuids(record).castToList());
        assertEquals(STAMP_B, EntityRecordFormat3.stampNid(v2));
        EntityRecordFormat3.Identity identity = EntityRecordFormat3.readIdentity(chronology);
        assertEquals(1, identity.token());
        assertEquals(chronology.length - 4, identity.restOffset(), "the rest begins after the UUIDs");
    }

    @Test
    void aFormat1RecordIsNotFormat3() {
        byte[] format1 = new byte[]{0, 0, 0, 2, 0, 0, 0, 30, 1, 3};
        assertFalse(EntityRecordFormat3.isFormat3(format1));
        assertFalse(EntityRecordFormat3.isFormat3(null));
        assertThrows(IllegalArgumentException.class, () -> EntityRecordFormat3.parts(format1));
    }

    @Test
    void aRecordOfTheRetiredFormat2IsRefusedByName() {
        byte[] format2 = new byte[]{2, 1, 0, 0};
        assertFalse(EntityRecordFormat3.isFormat3(format2));
        IllegalArgumentException refused = assertThrows(IllegalArgumentException.class, () -> EntityRecordFormat3.parts(format2));
        assertTrue(refused.getMessage().contains("format 2"), refused.getMessage());
    }

    @Test
    void aTypedReferenceIsItsElementAloneAndRefusesAnotherKind() {
        ByteBuf buf = ByteBuf.wrapForWriting(new byte[32]);
        EntityRecordFormat3.writeConceptNid(buf, CONCEPT);
        EntityRecordFormat3.writeStampNid(buf, STAMP_A);
        EntityRecordFormat3.writePatternNid(buf, Nid.compose64(1, 300));
        EntityRecordFormat3.writeConceptNid(buf, Nid.compose64(2, Nid.MAX_SEQUENCE_64));
        assertEquals(1 + 1 + 2 + 5, buf.tail(), "each reference is its element varint");
        ByteBuf read = ByteBuf.wrapForReading(Arrays.copyOf(buf.array(), buf.tail()));
        assertEquals(CONCEPT, EntityRecordFormat3.readConceptNid(read));
        assertEquals(STAMP_A, EntityRecordFormat3.readStampNid(read));
        assertEquals(Nid.compose64(1, 300), EntityRecordFormat3.readPatternNid(read));
        assertEquals(Nid.compose64(2, Nid.MAX_SEQUENCE_64), EntityRecordFormat3.readConceptNid(read));
        assertThrows(IllegalArgumentException.class, () -> EntityRecordFormat3.writeConceptNid(buf, STAMP_A),
                "a STAMP is not a concept");
        assertThrows(IllegalArgumentException.class, () -> EntityRecordFormat3.writeConceptNid(buf, Nid.compose64(7, 1)),
                "a semantic is not a concept");
    }

    @Test
    void theNidGivesTheKindAndASemanticsPattern() {
        assertEquals(EntityRecordFormat3.PATTERN_TOKEN, EntityRecordFormat3.kindToken(Nid.compose64(1, 9)));
        assertEquals(EntityRecordFormat3.CONCEPT_TOKEN, EntityRecordFormat3.kindToken(CONCEPT));
        assertEquals(EntityRecordFormat3.STAMP_TOKEN, EntityRecordFormat3.kindToken(STAMP_A));
        assertEquals(EntityRecordFormat3.SEMANTIC_TOKEN, EntityRecordFormat3.kindToken(Nid.compose64(40, 9)));
        assertEquals(Nid.compose64(1, 40), EntityRecordFormat3.impliedPatternNid(Nid.compose64(40, 9)));
        EntityRecordFormat3.requireKindOfNid(EntityRecordFormat3.SEMANTIC_TOKEN, Nid.compose64(40, 9), Nid.compose64(1, 40));
        assertThrows(IllegalArgumentException.class, () -> EntityRecordFormat3.requireKindOfNid(
                EntityRecordFormat3.SEMANTIC_TOKEN, CONCEPT, Nid.compose64(1, 40)), "a semantic under a concept's nid");
        assertThrows(IllegalArgumentException.class, () -> EntityRecordFormat3.requireKindOfNid(
                EntityRecordFormat3.SEMANTIC_TOKEN, Nid.compose64(40, 9), Nid.compose64(1, 41)), "a semantic under another pattern's nid");
        assertThrows(IllegalArgumentException.class, () -> EntityRecordFormat3.requireKindOfNid(
                EntityRecordFormat3.PATTERN_TOKEN, CONCEPT, 0), "a pattern under a concept's nid");
    }

    @Test
    void signedVarintsRoundTripTheirExtremes() {
        int[] ints = {0, 1, -1, 63, -64, 64, Integer.MAX_VALUE, Integer.MIN_VALUE};
        long[] longs = {0, -1, 1_767_225_600L, -62_135_596_800L, Long.MAX_VALUE, Long.MIN_VALUE};
        ByteBuf buf = ByteBuf.wrapForWriting(new byte[256]);
        for (int value : ints) {
            EntityRecordFormat3.writeSignedVarInt(buf, value);
        }
        for (long value : longs) {
            EntityRecordFormat3.writeSignedVarLong(buf, value);
        }
        ByteBuf read = ByteBuf.wrapForReading(Arrays.copyOf(buf.array(), buf.tail()));
        for (int value : ints) {
            assertEquals(value, EntityRecordFormat3.readSignedVarInt(read));
        }
        for (long value : longs) {
            assertEquals(value, EntityRecordFormat3.readSignedVarLong(read));
        }
        assertEquals(0, read.readRemaining());
    }

    @Test
    void theWriterRefusesANidThatIsNot64Bit() {
        ByteBuf buf = ByteBuf.wrapForWriting(new byte[16]);
        assertThrows(IllegalArgumentException.class, () -> EntityRecordFormat3.writeNid(buf, 12_345L));
        assertThrows(IllegalArgumentException.class, () -> EntityRecordFormat3.writeNid(buf, Nid.NOT_APPLICABLE));
        assertThrows(IllegalArgumentException.class, () -> EntityRecordFormat3.writeNid(buf, -1L));
    }

    @Test
    void mergeUnionsVersionsAndKeepsTheNewerUnderOneStamp() {
        byte[] chronology = chronology((byte) 1, CONCEPT, UUID_1);
        byte[] a = version((byte) 4, STAMP_A, 1);
        byte[] bOld = version((byte) 4, STAMP_B, 2);
        byte[] bNew = version((byte) 4, STAMP_B, 3);
        byte[] c = version((byte) 4, STAMP_C, 4);
        byte[] stored = EntityRecordFormat3.assemble(chronology, List.of(a, bOld));
        byte[] written = EntityRecordFormat3.assemble(chronology, List.of(bNew, c));

        byte[] merged = EntityRecordFormat3.merge(stored, written, stamp -> false);
        EntityRecordFormat3.Parts parts = EntityRecordFormat3.parts(merged);
        assertEquals(3, parts.versions().size());
        List<byte[]> versions = parts.versions().castToList();
        assertTrue(versions.stream().anyMatch(v -> Arrays.equals(v, a)));
        assertTrue(versions.stream().anyMatch(v -> Arrays.equals(v, bNew)), "the newer version under stamp B");
        assertTrue(versions.stream().noneMatch(v -> Arrays.equals(v, bOld)), "the older version under stamp B");
        assertTrue(versions.stream().anyMatch(v -> Arrays.equals(v, c)));
        // Order-free: merging the other way round gives the same bytes but for which B wins.
        byte[] other = EntityRecordFormat3.merge(written, stored, stamp -> false);
        assertEquals(3, EntityRecordFormat3.parts(other).versions().size());
        // Idempotent.
        assertArrayEquals(merged, EntityRecordFormat3.merge(merged, written, stamp -> false));
        assertSame(stored, EntityRecordFormat3.merge(stored, stored, stamp -> false));
        assertSame(stored, EntityRecordFormat3.merge(null, stored, stamp -> false));
        assertSame(stored, EntityRecordFormat3.merge(stored, null, stamp -> false));
    }

    private static final long STAMP_NID = Nid.compose64(3, 9);
    private static final long STATE = Nid.compose64(2, 5);

    /** A STAMP's version part as the codec writes it: state, time, author, module, path; no stamp nid, which is its own. */
    private static byte[] stampVersion(long time) {
        return stampVersion(STAMP_NID, time);
    }

    private static byte[] stampVersion(long stampNid, long time) {
        ByteBuf buf = ByteBuf.wrapForWriting(new byte[64]);
        EntityRecordFormat3.writeConceptNid(buf, STATE);
        buf.writeVarLong(time);
        EntityRecordFormat3.writeConceptNid(buf, CONCEPT);
        EntityRecordFormat3.writeConceptNid(buf, CONCEPT);
        EntityRecordFormat3.writeConceptNid(buf, CONCEPT);
        return Arrays.copyOf(buf.array(), buf.tail());
    }

    private static List<Long> stampTimes(byte[] record) {
        return EntityRecordFormat3.parts(record).versions().collect(EntityRecordFormat3::stampVersionTime).castToList();
    }

    @Test
    void stampVersionTimeReadsPastTheState() {
        assertEquals(1_700_000_000_000L, EntityRecordFormat3.stampVersionTime(stampVersion(1_700_000_000_000L)));
        assertEquals(Long.MAX_VALUE, EntityRecordFormat3.stampVersionTime(stampVersion(Long.MAX_VALUE)));
        assertEquals(Long.MIN_VALUE, EntityRecordFormat3.stampVersionTime(stampVersion(Long.MIN_VALUE)));
    }

    @Test
    void mergeHoldsAStampToItsTwoVersionsWithCommitBeatingCancel() {
        byte[] chronology = chronology(PrimitiveDataService.STAMP_DATA_TYPE, STAMP_NID, UUID_1);
        byte[] placeholder = stampVersion(Long.MAX_VALUE);
        byte[] committed = stampVersion(1_700_000_000_000L);
        byte[] canceled = stampVersion(Long.MIN_VALUE);
        byte[] committedRecord = EntityRecordFormat3.assemble(chronology, List.of(placeholder, committed));
        byte[] canceledRecord = EntityRecordFormat3.assemble(chronology, List.of(placeholder, canceled));
        long before = IdentityAdvisories.stampVersionsDroppedCount();

        byte[] merged = EntityRecordFormat3.merge(committedRecord, canceledRecord, stamp -> false);

        assertEquals(List.of(1_700_000_000_000L, Long.MAX_VALUE), sorted(stampTimes(merged)), "the placeholder and the committed version");
        assertArrayEquals(merged, EntityRecordFormat3.merge(canceledRecord, committedRecord, stamp -> false), "order-free");
        assertEquals(before + 2, IdentityAdvisories.stampVersionsDroppedCount(), "each heal is advised");
        assertArrayEquals(merged, EntityRecordFormat3.merge(merged, committedRecord, stamp -> false), "idempotent, and silent once healed");
        assertEquals(before + 2, IdentityAdvisories.stampVersionsDroppedCount());
    }

    @Test
    void mergeKeepsTheEarliestOfTwoCommits() {
        byte[] chronology = chronology(PrimitiveDataService.STAMP_DATA_TYPE, STAMP_NID, UUID_1);
        byte[] placeholder = stampVersion(Long.MAX_VALUE);
        byte[] first = EntityRecordFormat3.assemble(chronology, List.of(placeholder, stampVersion(1_700_000_000_000L)));
        byte[] second = EntityRecordFormat3.assemble(chronology, List.of(placeholder, stampVersion(1_700_000_000_500L)));

        byte[] merged = EntityRecordFormat3.merge(first, second, stamp -> false);

        assertEquals(List.of(1_700_000_000_000L, Long.MAX_VALUE), sorted(stampTimes(merged)));
        assertArrayEquals(merged, EntityRecordFormat3.merge(second, first, stamp -> false));
    }

    @Test
    void mergeLeavesALegalStampAlone() {
        byte[] chronology = chronology(PrimitiveDataService.STAMP_DATA_TYPE, STAMP_NID, UUID_1);
        byte[] uncommitted = EntityRecordFormat3.assemble(chronology, List.of(stampVersion(Long.MAX_VALUE)));
        byte[] committed = EntityRecordFormat3.assemble(chronology, List.of(stampVersion(Long.MAX_VALUE), stampVersion(1_700_000_000_000L)));
        long before = IdentityAdvisories.stampVersionsDroppedCount();

        byte[] merged = EntityRecordFormat3.merge(uncommitted, committed, stamp -> false);

        assertEquals(List.of(1_700_000_000_000L, Long.MAX_VALUE), sorted(stampTimes(merged)), "commit adds the committed version beside the placeholder");
        assertEquals(before, IdentityAdvisories.stampVersionsDroppedCount(), "nothing to advise");
    }

    private static List<Long> sorted(List<Long> times) {
        List<Long> copy = new java.util.ArrayList<>(times);
        copy.sort(null);
        return copy;
    }

    @Test
    void mergeDropsTheVersionsOfCanceledStampsWhenOthersRemain() {
        byte[] chronology = chronology((byte) 1, CONCEPT, UUID_1);
        byte[] a = version((byte) 4, STAMP_A, 1);
        byte[] b = version((byte) 4, STAMP_B, 2);
        byte[] stored = EntityRecordFormat3.assemble(chronology, List.of(a));
        byte[] written = EntityRecordFormat3.assemble(chronology, List.of(b));

        byte[] merged = EntityRecordFormat3.merge(stored, written, stamp -> stamp == STAMP_A);
        List<byte[]> versions = EntityRecordFormat3.parts(merged).versions().castToList();
        assertEquals(1, versions.size());
        assertArrayEquals(b, versions.get(0));

        // A stamp entity keeps its canceled version: it is the record of the cancellation, beside the placeholder.
        byte[] stampChronology = chronology(PrimitiveDataService.STAMP_DATA_TYPE, STAMP_A, UUID_2);
        byte[] s1 = stampVersion(STAMP_A, Long.MAX_VALUE);
        byte[] s2 = stampVersion(STAMP_A, Long.MIN_VALUE);
        byte[] stampMerged = EntityRecordFormat3.merge(
                EntityRecordFormat3.assemble(stampChronology, List.of(s1)),
                EntityRecordFormat3.assemble(stampChronology, List.of(s2)),
                stamp -> true);
        assertEquals(2, EntityRecordFormat3.parts(stampMerged).versions().size());
    }

    @Test
    void mergeUnionsTheUuidsOfTheChronologies() {
        byte[] stored = EntityRecordFormat3.assemble(chronology((byte) 1, CONCEPT, UUID_1), List.of(version((byte) 4, STAMP_A, 1)));
        byte[] written = EntityRecordFormat3.assemble(chronology((byte) 1, CONCEPT, UUID_1, UUID_2), List.of(version((byte) 4, STAMP_A, 1)));

        byte[] merged = EntityRecordFormat3.merge(stored, written, stamp -> false);
        assertEquals(List.of(UUID_1, UUID_2), EntityRecordFormat3.uuids(merged).castToList());
        assertEquals(CONCEPT, EntityRecordFormat3.nid(merged));
        EntityRecordFormat3.Identity identity = EntityRecordFormat3.readIdentity(EntityRecordFormat3.parts(merged).chronology());
        byte[] chronology = EntityRecordFormat3.parts(merged).chronology();
        assertEquals(0xCAFE, ByteBuf.wrapForReading(Arrays.copyOfRange(chronology, identity.restOffset(), chronology.length)).readInt(),
                "the rest of the chronology follows the unioned UUIDs");
    }

    @Test
    void mergeRefusesRecordsOfDifferentFormats() {
        byte[] format3 = EntityRecordFormat3.assemble(chronology((byte) 1, CONCEPT, UUID_1), List.of());
        byte[] format1 = new byte[]{0, 0, 0, 1, 0, 0, 0, 30, 1, 1, 0, 0, 0, 0};
        assertThrows(IllegalStateException.class, () -> EntityRecordFormat3.merge(format1, format3, stamp -> false));
        assertThrows(IllegalStateException.class, () -> EntityRecordFormat3.merge(format3, format1, stamp -> false));
    }
}
