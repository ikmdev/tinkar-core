package dev.ikm.tinkar.terms;

import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.component.Component;

/**
 * <entity desc="" uuids=""/>
 */
public interface EntityFacade extends Component, ComponentWithNid {
    static EntityFacade make(int nid) {
        return EntityProxy.make(nid);
    }

    static int toNid(EntityFacade entityFacade) {
        return entityFacade.nid();
    }

    default String description() {
        return PrimitiveData.text(nid());
    }

    default String toXmlFragment() {
        return ProxyFactory.toXmlFragment(this);
    }

    default <T extends EntityProxy> T toProxy() {
        return ProxyFactory.fromFacade(this);
    }

}