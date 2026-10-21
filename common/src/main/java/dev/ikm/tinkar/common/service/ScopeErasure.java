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

/**
 * A store that can erase a scope of instance or ephemeral data (IKE-Network/ike-issues#1313):
 * components a scope operation has already proven closed, that nothing outside them cites.
 *
 * <p>Unlike {@link PrimitiveDataRepair#erase(long)}, a repair tool that scans the whole
 * referencing map once per component, the erasure is targeted: each component's own bytes say
 * which index entries it made (its referenced component's citations, its pattern's element set,
 * its public ids), and only those are removed. A store that cannot erase does not implement this,
 * and a scope delete refuses there.
 */
public interface ScopeErasure {

    /**
     * Erases components from the store, bypassing the change-set journal: their bytes, the
     * citations they made, their pattern membership, their public ids, and their search text.
     * Their nids are not reused.
     *
     * @param nids the components, which nothing outside them may cite
     */
    void eraseComponents(long[] nids);

    /**
     * What removing components from change sets did.
     *
     * @param files     the change sets read
     * @param rewritten those written again without the components' records
     * @param deleted   those deleted, nothing being left in them
     * @param records   the records removed
     */
    record ChangeSetRemoval(int files, int rewritten, int deleted, long records) {

        /** Nothing read, nothing removed. */
        public static final ChangeSetRemoval NONE = new ChangeSetRemoval(0, 0, 0, 0);

        /** @return the two removals together */
        public ChangeSetRemoval plus(ChangeSetRemoval other) {
            return new ChangeSetRemoval(files + other.files, rewritten + other.rewritten, deleted + other.deleted,
                    records + other.records);
        }
    }

    /**
     * Removes erased components' records from the instance and ephemeral change sets this store's
     * change-set writers keep, so the files no longer hold a deleted scope. A store that keeps no
     * change sets, as a memory-only one does not, removes nothing.
     *
     * @param components the UUIDs of the erased components
     * @return what was removed
     */
    default ChangeSetRemoval removeFromChangeSets(java.util.Set<java.util.UUID> components) {
        return ChangeSetRemoval.NONE;
    }
}
