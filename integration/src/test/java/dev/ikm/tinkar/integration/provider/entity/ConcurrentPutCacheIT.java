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
package dev.ikm.tinkar.integration.provider.entity;

import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.entity.ConceptRecord;
import dev.ikm.tinkar.entity.ConceptRecordBuilder;
import dev.ikm.tinkar.entity.ConceptVersionRecord;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.EntityBinding;
import dev.ikm.tinkar.terms.KernelTerm;
import dev.ikm.tinkar.terms.State;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Two threads putting versions of one concept at once: the entity cache must hold the union
 * the store holds, never the earlier merge cached last. The entity provider serializes the
 * merge and the cache entry per nid.
 */
class ConcurrentPutCacheIT {

    private static final File DATASTORE_ROOT =
            TestConstants.createFilePathInTargetFromClassName.apply(ConcurrentPutCacheIT.class);
    private static final int ROUNDS = 300;

    @BeforeAll
    static void start() {
        FileUtil.recursiveDelete(DATASTORE_ROOT);
        TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, DATASTORE_ROOT);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
    }

    @AfterAll
    static void stop() {
        TestHelper.stopDatabase();
    }

    @Test
    void theCacheHoldsTheUnionOfConcurrentPuts() throws Exception {
        StampEntity first = stamp(System.currentTimeMillis());
        StampEntity second = stamp(System.currentTimeMillis() + 1);
        EntityService.get().putEntity(first);
        EntityService.get().putEntity(second);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        int stale = 0;
        try {
            for (int round = 0; round < ROUNDS; round++) {
                UUID uuid = UUID.randomUUID();
                ConceptRecord withFirst = concept(uuid, first.nid());
                ConceptRecord withSecond = concept(uuid, second.nid());
                CyclicBarrier together = new CyclicBarrier(2);
                Future<?> one = pool.submit(() -> {
                    together.await();
                    EntityService.get().putEntity(withFirst);
                    return null;
                });
                Future<?> other = pool.submit(() -> {
                    together.await();
                    EntityService.get().putEntity(withSecond);
                    return null;
                });
                one.get();
                other.get();
                // EntityHandle reads through the entity cache.
                Entity<?> cached = EntityHandle.get(withFirst.nid()).expectEntity();
                if (cached.versions().size() != 2) {
                    stale++;
                }
            }
        } finally {
            pool.shutdown();
        }
        assertEquals(0, stale, "rounds in which the cache held fewer versions than the store, of " + ROUNDS);
    }

    private static StampEntity stamp(long time) {
        return StampRecord.make(UUID.randomUUID(), State.ACTIVE, time,
                KernelTerm.USER.publicId(), KernelTerm.PRIMORDIAL_MODULE.publicId(), KernelTerm.DEVELOPMENT_PATH.publicId());
    }

    private static ConceptRecord concept(UUID uuid, long stampNid) {
        RecordListBuilder<ConceptVersionRecord> versions = RecordListBuilder.make();
        long nid = ScopedValue.where(PrimitiveData.SCOPED_PATTERN_PUBLICID_FOR_NID, EntityBinding.Concept.pattern().publicId())
                .call(() -> PrimitiveData.nid(PublicIds.of(uuid)));
        ConceptRecord concept = ConceptRecordBuilder.builder()
                .leastSignificantBits(uuid.getLeastSignificantBits())
                .mostSignificantBits(uuid.getMostSignificantBits())
                .nid(nid)
                .versions(versions)
                .build();
        versions.addAndBuild(new ConceptVersionRecord(concept, stampNid));
        return concept;
    }
}
