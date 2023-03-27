package dev.ikm.tinkar.terms;


public interface ConceptFacade extends EntityFacade, dev.ikm.tinkar.component.Concept {
    static ConceptFacade make(int nid) {
        return EntityProxy.Concept.make(nid);
    }

    static int toNid(ConceptFacade facade) {
        return facade.nid();
    }

}
