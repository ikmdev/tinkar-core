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

import java.util.function.LongConsumer;

/**
 * A store's index of what uses its instance and ephemeral data (IKE-Network/ike-issues#1313;
 * design {@code design-2026-10-20-instance-data}, "The data-use index"): the components written
 * under each instance or ephemeral STAMP, and the components referencing each instance or
 * ephemeral component other than as their pattern or referenced component, which the pattern and
 * referencing indexes hold. A scope delete asks it instead of reading every other component.
 *
 * <p>The index is a superset: an entry may outlive what it records, so a caller confirms a hit by
 * reading that one component; it never lacks one.
 */
public interface DataUse {

    /**
     * Brings the index up to date, rebuilding it with one pass over the store when it is absent
     * or was found stale.
     *
     * @return whether the index can answer
     */
    boolean current();

    /**
     * Visits the components with a version written under an instance or ephemeral STAMP.
     *
     * @param stampNid the STAMP
     * @param users    receives each component's nid
     */
    void forEachWrittenUnder(long stampNid, LongConsumer users);

    /**
     * Visits the components whose versions reference a component other than as their pattern or
     * referenced component.
     *
     * @param targetNid the referenced component, instance or ephemeral data
     * @param referrers receives each referring component's nid
     */
    void forEachReferrer(long targetNid, LongConsumer referrers);
}
