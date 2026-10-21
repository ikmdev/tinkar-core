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
import dev.ikm.tinkar.common.service.PrimitiveDataSearchResult;
import dev.ikm.tinkar.common.service.internal.EntityStore;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.entity.scope.Scope;
import dev.ikm.tinkar.entity.scope.ScopeOperations;
import dev.ikm.tinkar.fixtures.ForkedJvm;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.integration.scope.ScopeFixture.Doc;
import org.eclipse.collections.api.factory.primitive.LongLists;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Arrays;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A deleted scope stays deleted when the spined-array store is saved and opened again
 * (IKE-Network/ike-issues#1313): its components, public ids, citations and search text are gone
 * after the restart, and the document beside it is whole.
 */
class ScopeDeleteRestartIT {

    private static final File WORK = TestConstants.createFilePathInTargetFromClassName.apply(ScopeDeleteRestartIT.class);
    private static final String STORE = "store";

    @Test
    void aDeletedScopeStaysDeletedAfterARestart() {
        FileUtil.recursiveDelete(WORK);
        assertTrue(WORK.mkdirs(), "Could not create " + WORK);
        Properties in = new Properties();
        in.setProperty(STORE, new File(WORK, "store").getPath());

        Properties written = ForkedJvm.run(Write.class, in);
        assertEquals("1", written.getProperty("collected"), "the deleted document's own STAMP was collected");

        Properties reopened = ForkedJvm.run(Reopen.class, written);
        for (String part : new String[] {"pattern", "root", "line", "cited", "comment", "revision"}) {
            assertEquals("false", reopened.getProperty("a." + part), "after the restart the store lacks the deleted " + part);
        }
        assertEquals("true", reopened.getProperty("b.root"), "the other document is there");
        assertEquals("true", reopened.getProperty("b.comment"));
        assertEquals("false", reopened.getProperty("term.cites.a"), "the term's citations no longer list the deleted line");
        assertEquals("true", reopened.getProperty("term.cites.b"));
        assertEquals("false", reopened.getProperty("search.a"), "the deleted text is not in the search index");
        assertEquals("true", reopened.getProperty("search.b"));
    }

    /** Writes two documents, revises one, deletes its scope, and stops the store. */
    static class Write implements ForkedJvm.Stage {
        @Override
        public void run(Properties in, Properties out) {
            out.putAll(in);
            TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, new File(in.getProperty(STORE)));
            try {
                TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
                ScopeFixture fixture = new ScopeFixture();
                fixture.seed();
                Doc[] docs = fixture.documents("Ar", "Br");
                fixture.revise(docs[0], "zebracornAr revised");
                Doc a = docs[0];
                dev.ikm.tinkar.entity.EntityVersion newest = null;
                for (Object o : dev.ikm.tinkar.entity.EntityHandle.get(PrimitiveData.nid(a.comment())).expectEntity().versions()) {
                    dev.ikm.tinkar.entity.EntityVersion v = (dev.ikm.tinkar.entity.EntityVersion) o;
                    if (newest == null || v.time() > newest.time()) {
                        newest = v;
                    }
                }
                long revision = newest.stampNid();                  // the revision's STAMP, A's alone
                put(out, "a.pattern", a.pattern());
                put(out, "a.root", a.root());
                put(out, "a.line", a.line());
                put(out, "a.cited", a.cited());
                put(out, "a.comment", a.comment());
                put(out, "a.revision", PrimitiveData.publicId(revision));
                put(out, "b.root", docs[1].root());
                put(out, "b.comment", docs[1].comment());
                put(out, "b.cited", docs[1].cited());
                put(out, "term", fixture.term.publicId());
                out.setProperty("a.cited.nid", Long.toString(PrimitiveData.nid(a.cited())));
                out.setProperty("a.comment.nid", Long.toString(PrimitiveData.nid(a.comment())));
                ScopeOperations.Deleted deleted = ScopeOperations.delete(Scope.of(PrimitiveData.nid(a.pattern())));
                out.setProperty("collected", Integer.toString(deleted.stamps()));
            } finally {
                TestHelper.stopDatabase();
            }
        }
    }

    /** Opens the store again and reports what it holds. */
    static class Reopen implements ForkedJvm.Stage {
        @Override
        public void run(Properties in, Properties out) throws Exception {
            TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, new File(in.getProperty(STORE)));
            try {
                for (String part : new String[] {"pattern", "root", "line", "cited", "comment", "revision"}) {
                    out.setProperty("a." + part, Boolean.toString(PrimitiveData.get().hasPublicId(id(in, "a." + part))));
                }
                out.setProperty("b.root", Boolean.toString(PrimitiveData.get().hasPublicId(id(in, "b.root"))));
                out.setProperty("b.comment", Boolean.toString(PrimitiveData.get().hasPublicId(id(in, "b.comment"))));
                long[] citing = EntityStore.current().semanticNidsForComponent(PrimitiveData.nid(id(in, "term")));
                out.setProperty("term.cites.a", Boolean.toString(LongLists.immutable.of(citing).contains(Long.parseLong(in.getProperty("a.cited.nid")))));
                out.setProperty("term.cites.b", Boolean.toString(LongLists.immutable.of(citing).contains(PrimitiveData.nid(id(in, "b.cited")))));
                long aComment = Long.parseLong(in.getProperty("a.comment.nid"));
                out.setProperty("search.a", Boolean.toString(finds("zebracornAr", aComment)));
                out.setProperty("search.b", Boolean.toString(finds("zebracornBr", PrimitiveData.nid(id(in, "b.comment")))));
            } finally {
                TestHelper.stopDatabase();
            }
        }
    }

    private static void put(Properties out, String key, PublicId id) {
        out.setProperty(key, id.asUuidArray()[0].toString());
    }

    private static PublicId id(Properties in, String key) {
        return PublicIds.of(UUID.fromString(in.getProperty(key)));
    }

    private static boolean finds(String text, long nid) throws Exception {
        PrimitiveDataSearchResult[] results = PrimitiveData.get().search(text, 100);
        return results != null && Arrays.stream(results).anyMatch(r -> r.nid() == nid);
    }
}
