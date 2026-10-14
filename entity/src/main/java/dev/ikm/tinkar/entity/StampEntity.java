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
package dev.ikm.tinkar.entity;

import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.time.DateTimeUtil;
import dev.ikm.tinkar.component.Component;
import dev.ikm.tinkar.component.Stamp;
import dev.ikm.tinkar.component.Version;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.StampFacade;
import dev.ikm.tinkar.terms.State;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

import static dev.ikm.tinkar.common.util.time.DateTimeUtil.SEC_FORMATTER;

public interface StampEntity<V extends StampEntityVersion> extends Entity<V>,
        Stamp, Component, Version {

    default State state() {
        return lastVersion().state();
    }

    default long time() {
        if (lastVersion() != null) {
            return lastVersion().time();
        }
        return Long.MIN_VALUE;
    }

    default ConceptFacade author() {
        return EntityHandle.get(authorNid()).expectConcept();
    }

    default ConceptFacade module() {
        return EntityHandle.get(moduleNid()).expectConcept();
    }

    default ConceptFacade path() {
        return EntityHandle.get(pathNid()).expectConcept();
    }

    StampEntity stamp();

    default long pathNid() {
        if (lastVersion() != null) {
            return lastVersion().pathNid();
        }
        return KernelConcept.CANCELED_STATE.nid();
    }

    default long moduleNid() {
        if (lastVersion() != null) {
            return lastVersion().moduleNid();
        }
        return KernelConcept.CANCELED_STATE.nid();
    }

    default long authorNid() {
        if (lastVersion() != null) {
            return lastVersion().authorNid();
        }
        return KernelConcept.CANCELED_STATE.nid();
    }

    /**
     * The version that says what the stamp is: its committed version when it has one, else its
     * canceled version, else the uncommitted placeholder. A stamp has at most two versions and
     * never a committed one beside a canceled one; should a record from before that rule hold
     * both, the committed version wins (content may sit under it) and the earliest of several
     * commits wins, the same choices the store's merge makes when it heals such a record.
     *
     * @return the stamp's effective version
     */
    default StampEntityVersion lastVersion() {
        if (versions().size() == 1) {
            return versions().get(0);
        }
        StampEntityVersion committed = null;
        StampEntityVersion canceled = null;
        StampEntityVersion uncommitted = null;
        // Stamp times carry the sentinels: Long.MIN_VALUE is canceled and Long.MAX_VALUE is
        // uncommitted, which commit replaces with the commit time.
        for (StampEntityVersion version : versions()) {
            if (version.time() == Long.MIN_VALUE) {
                if (canceled == null) {
                    canceled = version;
                }
            } else if (version.time() == Long.MAX_VALUE) {
                if (uncommitted == null) {
                    uncommitted = version;
                }
            } else if (committed == null || version.time() < committed.time()) {
                committed = version;
            }
        }
        if (committed != null) {
            return committed;
        }
        return canceled != null ? canceled : uncommitted;
    }

    @Override
    ImmutableList<V> versions();

    /**
     * TODO: Consider if STAMP should just be a semantic...
     * @return
     */
    default ImmutableList<Object> fieldValues() {
        return Lists.immutable.of(state(), time(), author(), module(), path());
    }


    @Override
    default boolean canceled() {
        return Entity.super.canceled();
    }

    default long stateNid() {
        if (lastVersion() != null) {
            return lastVersion().stateNid();
        }
        return KernelConcept.CANCELED_STATE.nid();
    }

    default String describe() {
        return "s:" + PrimitiveData.text(stateNid()) +
                " t:" + DateTimeUtil.format(time(), SEC_FORMATTER) +
                " a:" + PrimitiveData.text(authorNid()) +
                " m:" + PrimitiveData.text(moduleNid()) +
                " p:" + PrimitiveData.text(pathNid());
    }
}
