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
package dev.ikm.tinkar.integration.builder;

import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.entity.StampService;
import dev.ikm.tinkar.entity.builder.KnowledgeSet;
import dev.ikm.tinkar.entity.builder.PrimordialStamp;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.State;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The non-existent stamp comes from the data, not from the running application: a store holds
 * it when a set that declares it ({@link KnowledgeSet#stamp(Stamp)}) has been written into it,
 * and a store started without one does not gain it.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class NonExistentStampIT {

    private static final KnowledgeSet TEST_SET = KnowledgeSet.of("5b2f6c1e-8a4d-5f7b-9c3e-1d6a8b4f2e70");

    private boolean absentBeforeTheSetWasWritten;

    @BeforeAll
    void startUseAndWrite() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        // The stamp service used to write the non-existent stamp the first time it was used.
        StampService.get().getPathNidsInUse();
        absentBeforeTheSetWasWritten =
                EntityHandle.get(PublicIds.of(PrimitiveData.NONEXISTENT_STAMP_UUID)).isAbsent();

        TEST_SET.stamp(Stamp.nonExistent());
        TEST_SET.write();
    }

    @AfterAll
    void stop() {
        TestHelper.stopDatabase();
    }

    @Test
    @DisplayName("A store started without a set that declares it does not hold the non-existent stamp")
    void notWrittenByTheApplication() {
        assertTrue(absentBeforeTheSetWasWritten,
                "the store held the non-existent stamp before any set declaring it was written");
    }

    @Test
    @DisplayName("A set that declares the non-existent stamp writes it, as StampRecord.nonExistentStamp() names it")
    void writtenFromTheSet() {
        StampEntity<?> written = EntityHandle.get(PublicIds.of(PrimitiveData.NONEXISTENT_STAMP_UUID)).expectStamp();
        StampRecord named = StampRecord.nonExistentStamp();
        PrimordialStamp declared = Stamp.nonExistent();

        assertEquals(named.nid(), written.nid(), "nid");
        assertEquals(1, written.versions().size(), "versions");
        assertEquals(State.PRIMORDIAL, written.state(), "state");
        assertEquals(named.state(), written.state(), "state, against StampRecord");
        assertEquals(PrimitiveData.PRE_INCEPTION_TIME, written.time(), "time");
        assertEquals(named.time(), written.time(), "time, against StampRecord");
        assertEquals(named.authorNid(), written.authorNid(), "author");
        assertEquals(named.moduleNid(), written.moduleNid(), "module");
        assertEquals(named.pathNid(), written.pathNid(), "path");
        assertEquals(declared.author().nid(), written.authorNid(), "author, against the declaration");
        assertEquals(declared.module().nid(), written.moduleNid(), "module, against the declaration");
        assertEquals(declared.path().nid(), written.pathNid(), "path, against the declaration");
    }

    @Test
    @DisplayName("Declaring the non-existent stamp again, or writing the set again, changes nothing")
    void idempotent() {
        int before = EntityHandle.get(PublicIds.of(PrimitiveData.NONEXISTENT_STAMP_UUID)).expectStamp().versions().size();
        TEST_SET.stamp(Stamp.nonExistent());
        TEST_SET.write();
        assertEquals(before,
                EntityHandle.get(PublicIds.of(PrimitiveData.NONEXISTENT_STAMP_UUID)).expectStamp().versions().size());
    }
}
