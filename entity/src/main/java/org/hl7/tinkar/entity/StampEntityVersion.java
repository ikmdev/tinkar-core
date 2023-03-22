package org.hl7.tinkar.entity;

import org.hl7.tinkar.terms.ConceptFacade;
import org.hl7.tinkar.terms.State;

public interface StampEntityVersion extends EntityVersion, StampVersion {

    @Override
    default State state() {
        return State.fromConceptNid(stateNid());
    }

    int stateNid();

    long time();

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

    int authorNid();

    int moduleNid();

    int pathNid();
}
