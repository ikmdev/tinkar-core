package org.hl7.tinkar.entity;

import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.component.Component;
import org.hl7.tinkar.entity.internal.Get;

public class EntityProxy implements Component {
    int nid;
    private EntityProxy() {
    }

    private EntityProxy(int nid) {
        this.nid = nid;
    }
    @Override
    public PublicId publicId() {
        return Get.entityService().getEntityFast(nid).publicId();
    }

    public static EntityProxy make(int nid) {
        return new EntityProxy(nid);
    }
}
