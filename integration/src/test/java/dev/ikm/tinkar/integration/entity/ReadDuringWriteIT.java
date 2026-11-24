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
package dev.ikm.tinkar.integration.entity;

import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.DataActivity;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.entity.ConceptRecord;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.fixtures.StarterDataEphemeralProvider;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import dev.ikm.tinkar.terms.State;
import org.eclipse.collections.api.factory.Lists;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a change set loads into an open knowledge base is what is read afterward, whoever was reading while
 * it was written (IKE-Network/ike-issues#1313). A load writes without caching
 * ({@link EntityService#putEntityNoCache}); a record was cleared from the cache before it was merged and not
 * after, so a read in between put the record of before the load back, and every later read gave that one until
 * the record was next written: a document a colleague had changed opened as it was, after the sync that
 * brought the change. Two readers read throughout, as the windows of an open knowledge base do.
 */
@ExtendWith(StarterDataEphemeralProvider.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ReadDuringWriteIT {

    private static final int WRITES = 2_000;

    /** Fewer, for each is also indexed for search. */
    private static final int NAMES = 200;

    @Test
    void aVersionLoadedWhileItsComponentIsReadIsTheOneReadAfterward() throws Exception {
        UUID concept = UUID.randomUUID();
        long nid = EntityService.get().nidForConcept(PublicIds.of(concept));
        whileReading(() -> EntityHandle.get(nid).entity(), () -> {
            for (int i = 1; i <= WRITES; i++) {
                EntityService.get().putEntityNoCache(ConceptRecord.build(concept, stamp(i).lastVersion()), DataActivity.SYNCHRONIZABLE_EDIT);
                assertEquals(i, EntityHandle.get(nid).expectConcept().versions().size(), "the versions read after write " + i);
            }
        });
    }

    @Test
    void aNameLoadedWhileItsConceptIsReadIsTheNameReadAfterward() throws Exception {
        StampEntity<?> stamp = stamp(1);
        AtomicLong reading = new AtomicLong(KernelConcept.USER.nid());
        whileReading(() -> PrimitiveData.text(reading.get()), () -> {
            for (int i = 1; i <= NAMES; i++) {
                UUID concept = UUID.randomUUID();
                EntityService.get().putEntityNoCache(ConceptRecord.build(concept, stamp.lastVersion()), DataActivity.SYNCHRONIZABLE_EDIT);
                long nid = EntityService.get().nidForConcept(PublicIds.of(concept));
                describe(nid, "Concept " + i + " (Test)", KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE, stamp);
                reading.set(nid);
                assertEquals("Concept " + i + " (Test)", PrimitiveData.text(nid), "the name read after write " + i);
                describe(nid, "Concept " + i, KernelConcept.REGULAR_NAME_DESCRIPTION_TYPE, stamp);
                assertEquals("Concept " + i, PrimitiveData.text(nid), "the regular name read after write " + i);
            }
        });
    }

    private static void describe(long nid, String text, KernelConcept type, StampEntity<?> stamp) {
        EntityService.get().putEntityNoCache(SemanticRecord.build(UUID.randomUUID(), KernelPattern.DESCRIPTION_PATTERN.nid(), nid,
                stamp.lastVersion(), Lists.immutable.of(
                        EntityProxy.Concept.make(KernelConcept.ENGLISH_LANGUAGE.nid()),
                        text,
                        EntityProxy.Concept.make(KernelConcept.DESCRIPTION_NOT_CASE_SENSITIVE.nid()),
                        EntityProxy.Concept.make(type.nid()))), DataActivity.SYNCHRONIZABLE_EDIT);
    }

    private static StampEntity<?> stamp(int seconds) {
        StampRecord stamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, 1_795_000_000_000L + seconds * 1000L,
                KernelConcept.USER.publicId(), KernelConcept.PRIMORDIAL_MODULE.publicId(), KernelConcept.DEVELOPMENT_PATH.publicId());
        EntityService.get().putEntityNoCache(stamp, DataActivity.SYNCHRONIZABLE_EDIT);
        return stamp;
    }

    /** Runs the writes with two threads reading until they are done; a reader's failure fails the test. */
    private static void whileReading(Runnable read, Runnable writes) throws Exception {
        AtomicBoolean done = new AtomicBoolean();
        Throwable[] failed = new Throwable[1];
        Thread[] readers = new Thread[2];
        for (int i = 0; i < readers.length; i++) {
            readers[i] = Thread.ofPlatform().name("reader " + i).start(() -> {
                try {
                    while (!done.get()) {
                        read.run();
                    }
                } catch (Throwable e) {
                    failed[0] = e;
                }
            });
        }
        try {
            writes.run();
        } finally {
            done.set(true);
            for (Thread reader : readers) {
                reader.join();
            }
        }
        assertEquals(null, failed[0], "a reader failed");
    }
}
