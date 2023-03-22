package org.hl7.tinkar.terms;


public interface ConceptFacade extends EntityFacade, org.hl7.tinkar.component.Concept {
    static ConceptFacade make(int nid) {
        return EntityProxy.Concept.make(nid);
    }

    static int toNid(ConceptFacade facade) {
        return facade.nid();
    }

}
