package org.hl7.tinkar.terms;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.UUID;

class ProxyFactoryTest {

    @Test
    void create() {
        EntityProxy ep = EntityProxy.make("\"<te&t>", new UUID[] {UUID.randomUUID()});
        String xml = ep.toXmlFragment();
        EntityFacade ep2 = ProxyFactory.fromXmlFragment(xml);
        Assertions.assertEquals(ep, ep2);
    }
}