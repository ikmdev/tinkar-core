package org.hl7.tinkar.terms;

import org.hl7.tinkar.component.Component;
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
}