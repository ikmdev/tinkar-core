package org.hl7.tinkar.entity;

import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.component.PatternForSemantic;
import org.hl7.tinkar.entity.internal.Get;

public class PatternForSemanticProxy implements PatternForSemantic, ComponentWithNid {
    int nid;

    private PatternForSemanticProxy(int nid) {
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

    public static PatternForSemanticProxy make(int nid) {
        return new PatternForSemanticProxy(nid);
    }

}
