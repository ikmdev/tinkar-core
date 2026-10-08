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

/** The format 2 envelope: its parts, its identity, and the merge of two records of one entity. */
class EntityRecordFormat2Test {

    private static final long CONCEPT = Nid.compose64(2, 17);
    private static final long STAMP_A = Nid.compose64(3, 1);
    private static final long STAMP_B = Nid.compose64(3, 2);
    private static final long STAMP_C = Nid.compose64(3, 3);
    private static final UUID UUID_1 = UUID.fromString("0f6c6a6e-6b1d-4c0c-8b8e-3f1a2b3c4d01");
    private static final UUID UUID_2 = UUID.fromString("0f6c6a6e-6b1d-4c0c-8b8e-3f1a2b3c4d02");

    private static byte[] chronology(byte token, long nid, UUID... uuids) {
        ByteBuf buf = ByteBuf.wrapForWriting(new byte[256]);
        buf.writeByte(token);
        long[] additional = new long[2 * (uuids.length - 1)];
        for (int i = 1; i < uuids.length; i++) {
            additional[2 * (i - 1)] = uuids[i].getMostSignificantBits();
            additional[2 * (i - 1) + 1] = uuids[i].getLeastSignificantBits();
        }
        EntityRecordFormat2.writeIdentity(buf, nid, uuids[0].getMostSignificantBits(), uuids[0].getLeastSignificantBits(), additional);
        buf.writeInt(0xCAFE); // the rest of the chronology, whatever the kind writes
        return Arrays.copyOf(buf.array(), buf.tail());
    }

    private static byte[] version(byte token, long stampNid, int payload) {
        ByteBuf buf = ByteBuf.wrapForWriting(new byte[64]);
        buf.writeByte(token);
        EntityRecordFormat2.writeNid(buf, stampNid);
        buf.writeInt(payload);
        return Arrays.copyOf(buf.array(), buf.tail());
    }

    @Test
    void aRecordComesApartAsItWasAssembled() {
        byte[] chronology = chronology((byte) 1, CONCEPT, UUID_1, UUID_2);
        byte[] v1 = version((byte) 4, STAMP_A, 1);
        byte[] v2 = version((byte) 4, STAMP_B, 2);
        byte[] record = EntityRecordFormat2.assemble(chronology, List.of(v1, v2));

        assertTrue(EntityRecordFormat2.isFormat2(record));
        assertEquals(2, record[0]);
        EntityRecordFormat2.Parts parts = EntityRecordFormat2.parts(record);
        assertArrayEquals(chronology, parts.chronology());
        assertEquals(2, parts.versions().size());
        assertArrayEquals(v1, parts.versions().get(0));
        assertArrayEquals(v2, parts.versions().get(1));
        assertEquals(CONCEPT, EntityRecordFormat2.nid(record));
        assertEquals(List.of(UUID_1, UUID_2), EntityRecordFormat2.uuids(record).castToList());
        assertEquals(STAMP_B, EntityRecordFormat2.stampNid(v2));
        EntityRecordFormat2.Identity identity = EntityRecordFormat2.readIdentity(chronology);
        assertEquals(1, identity.token());
        assertEquals(chronology.length - 4, identity.restOffset(), "the rest begins after the UUIDs");
    }

    @Test
    void aFormat1RecordIsNotFormat2() {
        byte[] format1 = new byte[]{0, 0, 0, 2, 0, 0, 0, 30, 1, 3};
        assertFalse(EntityRecordFormat2.isFormat2(format1));
        assertFalse(EntityRecordFormat2.isFormat2(null));
        assertThrows(IllegalArgumentException.class, () -> EntityRecordFormat2.parts(format1));
    }

    @Test
    void theWriterRefusesANidThatIsNot64Bit() {
        ByteBuf buf = ByteBuf.wrapForWriting(new byte[16]);
        assertThrows(IllegalArgumentException.class, () -> EntityRecordFormat2.writeNid(buf, 12_345L));
        assertThrows(IllegalArgumentException.class, () -> EntityRecordFormat2.writeNid(buf, Nid.NOT_APPLICABLE));
        assertThrows(IllegalArgumentException.class, () -> EntityRecordFormat2.writeNid(buf, -1L));
    }

    @Test
    void mergeUnionsVersionsAndKeepsTheNewerUnderOneStamp() {
        byte[] chronology = chronology((byte) 1, CONCEPT, UUID_1);
        byte[] a = version((byte) 4, STAMP_A, 1);
        byte[] bOld = version((byte) 4, STAMP_B, 2);
        byte[] bNew = version((byte) 4, STAMP_B, 3);
        byte[] c = version((byte) 4, STAMP_C, 4);
        byte[] stored = EntityRecordFormat2.assemble(chronology, List.of(a, bOld));
        byte[] written = EntityRecordFormat2.assemble(chronology, List.of(bNew, c));

        byte[] merged = EntityRecordFormat2.merge(stored, written, stamp -> false);
        EntityRecordFormat2.Parts parts = EntityRecordFormat2.parts(merged);
        assertEquals(3, parts.versions().size());
        List<byte[]> versions = parts.versions().castToList();
        assertTrue(versions.stream().anyMatch(v -> Arrays.equals(v, a)));
        assertTrue(versions.stream().anyMatch(v -> Arrays.equals(v, bNew)), "the newer version under stamp B");
        assertTrue(versions.stream().noneMatch(v -> Arrays.equals(v, bOld)), "the older version under stamp B");
        assertTrue(versions.stream().anyMatch(v -> Arrays.equals(v, c)));
        // Order-free: merging the other way round gives the same bytes but for which B wins.
        byte[] other = EntityRecordFormat2.merge(written, stored, stamp -> false);
        assertEquals(3, EntityRecordFormat2.parts(other).versions().size());
        // Idempotent.
        assertArrayEquals(merged, EntityRecordFormat2.merge(merged, written, stamp -> false));
        assertSame(stored, EntityRecordFormat2.merge(stored, stored, stamp -> false));
        assertSame(stored, EntityRecordFormat2.merge(null, stored, stamp -> false));
        assertSame(stored, EntityRecordFormat2.merge(stored, null, stamp -> false));
    }

    @Test
    void mergeDropsTheVersionsOfCanceledStampsWhenOthersRemain() {
        byte[] chronology = chronology((byte) 1, CONCEPT, UUID_1);
        byte[] a = version((byte) 4, STAMP_A, 1);
        byte[] b = version((byte) 4, STAMP_B, 2);
        byte[] stored = EntityRecordFormat2.assemble(chronology, List.of(a));
        byte[] written = EntityRecordFormat2.assemble(chronology, List.of(b));

        byte[] merged = EntityRecordFormat2.merge(stored, written, stamp -> stamp == STAMP_A);
        List<byte[]> versions = EntityRecordFormat2.parts(merged).versions().castToList();
        assertEquals(1, versions.size());
        assertArrayEquals(b, versions.get(0));

        // A stamp entity keeps every version, canceled or not: the canceled version is the record of it.
        byte[] stampChronology = chronology(PrimitiveDataService.STAMP_DATA_TYPE, STAMP_A, UUID_2);
        byte[] s1 = version((byte) 25, STAMP_A, 1);
        byte[] s2 = version((byte) 25, STAMP_A, 2);
        byte[] stampMerged = EntityRecordFormat2.merge(
                EntityRecordFormat2.assemble(stampChronology, List.of(s1)),
                EntityRecordFormat2.assemble(stampChronology, List.of(s2)),
                stamp -> true);
        assertEquals(2, EntityRecordFormat2.parts(stampMerged).versions().size());
    }

    @Test
    void mergeUnionsTheUuidsOfTheChronologies() {
        byte[] stored = EntityRecordFormat2.assemble(chronology((byte) 1, CONCEPT, UUID_1), List.of(version((byte) 4, STAMP_A, 1)));
        byte[] written = EntityRecordFormat2.assemble(chronology((byte) 1, CONCEPT, UUID_1, UUID_2), List.of(version((byte) 4, STAMP_A, 1)));

        byte[] merged = EntityRecordFormat2.merge(stored, written, stamp -> false);
        assertEquals(List.of(UUID_1, UUID_2), EntityRecordFormat2.uuids(merged).castToList());
        assertEquals(CONCEPT, EntityRecordFormat2.nid(merged));
        EntityRecordFormat2.Identity identity = EntityRecordFormat2.readIdentity(EntityRecordFormat2.parts(merged).chronology());
        byte[] chronology = EntityRecordFormat2.parts(merged).chronology();
        assertEquals(0xCAFE, ByteBuf.wrapForReading(Arrays.copyOfRange(chronology, identity.restOffset(), chronology.length)).readInt(),
                "the rest of the chronology follows the unioned UUIDs");
    }

    @Test
    void mergeRefusesRecordsOfDifferentFormats() {
        byte[] format2 = EntityRecordFormat2.assemble(chronology((byte) 1, CONCEPT, UUID_1), List.of());
        byte[] format1 = new byte[]{0, 0, 0, 1, 0, 0, 0, 30, 1, 1, 0, 0, 0, 0};
        assertThrows(IllegalStateException.class, () -> EntityRecordFormat2.merge(format1, format2, stamp -> false));
        assertThrows(IllegalStateException.class, () -> EntityRecordFormat2.merge(format2, format1, stamp -> false));
    }
}
