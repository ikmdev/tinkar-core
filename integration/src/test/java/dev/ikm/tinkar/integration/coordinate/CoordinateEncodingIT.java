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
package dev.ikm.tinkar.integration.coordinate;

import dev.ikm.tinkar.terms.KernelTerm;
import dev.ikm.tinkar.common.binary.Encodable;
import dev.ikm.tinkar.common.binary.EncoderOutput;
import dev.ikm.tinkar.common.binary.EncodingExceptionUnchecked;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.coordinate.Coordinates;
import dev.ikm.tinkar.coordinate.edit.EditCoordinateRecord;
import dev.ikm.tinkar.coordinate.language.LanguageCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StampCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StampPathImmutable;
import dev.ikm.tinkar.coordinate.stamp.StampPositionRecord;
import dev.ikm.tinkar.coordinate.stamp.StateSet;
import dev.ikm.tinkar.coordinate.view.ViewCoordinateRecord;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An encoded coordinate holds no nid ({@code IKE-Network/ike-issues#1172}).
 *
 * <p>A stamp position used to write its path as a raw {@code int} nid, so every view coordinate
 * Komet keeps in preferences held a nid of the store that was open. From stream version 11 the
 * path is written as a public id. These tests fix the bytes that are written, that they read
 * back, that a stream of the first version is still read, and that the edit coordinate of a
 * stored view coordinate stays unread, as it always was.
 *
 * <p>Streams of the first version are composed here by hand, field by field, in the layout the
 * encoders had before the change. One real one is included as well: a view coordinate taken
 * from the preferences of a store written by a build that predates the change.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CoordinateEncodingIT {

    private static final File DATASTORE_ROOT = TestConstants.createFilePathInTargetFromClassName.apply(
            CoordinateEncodingIT.class);

    /**
     * A window's view coordinate as a version 10 build stored it in preferences, base64 as it
     * is kept there. Its stamp position holds the nid 134245248, assigned by the store it was
     * written from.
     */
    private static final String STORED_BY_A_VERSION_10_BUILD =
            "AAAACgAAADNkZXYuaWttLnRpbmthci5jb29yZGluYXRlLnZpZXcuVmlld0Nvb3JkaW5hdGVSZWNvcmQCAAAABkFD" +
            "VElWRQAAAAhJTkFDVElWRX//////////CABrgAAAAAAAAAEDAgGOWka6UpeS8WkxufmKEgbZBerGRzr5v+UlFOE1" +
            "tVhFAhkglWcR5YmU/v+BnNyfAQGk3gA5JiVYQopM0c5q6/AhAgP9oH+RUe1axpc1u7iOj0KYi/upRDllOUabyx6A" +
            "pdpjotb62YF99jOIlNgjjMBGWnkDAHkScHfJMrazT9kyVpvSv0mJ4xFiUlSXnWdHeXaDvb1eH+lAj68R27YGCAAg" +
            "DJpmAgEI+REswEFW07ibYyWPBwB0AVYfgXoTDl5WmE2RDpmRVYwCAp7MFUzkkFz4gF3Shl1irvMfIBamlg4R5YmU" +
            "/v+BnNyfAfaAyGj35V0OkfJhXsqPj9IBHyAfrJYOEeWJlP7/gZzcnwEfIB4Slg4R5YmU/v+BnNyfAZ8BGBIVyVsb" +
            "hfi7JivBsqIB6BPrkn0HUDWNQ+gSSfWzbgHTmz7NmoBQCaisC5R/lcp8AdApV9YTLVs8rbpQX1d42ZgBpTzELcB+" +
            "WTSWsy7eMmRHTgHH8Bg0NMpfi4+AGT++sS6uAQGlPMQtwH5ZNJazLt4yZEdOAgAAAAZBQ1RJVkUAAAAISU5BQ1RJ" +
            "VkUBAAH3SVtYZjA0maROIFK1/PBsAp7MFUzkkFz4gF3Shl1irvMfIBamlg4R5YmU/v+BnNyfAR8gDKaWDhHliZT+" +
            "/4Gc3J8BHyAMppYOEeWJlP7/gZzcnwKezBVM5JBc+IBd0oZdYq7zHyAWppYOEeWJlP7/gZzcnw==";

    /** The nid that stored view coordinate holds for its path. */
    private static final int PATH_NID_IN_THE_STORED_BYTES = 134245248;

    /** An encoder output whose bytes can be taken, for composing a stream field by field. */
    private static final class StreamBytes extends EncoderOutput {
        byte[] toByteArray() {
            return buf.asArray();
        }
    }

    @BeforeAll
    static void beforeAll() {
        TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, DATASTORE_ROOT);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
    }

    @AfterAll
    static void afterAll() {
        TestHelper.stopDatabase();
    }

    @Test
    void aStampPositionIsWrittenAsItsTimeAndThePublicIdOfItsPath() throws IOException {
        long time = 1_700_000_000_000L;
        StampPositionRecord position = StampPositionRecord.make(time, KernelTerm.DEVELOPMENT_PATH);

        // The whole stream, written here with nothing but java.io: version, class name, time,
        // then the path's public id as a count and its UUIDs. No nid anywhere.
        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(expected);
        writeHeader(out, Encodable.LATEST_VERSION, StampPositionRecord.class);
        out.writeLong(time);
        writePublicId(out, PrimitiveData.publicId(KernelTerm.DEVELOPMENT_PATH.nid()));

        assertArrayEquals(expected.toByteArray(), position.toBytes());
    }

    @Test
    void aStampPositionReadsBackAsItWasWritten() {
        StampPositionRecord position = StampPositionRecord.make(1_700_000_000_000L, KernelTerm.MASTER_PATH);
        StampPositionRecord decoded = Encodable.decode(position.toBytes());
        assertEquals(position, decoded);
        assertEquals(KernelTerm.MASTER_PATH.nid(), decoded.getPathForPositionNid());
    }

    @Test
    void aStampPositionOfTheFirstVersionIsStillRead() throws IOException {
        long time = 1_600_000_000_000L;
        int pathNid = KernelTerm.DEVELOPMENT_PATH.nid();

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(stream);
        writeHeader(out, Encodable.FIRST_VERSION, StampPositionRecord.class);
        out.writeLong(time);
        out.writeInt(pathNid);   // the first version's layout: the nid itself

        StampPositionRecord decoded = Encodable.decode(stream.toByteArray());
        assertEquals(new StampPositionRecord(time, pathNid), decoded);
    }

    @Test
    void aViewCoordinateReadsBackAsItWasWrittenAndHoldsNoNid() {
        ViewCoordinateRecord view = Coordinates.View.DefaultView();
        int pathNid = view.stampCoordinate().stampPosition().getPathForPositionNid();

        byte[] stream = view.toBytes();
        assertEquals(Encodable.LATEST_VERSION, ByteBuffer.wrap(stream).getInt());
        assertEquals(view, Encodable.<ViewCoordinateRecord>decode(stream));

        assertFalse(contains(stream, intBytes(pathNid)),
                "the stream written now does not hold the path's nid");
        assertTrue(contains(firstVersionStream(view), intBytes(pathNid)),
                "the first version's stream did, which is what the search above would have found");
    }

    @Test
    void aViewCoordinateOfTheFirstVersionIsStillRead() {
        ViewCoordinateRecord view = Coordinates.View.DefaultView();
        ViewCoordinateRecord decoded = Encodable.decode(firstVersionStream(view));
        assertEquals(view, decoded);
        assertEquals(KernelTerm.DEVELOPMENT_PATH.nid(),
                decoded.stampCoordinate().stampPosition().getPathForPositionNid());
    }

    @Test
    void aViewCoordinateStoredByAVersion10BuildIsStillRead() {
        byte[] stored = Base64.getDecoder().decode(STORED_BY_A_VERSION_10_BUILD);
        assertEquals(Encodable.FIRST_VERSION, ByteBuffer.wrap(stored).getInt(), "the fixture is a version 10 stream");

        ViewCoordinateRecord decoded = Encodable.decode(stored);

        StampCoordinateRecord stamp = decoded.stampCoordinate();
        assertEquals(StateSet.ACTIVE_AND_INACTIVE, stamp.allowedStates());
        assertEquals(Long.MAX_VALUE, stamp.stampPosition().time());
        // A version 10 stream holds its path as a nid, and it is read as that nid: right for
        // the store the stream was written from, and for no other. That is what version 11 ends.
        assertEquals(PATH_NID_IN_THE_STORED_BYTES, stamp.stampPosition().getPathForPositionNid());
        assertFalse(decoded.languageCoordinateList().isEmpty(), "its language coordinates are read");
        assertEquals(Coordinates.Edit.Default(), decoded.editCoordinate());
    }

    @Test
    void theEditCoordinateOfAStoredViewCoordinateIsNotReadBack() {
        // Kept as it always was (IKE-Network/ike-issues#745 persists a view override as a delta
        // and depends on it): the edit coordinate is written, and the decoded view coordinate
        // has the default one. Stamping streams with version 11 must not change that.
        EditCoordinateRecord edit = EditCoordinateRecord.make(KernelTerm.KOMET_USER, KernelTerm.SOLOR_MODULE,
                KernelTerm.SOLOR_OVERLAY_MODULE, KernelTerm.MASTER_PATH, KernelTerm.SANDBOX_PATH);
        assertNotEquals(Coordinates.Edit.Default(), edit, "precondition: not the default edit coordinate");
        ViewCoordinateRecord view = Coordinates.View.DefaultView().withEditCoordinate(edit);

        ViewCoordinateRecord decoded = Encodable.decode(view.toBytes());

        assertEquals(Coordinates.Edit.Default(), decoded.editCoordinate());
        assertEquals(view.withEditCoordinate(Coordinates.Edit.Default()), decoded,
                "everything but the edit coordinate reads back");
    }

    @Test
    void aStampPathIsWrittenWithPublicIdsAndReadsBack() throws IOException {
        StampPathImmutable path = StampPathImmutable.make(KernelTerm.DEVELOPMENT_PATH);
        assertFalse(path.getPathOrigins().isEmpty(), "precondition: the development path has an origin");

        ByteArrayOutputStream expected = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(expected);
        writeHeader(out, Encodable.LATEST_VERSION, StampPathImmutable.class);
        writePublicId(out, PrimitiveData.publicId(path.pathConceptNid()));
        out.writeByte(path.getPathOrigins().size());
        for (StampPositionRecord origin : path.getPathOrigins()) {
            out.writeLong(origin.time());
            writePublicId(out, PrimitiveData.publicId(origin.getPathForPositionNid()));
        }

        byte[] stream = path.toBytes();
        assertArrayEquals(expected.toByteArray(), stream);

        StampPathImmutable decoded = Encodable.decode(stream);
        assertEquals(path.pathConceptNid(), decoded.pathConceptNid());
        assertEquals(path.getPathOrigins(), decoded.getPathOrigins());
    }

    @Test
    void aStreamOfAVersionThisBuildDoesNotKnowIsRefused() throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(stream);
        writeHeader(out, Encodable.LATEST_VERSION + 1, StampPositionRecord.class);
        out.writeLong(0L);
        writePublicId(out, PrimitiveData.publicId(KernelTerm.DEVELOPMENT_PATH.nid()));

        EncodingExceptionUnchecked refused = assertThrows(EncodingExceptionUnchecked.class,
                () -> Encodable.decode(stream.toByteArray()));
        assertTrue(messages(refused).contains("Wrong encoding version"), messages(refused));
    }

    /**
     * A view coordinate as a stream of the first version: every field written by the encoder
     * that still writes it, and the stamp position written the way it was then, with its path
     * as a nid.
     */
    private static byte[] firstVersionStream(ViewCoordinateRecord view) {
        StampCoordinateRecord stamp = view.stampCoordinate();
        StreamBytes out = new StreamBytes();
        out.writeInt(Encodable.FIRST_VERSION);
        out.writeString(ViewCoordinateRecord.class.getName());
        stamp.allowedStates().encode(out);
        out.writeLong(stamp.stampPosition().time());
        out.writeInt(stamp.stampPosition().getPathForPositionNid());
        out.writeNidArray(stamp.moduleNids().toArray());
        out.writeNidArray(stamp.excludedModuleNids().toArray());
        out.writeNidArray(stamp.modulePriorityNidList().toArray());
        out.writeInt(view.languageCoordinateList().size());
        for (LanguageCoordinateRecord language : view.languageCoordinateList()) {
            language.encode(out);
        }
        view.logicCoordinate().encode(out);
        view.navigationCoordinate().encode(out);
        view.editCoordinate().encode(out);
        return out.toByteArray();
    }

    /** Writes what begins every stream: its version, then the name of the class it holds. */
    private static void writeHeader(DataOutputStream out, int version, Class<?> encoded) throws IOException {
        out.writeInt(version);
        byte[] name = encoded.getName().getBytes(StandardCharsets.UTF_8);
        out.writeInt(name.length);
        out.write(name);
    }

    /** Writes a public id the way the stream holds one: the count of its UUIDs, then each. */
    private static void writePublicId(DataOutputStream out, PublicId publicId) throws IOException {
        UUID[] uuids = publicId.asUuidArray();
        out.writeByte(uuids.length);
        for (UUID uuid : uuids) {
            out.writeLong(uuid.getMostSignificantBits());
            out.writeLong(uuid.getLeastSignificantBits());
        }
    }

    private static byte[] intBytes(int value) {
        return ByteBuffer.allocate(Integer.BYTES).putInt(value).array();
    }

    /** Whether {@code bytes} holds {@code part} anywhere. */
    private static boolean contains(byte[] bytes, byte[] part) {
        for (int start = 0; start + part.length <= bytes.length; start++) {
            int matched = 0;
            while (matched < part.length && bytes[start + matched] == part[matched]) {
                matched++;
            }
            if (matched == part.length) {
                return true;
            }
        }
        return false;
    }

    /** The messages of an exception and of everything that caused it. */
    private static String messages(Throwable thrown) {
        StringBuilder messages = new StringBuilder();
        for (Throwable cause = thrown; cause != null; cause = cause.getCause()) {
            messages.append(cause.getMessage()).append('\n');
        }
        return messages.toString();
    }
}
