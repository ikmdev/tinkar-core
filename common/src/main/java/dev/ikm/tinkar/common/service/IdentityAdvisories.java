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

import dev.ikm.tinkar.common.alert.AlertObject;
import dev.ikm.tinkar.common.alert.AlertStreams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;

/**
 * Advisories about component identity: the rare events in which a store learns that a
 * component has more UUIDs than it held, or that components it held apart are one. Each is
 * logged as a warning and dispatched to the root alert stream; counts are kept for tests and
 * reports.
 * <p>
 * Public ids match when they share any UUID. A change set may bring a component under UUIDs the
 * store does not yet hold: the store adds them to the existing component, so a later lookup by
 * any of them finds it, and says so here.
 */
public final class IdentityAdvisories {
    private static final Logger LOG = LoggerFactory.getLogger(IdentityAdvisories.class);

    private static final LongAdder UUIDS_ADDED = new LongAdder();
    private static final LongAdder COMPONENTS_SHARING_UUIDS = new LongAdder();

    private IdentityAdvisories() {
    }

    /**
     * An existing component gained UUIDs it did not hold.
     *
     * @param nid   the component
     * @param held  the UUIDs it held
     * @param added the UUIDs added to it
     */
    public static void uuidsAdded(int nid, Collection<UUID> held, Collection<UUID> added) {
        UUIDS_ADDED.increment();
        String description = "Component " + nid + " " + held + " gained UUIDs " + added
                + ": a public id sharing one of its UUIDs carried them, so the component now holds them too.";
        LOG.warn(description);
        AlertStreams.getRoot().dispatch(AlertObject.makeWarning("UUIDs added to a component", description));
    }

    /**
     * One public id's UUIDs belong to more than one existing component: components the store held
     * apart are, by that public id, the same component.
     *
     * @param uuids the public id's UUIDs
     * @param nids  the components they belong to
     */
    public static void componentsShareUuids(Collection<UUID> uuids, Collection<Integer> nids) {
        COMPONENTS_SHARING_UUIDS.increment();
        String description = "The public id " + uuids + " names components " + nids
                + ", which the store holds as distinct: they are one component by that public id,"
                + " and the store has not reconciled them.";
        LOG.warn(description);
        AlertStreams.getRoot().dispatch(AlertObject.makeWarning("Components share UUIDs", description));
    }

    /**
     * @return how many times an existing component has gained UUIDs, since this JVM started
     */
    public static long uuidsAddedCount() {
        return UUIDS_ADDED.sum();
    }

    /**
     * @return how many public ids have named more than one existing component, since this JVM started
     */
    public static long componentsSharingUuidsCount() {
        return COMPONENTS_SHARING_UUIDS.sum();
    }
}
