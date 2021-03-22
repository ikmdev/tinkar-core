package org.hl7.tinkar.entity;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.component.Component;
import org.hl7.tinkar.component.Semantic;
import org.hl7.tinkar.component.SemanticChronology;
import org.hl7.tinkar.component.TypePattern;
import org.hl7.tinkar.entity.internal.Get;

public class SemanticProxy implements SemanticFacade {
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
        return ((SemanticChronology) Get.entityService().getEntityFast(nid)).referencedComponent();
    }

    @Override
    public TypePattern typePattern() {
        return ((SemanticChronology) Get.entityService().getEntityFast(nid)).typePattern();
    }

    @Override
    public ImmutableList versions() {
        return ((SemanticChronology) Get.entityService().getEntityFast(nid)).versions();
    }

    @Override
    public int nid() {
        return nid;
    }

    public static SemanticProxy make(int nid) {
        return new SemanticProxy(nid);
    }

}
