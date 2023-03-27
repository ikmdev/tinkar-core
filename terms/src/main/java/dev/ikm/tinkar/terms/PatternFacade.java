package dev.ikm.tinkar.terms;

public interface PatternFacade extends dev.ikm.tinkar.component.Pattern, EntityFacade {

    static PatternFacade make(int nid) {
        return EntityProxy.Pattern.make(nid);
    }

    static int toNid(PatternFacade facade) {
        return facade.nid();
    }

}
