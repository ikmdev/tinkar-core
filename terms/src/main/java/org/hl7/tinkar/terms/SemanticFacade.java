package org.hl7.tinkar.terms;

public interface SemanticFacade extends org.hl7.tinkar.component.Semantic, EntityFacade  {

    static SemanticFacade make(int nid) {
        return EntityProxy.Semantic.make(nid);
    }

    static int toNid(SemanticFacade facade) {
        return facade.nid();
    }

}
