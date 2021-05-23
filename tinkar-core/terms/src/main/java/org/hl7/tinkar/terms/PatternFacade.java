package org.hl7.tinkar.terms;

import org.hl7.tinkar.component.Pattern;

public interface PatternFacade extends Pattern, EntityFacade {

    static PatternFacade make(int nid) {
        return PatternProxy.make(nid);
    }

    static int toNid(PatternFacade facade) {
        return facade.nid();
    }

}
