package org.hl7.tinkar.terms;

import org.hl7.tinkar.component.Concept;


public interface ConceptFacade extends EntityFacade, Concept {
    static ConceptFacade make(int nid) {
        return ConceptProxy.make(nid);
    }

    static int toNid(ConceptFacade facade) {
        return facade.nid();
    }

}
