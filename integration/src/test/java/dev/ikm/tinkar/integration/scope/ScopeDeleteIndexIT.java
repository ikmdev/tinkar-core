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
package dev.ikm.tinkar.integration.scope;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ScopeErasure;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.scope.DataUseIndex;
import dev.ikm.tinkar.entity.scope.Scope;
import dev.ikm.tinkar.entity.scope.ScopeOperations;
import dev.ikm.tinkar.fixtures.ForkedJvm;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.integration.scope.ScopeFixture.Doc;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A scope delete answered by the store's data-use index alone (IKE-Network/ike-issues#1313;
 * design {@code design-2026-10-20-instance-data}, "The data-use index"), with the read of every
 * component disabled ({@value ScopeOperations#FULL_PASS_PROPERTY}{@code =false}): a reference
 * into the scope carried in another scope's field still refuses the delete, and the STAMP the
 * scope shares with another document is still kept. The index is kept current as the store is
 * written, read back from its file when the store is opened again, and rebuilt by one pass when
 * the file is gone.
 */
class ScopeDeleteIndexIT {

    private static final File WORK = TestConstants.createFilePathInTargetFromClassName.apply(ScopeDeleteIndexIT.class);
    private static final String STORE = "store";
    private static final String REBUILDS = "rebuilds";

    @Test
    void theIndexAloneRefusesAReferenceIntoTheScopeAndKeepsASharedStamp() {
        FileUtil.recursiveDelete(WORK);
        assertTrue(WORK.mkdirs(), "Could not create " + WORK);
        Properties in = new Properties();
        in.setProperty(STORE, new File(WORK, "store").getPath());

        Properties written = ForkedJvm.run(Write.class, in);
        assertDeleted("as the store is written", written, "a");
        assertEquals("0", written.getProperty("delete.rebuilds"),
                "the writes and deletes after the load did not rebuild the index: it was kept current");

        File indexFile = new File(new File(in.getProperty(STORE)), DataUseIndex.FILE_NAME);
        assertTrue(indexFile.isFile(), "the index was written as the store closed");
        written.setProperty(REBUILDS, "0");
        Properties reopened = ForkedJvm.run(Reopen.class, written);
        assertDeleted("after the store is opened again", reopened, "c");
        assertEquals("0", reopened.getProperty("rebuilds"), "the index was read from its file, not rebuilt");

        assertTrue(indexFile.delete(), "the index file is removed");
        reopened.setProperty("first", "e");
        Properties rebuilt = ForkedJvm.run(Reopen.class, reopened);
        assertDeleted("with the index file gone", rebuilt, "e");
        assertEquals("1", rebuilt.getProperty("rebuilds"), "the absent index was rebuilt once, by one pass");
    }

    private static void assertDeleted(String when, Properties out, String doc) {
        assertTrue(out.getProperty(doc + ".refused", "").contains("lead into it"),
                when + ": the field reference into " + doc + " refuses its delete: " + out.getProperty(doc + ".refused"));
        assertEquals("true", out.getProperty(doc + ".link.indexed"), when + ": the linking scope's delete was answered by the index");
        assertEquals("1", out.getProperty(doc + ".link.outgoing"), when + ": the link's reference out goes with it");
        assertEquals("true", out.getProperty(doc + ".indexed"), when + ": " + doc + "'s delete was answered by the index");
        assertEquals("1", out.getProperty(doc + ".collected"), when + ": " + doc + "'s own STAMP is collected");
        assertEquals("1", out.getProperty(doc + ".kept"), when + ": the STAMP " + doc + " shares is kept");
        assertEquals("false", out.getProperty(doc + ".has.root"), when + ": " + doc + " is gone");
        assertEquals("true", out.getProperty(doc + ".has.shared"), when + ": the shared STAMP is still there");
        assertEquals("true", out.getProperty(doc + ".has.other"), when + ": the document beside it is whole");
    }

    /**
     * Writes documents and a link into one of them, deletes in the order the link allows, then
     * writes another pair, linked the same way, for the next stage.
     */
    static class Write implements ForkedJvm.Stage {
        @Override
        public void run(Properties in, Properties out) {
            out.putAll(in);
            System.setProperty(ScopeOperations.FULL_PASS_PROPERTY, "false");
            TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, new File(in.getProperty(STORE)));
            try {
                TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
                ScopeFixture fixture = new ScopeFixture();
                fixture.seed();
                // A parallel load may classify a STAMP before its module's taxonomy arrives; the
                // index settles that once, after the load. What follows must not rebuild it.
                assertTrue(index().current());
                long before = index().rebuilds();
                deleteLinkedPair(fixture, "a", out);
                out.setProperty("delete.rebuilds", Long.toString(index().rebuilds() - before));
                writeLinkedPair(fixture, "c", out);
                writeLinkedPair(fixture, "e", out);
            } finally {
                TestHelper.stopDatabase();
            }
        }
    }

    /** Opens the store again, deletes the pair the property {@code first} names (c by default), and reports the rebuilds. */
    static class Reopen implements ForkedJvm.Stage {
        @Override
        public void run(Properties in, Properties out) {
            out.putAll(in);
            System.setProperty(ScopeOperations.FULL_PASS_PROPERTY, "false");
            TestHelper.reopenDataBase(DataStore.SPINED_ARRAY_STORE, new File(in.getProperty(STORE)));
            try {
                String doc = in.getProperty("first", "c");
                deleteWritten(doc, in, out);
                out.setProperty(REBUILDS, Long.toString(index().rebuilds()));
            } finally {
                TestHelper.stopDatabase();
            }
        }
    }

    private static DataUseIndex index() {
        return (DataUseIndex) ((ScopeErasure) PrimitiveData.get()).dataUse().orElseThrow();
    }

    private static void deleteLinkedPair(ScopeFixture fixture, String doc, Properties out) {
        writeLinkedPair(fixture, doc, out);
        deleteWritten(doc, out, out);
    }

    /**
     * Two documents under one STAMP, the first revised under a STAMP of its own, and a third
     * scope whose element lists the first one's line in a field.
     */
    private static void writeLinkedPair(ScopeFixture fixture, String doc, Properties out) {
        Doc[] docs = fixture.documents(doc + "1", doc + "2");
        fixture.revise(docs[0], "zebracorn " + doc + " revised");
        PublicId link = fixture.link(docs[0]);
        EntityVersion first = null;
        for (Object o : EntityHandle.get(PrimitiveData.nid(docs[0].root())).expectEntity().versions()) {
            first = (EntityVersion) o;
        }
        put(out, doc + ".pattern", docs[0].pattern());
        put(out, doc + ".root", docs[0].root());
        put(out, doc + ".other", docs[1].root());
        put(out, doc + ".shared", PrimitiveData.publicId(first.stampNid()));
        put(out, doc + ".link", link);
    }

    private static void deleteWritten(String doc, Properties in, Properties out) {
        Scope scope = Scope.of(PrimitiveData.nid(id(in, doc + ".pattern")));
        try {
            ScopeOperations.delete(scope);
            out.setProperty(doc + ".refused", "not refused");
        } catch (IllegalStateException refused) {
            out.setProperty(doc + ".refused", refused.getMessage());
        }
        ScopeOperations.Deleted link = ScopeOperations.delete(Scope.of(PrimitiveData.nid(id(in, doc + ".link"))));
        out.setProperty(doc + ".link.indexed", Boolean.toString(link.indexed()));
        out.setProperty(doc + ".link.outgoing", Integer.toString(link.outgoing()));
        ScopeOperations.Deleted deleted = ScopeOperations.delete(Scope.of(PrimitiveData.nid(id(in, doc + ".pattern"))));
        out.setProperty(doc + ".indexed", Boolean.toString(deleted.indexed()));
        out.setProperty(doc + ".collected", Integer.toString(deleted.stamps()));
        out.setProperty(doc + ".kept", Integer.toString(deleted.stampsKept()));
        out.setProperty(doc + ".has.root", Boolean.toString(PrimitiveData.get().hasPublicId(id(in, doc + ".root"))));
        out.setProperty(doc + ".has.shared", Boolean.toString(PrimitiveData.get().hasPublicId(id(in, doc + ".shared"))));
        out.setProperty(doc + ".has.other", Boolean.toString(PrimitiveData.get().hasPublicId(id(in, doc + ".other"))));
    }

    private static void put(Properties out, String key, PublicId id) {
        out.setProperty(key, id.asUuidArray()[0].toString());
    }

    private static PublicId id(Properties in, String key) {
        return PublicIds.of(UUID.fromString(in.getProperty(key)));
    }
}
