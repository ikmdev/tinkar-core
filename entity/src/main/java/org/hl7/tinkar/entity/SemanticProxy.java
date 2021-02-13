package org.hl7.tinkar.entity;

import org.hl7.tinkar.common.util.id.PublicId;
import org.hl7.tinkar.component.Component;
import org.hl7.tinkar.component.PatternForSemantic;
import org.hl7.tinkar.component.Semantic;
import org.hl7.tinkar.entity.internal.Get;

public class SemanticProxy implements Semantic, ComponentWithNid {
    int nid;

    private SemanticProxy(int nid) {
        this.nid = nid;
    }

    @Override
    public PublicId publicId() {
        return Get.entityService().getEntityFast(nid).publicId();
    }

    @Override
    public Component referencedComponent() {
        return ((Semantic) Get.entityService().getEntityFast(nid)).referencedComponent();
    }

    @Override
    public PatternForSemantic patternForSemantic() {
        return ((Semantic) Get.entityService().getEntityFast(nid)).patternForSemantic();
    }

    @Override
    public int nid() {
        return nid;
    }

    public static SemanticProxy make(int nid) {
        return new SemanticProxy(nid);
    }

}
