package org.hl7.tinkar.entity;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.util.time.DateTimeUtil;
import org.hl7.tinkar.component.Component;
import org.hl7.tinkar.component.Stamp;
import org.hl7.tinkar.component.Version;
import org.hl7.tinkar.terms.ConceptFacade;
import org.hl7.tinkar.terms.State;

public interface StampEntity<V extends StampEntityVersion> extends Entity<V>,
        Stamp<V>, Component, Version, IdentifierData {
    @Override
    default State state() {
        return lastVersion().state();
    }

    @Override
    default long time() {
        return lastVersion().time();
    }

    @Override
    default ConceptFacade author() {
        return Entity.provider().getEntityFast(authorNid());
    }

    @Override
    default ConceptFacade module() {
        return Entity.provider().getEntityFast(moduleNid());
    }

    @Override
    default ConceptFacade path() {
        return Entity.provider().getEntityFast(pathNid());
    }

    @Override
    Stamp stamp();

    default int pathNid() {
        return lastVersion().pathNid();
    }

    default int moduleNid() {
        return lastVersion().moduleNid();
    }

    default int authorNid() {
        return lastVersion().authorNid();
    }

    default StampEntityVersion lastVersion() {
        if (versions().size() == 1) {
            return versions().get(0);
        }
        StampEntityVersion latest = null;
        for (StampEntityVersion version : versions()) {
            if (version.time() == Long.MIN_VALUE) {
                // if canceled (Long.MIN_VALUE), latest is canceled.
                return version;
            } else if (latest == null || latest.time() < version.time()) {
                latest = version;
            }
        }
        return latest;
    }

    @Override
    ImmutableList<V> versions();

    default int stateNid() {
        return lastVersion().stateNid();
    }

    default String describe() {
        return "s:" + PrimitiveData.text(stateNid()) +
                " t:" + DateTimeUtil.format(time()) +
                " a:" + PrimitiveData.text(authorNid()) +
                " m:" + PrimitiveData.text(moduleNid()) +
                " p:" + PrimitiveData.text(pathNid());
    }
}
