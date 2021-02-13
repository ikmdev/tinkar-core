package org.hl7.tinkar.entity;

import org.hl7.tinkar.common.util.id.PublicId;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.entity.internal.Get;

public class ConceptProxy implements Concept, ComponentWithNid {
    int nid;

    private ConceptProxy(int nid) {
        this.nid = nid;
    }
    @Override
    public PublicId publicId() {
        return Get.entityService().getEntityFast(nid).publicId();
    }

    @Override
    public int nid() {
        return nid;
    }

    public static ConceptProxy make(int nid) {
        return new ConceptProxy(nid);
    }

}
