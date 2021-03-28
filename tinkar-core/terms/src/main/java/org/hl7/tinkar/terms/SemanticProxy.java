package org.hl7.tinkar.entity;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.component.Component;
import org.hl7.tinkar.component.SemanticChronology;
import org.hl7.tinkar.component.TypePattern;
import org.hl7.tinkar.entity.internal.Get;
import org.hl7.tinkar.terms.SemanticFacade;

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.UUID;

public class SemanticProxy implements SemanticFacade {
    /**
     * Universal identifiers for the concept proxied by the this object.
     */
    private UUID[] uuids;

    private int cachedNid = 0;

    public SemanticProxy(int conceptNid) {
        this.cachedNid = conceptNid;
    }

    public SemanticProxy(UUID... uuids) {
        this.uuids = uuids;
    }

    @Override
    public PublicId publicId() {
        return Get.entityService().getEntityFast(nid()).publicId();
    }


    @Override
    public int nid() {
        if (cachedNid == 0) {
            try {
                cachedNid = Get.entityService().nidForPublicId(uuids);
            }
            catch (NoSuchElementException e) {
                //This it to help me bootstrap the system... normally, all metadata will be pre-assigned by the IdentifierProvider upon startup.
                throw new NoSuchElementException();
            }
        }
        return cachedNid;
    }

    public static SemanticProxy make(int nid) {
        return new SemanticProxy(nid);
    }

    @Override
    public String toString() {
        SemanticEntity proxiedEntity = Entity.getFast(nid());
        return "SemanticProxy{" +
                " type: " + DefaultDescriptionText.get(proxiedEntity.typePatternNid) +
                " <" +
                nid() +
                "> " + Arrays.toString(proxiedEntity.asUuidArray()) +
                ", rc: " + DefaultDescriptionText.get(proxiedEntity.referencedComponentNid) +
                '}';
    }

}
