package org.hl7.tinkar.terms;

import org.hl7.tinkar.component.Component;

import java.util.function.ToIntFunction;

/**
 <entity desc="" uuids=""/>
 */
public interface EntityFacade extends Component, ComponentWithNid {
    default String description() {
        return "No description";
    }

    default String toXmlFragment() {
        return ProxyFactory.toXmlFragment(this);
    }

    static EntityFacade make(int nid) {
        return EntityProxy.make(nid);
    }

    static int toNid(EntityFacade entityFacade) {
        return entityFacade.nid();
    }

    default  <T extends EntityProxy> T toProxy() {
        return ProxyFactory.fromFacade(this);
    }

}