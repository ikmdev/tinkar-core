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
package dev.ikm.tinkar.integration.diagnostic;

import dev.ikm.tinkar.terms.KernelTerm;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.DiagnosticText;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DiagnosticText} against a spined-array store, which reads the public id of a nid from
 * the entity and keeps no other index from nid to UUID ({@code IKE-Network/ike-issues#1189}).
 * For a component that is referred to but was never written, the nid is the only identifier
 * this store has, and the text keeps it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DiagnosticTextSpinedArrayIT {

    private static final File DATASTORE_ROOT =
            TestConstants.createFilePathInTargetFromClassName.apply(DiagnosticTextSpinedArrayIT.class);

    @BeforeAll
    void startStore() {
        if (DATASTORE_ROOT.exists()) {
            FileUtil.recursiveDelete(DATASTORE_ROOT);
        }
        TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, DATASTORE_ROOT);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
    }

    @AfterAll
    void stopStoreAndRemoveItsDirectory() {
        TestHelper.stopDatabase();
        FileUtil.recursiveDelete(DATASTORE_ROOT);
    }

    @Test
    void aDescribedComponentIsItsDescriptionThenItsUuid() {
        int nid = KernelTerm.ENGLISH_LANGUAGE.nid();
        String description = PrimitiveData.textOptional(nid).orElseThrow();

        String text = DiagnosticText.component(nid);

        assertTrue(text.startsWith(description + " (UUID"), text);
        // Every UUID is named: none of them is the component's primordial UUID.
        for (UUID uuid : KernelTerm.ENGLISH_LANGUAGE.publicId().asUuidArray()) {
            assertTrue(text.contains(uuid.toString()), "the text names " + uuid + ": " + text);
        }
    }

    @Test
    void aComponentThatIsReferredToButWasNeverWrittenIsItsNidInThisStore() {
        int nid = PrimitiveData.nid(PublicIds.of(UUID.randomUUID().toString()));

        assertEquals("nid " + nid + " in this store, which has no public id for it", DiagnosticText.component(nid));
        assertEquals("nid " + nid + " in this store", DiagnosticText.name(nid));
    }

    @Test
    void aHandleForAComponentThatWasNeverWrittenSaysWhichNidWasAskedFor() {
        int nid = PrimitiveData.nid(PublicIds.of(UUID.randomUUID().toString()));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> EntityHandle.get(nid).expectEntity());

        assertEquals("Expected entity to be present but entity was absent: nid " + nid
                + " in this store, which has no public id for it", failure.getMessage());
    }

    @Test
    void aHandleAskedForByPublicIdNamesTheUuidAndNotTheNid() {
        String referredTo = UUID.randomUUID().toString();

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> EntityHandle.get(PublicIds.of(referredTo)).expectEntity());

        assertEquals("Expected entity to be present but entity was absent: UUID " + referredTo, failure.getMessage());
    }
}
