package dev.ikm.tinkar.entity;

import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.time.DateTimeUtil;
import dev.ikm.tinkar.component.Stamp;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.State;

public interface StampVersion extends Stamp<StampEntityVersion>, VersionData {
    default String describe() {
        return "s:" + PrimitiveData.text(stateNid()) +
                " t:" + DateTimeUtil.format(time()) +
                " a:" + PrimitiveData.text(authorNid()) +
                " m:" + PrimitiveData.text(moduleNid()) +
                " p:" + PrimitiveData.text(pathNid());
    }

    int stateNid();

    int authorNid();

    int moduleNid();

    int pathNid();

    default State state() {
        return State.fromConceptNid(stateNid());
    }

    long time();

    default ConceptFacade author() {
        return EntityProxy.Concept.make(authorNid());
    }

    default ConceptFacade module() {
        return EntityProxy.Concept.make(moduleNid());
    }

    default ConceptFacade path() {
        return EntityProxy.Concept.make(pathNid());
    }

}
