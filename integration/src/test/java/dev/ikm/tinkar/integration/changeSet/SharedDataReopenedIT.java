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
package dev.ikm.tinkar.integration.changeSet;

import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.entity.scope.SharedFolder;
import dev.ikm.tinkar.fixtures.ForkedJvm;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.fixtures.TestTags;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.KernelConcept;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A knowledge base opened again knows the shared documents it holds, and says which of their change sets
 * are missing from the folder of shared data (IKE-Network/ike-issues#1313, step 4 of
 * {@code design-2026-11-22-knowledge-shared-local}): what it holds is read from the store, not from what
 * was written since it was opened. Each opening runs in a JVM of its own.
 */
@Tag(TestTags.STAGED)
class SharedDataReopenedIT {

    private static final File WORK = TestConstants.createFilePathInTargetFromClassName.apply(SharedDataReopenedIT.class);
    private static final String STORE = "store";
    private static final String MISSING = "missing";

    @Test
    void whatIsMissingIsSaidAfterAReopenToo() {
        FileUtil.recursiveDelete(WORK);
        assertTrue(WORK.mkdirs(), "Could not create " + WORK);
        Properties in = new Properties();
        in.setProperty(STORE, new File(WORK, "store").getPath());

        Properties written = ForkedJvm.run(Write.class, in);
        assertEquals("[notes/a.adoc]", written.getProperty(MISSING), "where it was written");
        Properties reopened = ForkedJvm.run(Reopen.class, written);
        assertEquals("[notes/a.adoc]", reopened.getProperty(MISSING), "and after the knowledge base is opened again");
    }

    /** Writes a shared module with no change set in the folder of shared data, then stops the store. */
    static class Write implements ForkedJvm.Stage {
        @Override
        public void run(Properties in, Properties out) {
            out.putAll(in);
            File store = new File(in.getProperty(STORE));
            TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, store);
            try {
                TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
                ConceptFacade module = EntityProxy.Concept.make(PublicIds.of(UUID.nameUUIDFromBytes("shared-reopened module".getBytes())));
                SharedDataIT.writeModule(module, "notes/a.adoc", KernelConcept.SHARED_MODULE);
                out.setProperty(MISSING, missing(store));
            } finally {
                TestHelper.stopDatabase();
            }
        }
    }

    /** Opens the store again and asks what is missing. */
    static class Reopen implements ForkedJvm.Stage {
        @Override
        public void run(Properties in, Properties out) {
            out.putAll(in);
            File store = new File(in.getProperty(STORE));
            TestHelper.reopenDataBase(DataStore.SPINED_ARRAY_STORE, store);
            try {
                out.setProperty(MISSING, missing(store));
            } finally {
                TestHelper.stopDatabase();
            }
        }
    }

    private static String missing(File store) {
        return SharedFolder.apply(SharedFolder.of(store.toPath()), KernelConcept.USER).missing().toString();
    }
}
