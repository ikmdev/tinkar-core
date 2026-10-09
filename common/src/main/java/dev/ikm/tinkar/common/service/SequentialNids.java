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
package dev.ikm.tinkar.common.service;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.ListIterable;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ConcurrentMap;
import java.util.function.IntSupplier;

/**
 * Nid assignment for the sequential providers (the spined array, persistent or ephemeral, and
 * the gRPC client), which assign {@code int} nids in sequence from {@link #FIRST_NID} and
 * keep them as {@code int}, on disk and in memory. Their nids are widened at the provider's edge;
 * nothing here is part of the provider contract.
 */
public final class SequentialNids {

    /** The first nid a sequential provider assigns. */
    public static final int FIRST_NID = Integer.MIN_VALUE + 1;

    private SequentialNids() {
    }

    public static int nidForUuids(ConcurrentMap<UUID, Integer> uuidNidMap, IntSupplier newNid, ImmutableList<UUID> uuidList) {
        switch (uuidList.size()) {
            case 0:
                throw new IllegalStateException("uuidList cannot be empty");
            case 1: {
                return valueOrGenerateAndPut(uuidList.get(0), uuidNidMap, newNid);
            }
        }
        return valueOrGenerateForList(uuidList.toSortedList(), uuidNidMap, newNid);
    }

    public static int valueOrGenerateAndPut(UUID uuid,
                                     ConcurrentMap<UUID, Integer> uuidNidMap,
                                     IntSupplier newNid) {
        Integer nid = uuidNidMap.get(uuid);
        if (nid != null) {
            return nid;
        }
        nid = uuidNidMap.computeIfAbsent(uuid, uuidKey -> newNid.getAsInt());
        return nid;
    }

    /**
     * The nid of a public id with more than one UUID: the nid of its least UUID the store knows,
     * or a new one if it knows none. Every UUID no component holds is then mapped to that nid, so a
     * later lookup by it finds the component; a UUID another component holds keeps its nid.
     * <p>
     * When the UUIDs belong to more than one component, the public id names components the store
     * holds as distinct. That is advised ({@link IdentityAdvisories#componentsShareUuids}) and left
     * for review, not reconciled here: the store goes on, the id resolving by its least known UUID.
     *
     * @param sortedUuidList the public id's UUIDs, sorted
     */
    public static int valueOrGenerateForList(ListIterable<UUID> sortedUuidList,
                                      ConcurrentMap<UUID, Integer> uuidNidMap,
                                      IntSupplier newNid) {
        boolean missingMap = false;
        int foundValue = Integer.MIN_VALUE;
        java.util.TreeSet<Long> foundNids = new java.util.TreeSet<>();

        for (UUID uuid : sortedUuidList) {
            Integer nid = uuidNidMap.get(uuid);
            if (nid == null) {
                missingMap = true;
            } else {
                foundNids.add((long) nid);
                if (foundValue == Integer.MIN_VALUE) {
                    foundValue = nid;
                }
            }
        }
        if (foundNids.size() > 1) {
            IdentityAdvisories.componentsShareUuids(sortedUuidList.toList(), foundNids);
        }
        if (!missingMap) {
            return foundValue;
        }
        if (foundValue == Integer.MIN_VALUE) {
            foundValue = valueOrGenerateAndPut(sortedUuidList.get(0), uuidNidMap, newNid);
        }
        for (UUID uuid : sortedUuidList) {
            uuidNidMap.putIfAbsent(uuid, foundValue);
        }
        return foundValue;
    }

    public static int nidForUuids(ConcurrentMap<UUID, Integer> uuidNidMap, IntSupplier newNid, UUID... uuids) {
        switch (uuids.length) {
            case 0:
                throw new IllegalStateException("uuidList cannot be empty");
            case 1:
                return valueOrGenerateAndPut(uuids[0], uuidNidMap, newNid);
        }
        UUID[] sorted = uuids.clone();
        Arrays.sort(sorted);
        return valueOrGenerateForList(Lists.immutable.of(sorted), uuidNidMap, newNid);
    }

}
