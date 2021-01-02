package org.hl7.tinkar.entity;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.component.Component;
import org.hl7.tinkar.entity.internal.Get;

import java.util.UUID;

public class EntityProxy implements Component {
    int nid;
    private EntityProxy() {
    }

    private EntityProxy(int nid) {
        this.nid = nid;
    }
    @Override
    public ImmutableList<UUID> componentUuids() {
        return Get.entityService().getEntityFast(nid).componentUuids();
    }

    public static EntityProxy make(int nid) {
        return new EntityProxy(nid);
    }
}
