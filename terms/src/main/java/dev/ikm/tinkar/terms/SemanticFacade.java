package dev.ikm.tinkar.terms;

public interface SemanticFacade extends dev.ikm.tinkar.component.Semantic, EntityFacade  {

    static SemanticFacade make(int nid) {
        return EntityProxy.Semantic.make(nid);
    }

    static int toNid(SemanticFacade facade) {
        return facade.nid();
    }

}
