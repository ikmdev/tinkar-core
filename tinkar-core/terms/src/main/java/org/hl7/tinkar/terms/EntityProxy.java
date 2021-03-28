package org.hl7.tinkar.entity;

import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.component.Component;
import org.hl7.tinkar.entity.internal.Get;

import java.util.NoSuchElementException;
import java.util.UUID;

public class EntityProxy implements EntityFacade {
    /**
     * Universal identifiers for the concept proxied by the this object.
     */
    private UUID[] uuids;

    private int cachedNid = 0;


    private EntityProxy(int nid) {
        this.cachedNid = nid;
    }
    @Override
    public PublicId publicId() {
        return Get.entityService().getEntityFast(nid()).publicId();
    }

    public static EntityProxy make(int nid) {
        return new EntityProxy(nid);
    }

    @Override
    public int nid() {
        if (cachedNid == 0) {
            try {
                cachedNid = Get.entityService().nidForPublicId(uuids);
            } catch (NoSuchElementException e) {
                //This it to help me bootstrap the system...
                throw new NoSuchElementException();
            }
        }
        return cachedNid;
    }
}
