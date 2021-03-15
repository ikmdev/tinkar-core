package org.hl7.tinkar.entity;

import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.component.TypePatternForSemantic;
import org.hl7.tinkar.entity.internal.Get;

public class TypePatternProxy implements TypePatternForSemantic, ComponentWithNid {
    int nid;

    private TypePatternProxy(int nid) {
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

    public static TypePatternForSemanticProxy make(int nid) {
        return new TypePatternProxy(nid);
    }

}
