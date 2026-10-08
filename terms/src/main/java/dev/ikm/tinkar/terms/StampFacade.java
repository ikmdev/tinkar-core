package dev.ikm.tinkar.terms;

public interface StampFacade
        extends dev.ikm.tinkar.component.Stamp, EntityFacade {

    static StampFacade make(long nid) {
        return EntityProxy.Stamp.make(nid);
    }

    static long toNid(StampFacade facade) {
        return facade.nid();
    }

}
