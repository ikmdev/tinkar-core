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
package dev.ikm.tinkar.entity.scope;

import dev.ikm.tinkar.common.service.DataUse;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ScopeErasure;
import dev.ikm.tinkar.common.service.internal.EntityStore;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityRecordFactory;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.PatternEntity;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.boundary.DataKind;
import dev.ikm.tinkar.entity.boundary.EntityBoundaries;
import dev.ikm.tinkar.entity.boundary.ModuleKinds;
import dev.ikm.tinkar.terms.KernelConcept;
import org.eclipse.collections.api.LongIterable;
import org.eclipse.collections.api.factory.primitive.LongLists;
import org.eclipse.collections.api.factory.primitive.LongSets;
import org.eclipse.collections.api.list.primitive.MutableLongList;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * What a knowledge base has that is not knowledge: every concept, pattern and semantic written as
 * shared or local data, and the groups of it that no list shows (IKE-Network/ike-issues#1313; step 3
 * of {@code plan-2026-11-22-remove-export-import-data}). The records of how the knowledge base
 * changed, written under the Change tracking module, are left out: the work in progress they describe
 * is published or withdrawn, not removed.
 *
 * <p>Reading it takes the store's data-use index ({@link DataUse}), or one read of every component
 * where the store keeps none, so it is done off the JavaFX thread.
 */
public final class DataInventory {

    private static final Logger LOG = LoggerFactory.getLogger(DataInventory.class);

    private DataInventory() {
    }

    /**
     * @return the concepts, patterns and semantics of shared and local data, other than the records
     *         of how the knowledge base changed
     */
    public static MutableLongSet components() {
        MutableLongSet found = LongSets.mutable.empty();
        if (!ModuleKinds.parentsPresent()) {
            return found;
        }
        MutableLongSet stamps = LongSets.mutable.empty();
        long changes = PrimitiveData.get().hasPublicId(KernelConcept.CHANGE_TRACKING_MODULE.publicId())
                ? KernelConcept.CHANGE_TRACKING_MODULE.nid() : 0;
        EntityService.get().forEachStampEntity(stamp -> {
            if ((changes == 0 || stamp.moduleNid() != changes)
                    && ModuleKinds.resolve(stamp.moduleNid()).kind() != DataKind.KNOWLEDGE) {
                stamps.add(stamp.nid());
            }
        });
        Optional<DataUse> index = PrimitiveData.get() instanceof ScopeErasure erasure ? erasure.dataUse().filter(DataUse::current)
                : Optional.empty();
        if (index.isPresent()) {
            MutableLongSet users = LongSets.mutable.empty();
            stamps.forEach(stamp -> index.get().forEachWrittenUnder(stamp, users::add));
            // An entry may outlive what it recorded: each is confirmed by reading that one component.
            users.forEach(nid -> {
                if (writtenUnderAnyOf(read(nid), stamps)) {
                    found.add(nid);
                }
            });
        } else if (!stamps.isEmpty()) {
            EntityStore.current().forEachParallel((bytes, nid) -> {
                if (bytes != null && writtenUnderAnyOf(Optional.of(EntityRecordFactory.make(bytes)), stamps)) {
                    synchronized (found) {
                        found.add(nid);
                    }
                }
            });
        }
        return found;
    }

    /**
     * The groups of shared and local data that nothing lists.
     *
     * @param listed the members of every group something does list
     * @return the rest, as the scopes they make, each component in one of them at most
     */
    public static List<Scope> unlisted(LongIterable listed) {
        MutableLongSet rest = components();
        rest.removeAll(listed);
        List<Scope> scopes = new ArrayList<>();
        // A pattern leads to its elements, a concept to what cites it, and a semantic whose own
        // referenced component is elsewhere to what cites it.
        MutableLongList roots = LongLists.mutable.empty();
        MutableLongList concepts = LongLists.mutable.empty();
        MutableLongList semantics = LongLists.mutable.empty();
        MutableLongList inner = LongLists.mutable.empty();
        rest.toSortedList().forEach(nid -> {
            switch (read(nid).orElse(null)) {
                case PatternEntity<?> _ -> roots.add(nid);
                case ConceptEntity<?> _ -> concepts.add(nid);
                case SemanticEntity<?> semantic -> (rest.contains(semantic.referencedComponentNid()) || rest.contains(semantic.patternNid())
                        ? inner : semantics).add(nid);
                case null, default -> { }
            }
        });
        roots.addAll(concepts);
        roots.addAll(semantics);
        roots.addAll(inner);
        roots.forEach(root -> {
            if (!rest.contains(root)) {
                return;                                          // part of a scope already
            }
            try {
                Scope scope = Scope.of(root);
                rest.removeAll(scope.members());
                scopes.add(scope);
            } catch (RuntimeException unresolved) {
                LOG.warn("Could not resolve the group of {}", Scope.describe(root), unresolved);
            }
        });
        return scopes;
    }

    private static boolean writtenUnderAnyOf(Optional<? extends Entity<? extends EntityVersion>> held, MutableLongSet stamps) {
        if (held.isEmpty() || held.get() instanceof StampEntity<?> || EntityBoundaries.kindOf(held.get().nid()) == DataKind.KNOWLEDGE) {
            return false;
        }
        for (EntityVersion version : held.get().versions()) {
            if (stamps.contains(version.stampNid())) {
                return true;
            }
        }
        return false;
    }

    private static Optional<? extends Entity<? extends EntityVersion>> read(long nid) {
        try {
            return EntityHandle.get(nid).entity();
        } catch (RuntimeException absent) {
            return Optional.empty();
        }
    }
}
