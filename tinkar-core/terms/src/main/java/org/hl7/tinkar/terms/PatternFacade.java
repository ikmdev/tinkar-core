package org.hl7.tinkar.terms;

public interface PatternFacade extends org.hl7.tinkar.component.Pattern, EntityFacade {

    static PatternFacade make(int nid) {
        return EntityProxy.Pattern.make(nid);
    }

    static int toNid(PatternFacade facade) {
        return facade.nid();
    }

}
