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
package dev.ikm.tinkar.provider.mvstore;

import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression tests for nid reuse after an unclean shutdown, the root cause of the
 * intermittent {@code ConceptRecord cannot be cast to SemanticEntity} failure.
 */
class NidAllocatorTest {
    private static final int FIRST_NID = Integer.MIN_VALUE + 1;
    private static final int BLOCK = 16;

    private static MVStore open(Path dir) {
        return new MVStore.Builder().fileName(dir.resolve("mvstore.dat").toString()).open();
    }

    /** Assign {@code count} fresh UUIDs, the way PrimitiveDataService does. */
    private static Set<Integer> assign(MVMap<UUID, Integer> uuidToNid, MVMap<Integer, byte[]> components,
                                       NidAllocator allocator, int count) {
        Set<Integer> nids = new HashSet<>();
        for (int i = 0; i < count; i++) {
            int nid = uuidToNid.computeIfAbsent(UUID.randomUUID(), u -> allocator.newNid());
            components.put(nid, new byte[]{1});
            nids.add(nid);
        }
        return nids;
    }

    @Test
    void crashAfterCommitDoesNotReuseNids(@TempDir Path dir) {
        Set<Integer> before;
        MVStore store = open(dir);
        MVMap<UUID, Integer> uuidToNid = store.openMap("uuidToNidMap");
        MVMap<Integer, byte[]> components = store.openMap("nidToComponentMap");
        NidAllocator allocator = new NidAllocator(uuidToNid, FIRST_NID, BLOCK, List.of(components));
        before = assign(uuidToNid, components, allocator, 100);
        store.commit();          // what background auto-commit does
        store.closeImmediately(); // crash: no save(), no close()

        store = open(dir);
        uuidToNid = store.openMap("uuidToNidMap");
        components = store.openMap("nidToComponentMap");
        allocator = new NidAllocator(uuidToNid, FIRST_NID, BLOCK, List.of(components));
        Set<Integer> after = assign(uuidToNid, components, allocator, 100);
        store.close();

        Set<Integer> overlap = new HashSet<>(before);
        overlap.retainAll(after);
        assertTrue(overlap.isEmpty(), "nids reissued after crash: " + overlap);
    }

    @Test
    void legacyStoreWithStaleCounterIsRecovered(@TempDir Path dir) {
        MVStore store = open(dir);
        MVMap<UUID, Integer> uuidToNid = store.openMap("uuidToNidMap");
        MVMap<Integer, byte[]> components = store.openMap("nidToComponentMap");
        // Simulate the old format: counter saved at FIRST_NID + 10, but auto-commit
        // persisted assignments up to FIRST_NID + 49, some without a component yet.
        uuidToNid.put(NidAllocator.WATERMARK_KEY, FIRST_NID + 10);
        for (int i = 0; i < 50; i++) {
            uuidToNid.put(UUID.randomUUID(), FIRST_NID + i);
            if (i < 30) {
                components.put(FIRST_NID + i, new byte[]{1});
            }
        }
        store.commit();
        store.closeImmediately();

        store = open(dir);
        uuidToNid = store.openMap("uuidToNidMap");
        components = store.openMap("nidToComponentMap");
        NidAllocator allocator = new NidAllocator(uuidToNid, FIRST_NID, BLOCK, List.of(components));
        assertEquals(FIRST_NID + 50, allocator.newNid());
        assertTrue(uuidToNid.containsKey(NidAllocator.FORMAT_KEY));
        store.close();
    }

    @Test
    void nidKeyedMapAheadOfWatermarkIsGuarded(@TempDir Path dir) {
        // A commit captured the pattern map after a merge whose nid's watermark the
        // identity map's captured root does not yet cover.
        MVStore store = open(dir);
        MVMap<UUID, Integer> uuidToNid = store.openMap("uuidToNidMap");
        MVMap<Integer, byte[]> components = store.openMap("nidToComponentMap");
        MVMap<Integer, Integer> patterns = store.openMap("nidToPatternNidMap");
        NidAllocator allocator = new NidAllocator(uuidToNid, FIRST_NID, BLOCK,
                List.of(components, patterns));
        int watermark = uuidToNid.get(NidAllocator.WATERMARK_KEY);
        patterns.put(watermark + 5, 42);
        store.commit();
        store.closeImmediately();

        store = open(dir);
        uuidToNid = store.openMap("uuidToNidMap");
        components = store.openMap("nidToComponentMap");
        patterns = store.openMap("nidToPatternNidMap");
        allocator = new NidAllocator(uuidToNid, FIRST_NID, BLOCK, List.of(components, patterns));
        assertEquals(watermark + 6, allocator.newNid());
        store.close();
    }

    @Test
    void watermarkIsPersistedBeforeNidsAreIssued(@TempDir Path dir) {
        MVStore store = open(dir);
        MVMap<UUID, Integer> uuidToNid = store.openMap("uuidToNidMap");
        MVMap<Integer, byte[]> components = store.openMap("nidToComponentMap");
        NidAllocator allocator = new NidAllocator(uuidToNid, FIRST_NID, BLOCK, List.of(components));
        for (int i = 0; i < 5 * BLOCK + 3; i++) {
            int nid = allocator.newNid();
            assertTrue(uuidToNid.get(NidAllocator.WATERMARK_KEY) > nid,
                    "watermark must exceed every issued nid");
        }
        store.close();
    }

    @Test
    void concurrentAllocationIsUnique(@TempDir Path dir) {
        MVStore store = open(dir);
        MVMap<UUID, Integer> uuidToNid = store.openMap("uuidToNidMap");
        MVMap<Integer, byte[]> components = store.openMap("nidToComponentMap");
        NidAllocator allocator = new NidAllocator(uuidToNid, FIRST_NID, BLOCK, List.of(components));
        Set<Integer> seen = ConcurrentHashMap.newKeySet();
        int count = 20_000;
        IntStream.range(0, count).parallel().forEach(i -> {
            int nid = allocator.newNid();
            assertTrue(seen.add(nid), "duplicate nid " + nid);
        });
        assertEquals(count, seen.size());
        int max = seen.stream().mapToInt(Integer::intValue).max().orElseThrow();
        assertTrue(uuidToNid.get(NidAllocator.WATERMARK_KEY) > max);
        store.close();
    }
}
