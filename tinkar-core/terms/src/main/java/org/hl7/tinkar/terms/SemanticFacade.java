package org.hl7.tinkar.terms;

import org.hl7.tinkar.component.Semantic;

public interface SemanticFacade extends Semantic, EntityFacade  {

    static SemanticFacade make(int nid) {
        return SemanticProxy.make(nid);
    }

    static int toNid(SemanticFacade facade) {
        return facade.nid();
    }

}
