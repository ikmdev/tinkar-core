package dev.ikm.tinkar.entity;

import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.State;

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
