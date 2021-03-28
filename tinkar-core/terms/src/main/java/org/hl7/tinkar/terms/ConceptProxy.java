package org.hl7.tinkar.terms;

import org.hl7.tinkar.common.id.PublicId;

import java.util.UUID;

public class ConceptProxy extends EntityProxy implements ConceptFacade, PublicId {


    private ConceptProxy(int conceptNid) {
        super(conceptNid);
    }

    private ConceptProxy(String name, UUID... uuids) {
        super(name, uuids);
    }

    private ConceptProxy(String name, PublicId publicId) {
        super(name, publicId);
    }

    public static ConceptProxy make(String name, PublicId publicId) {
        return new ConceptProxy(name, publicId);
    }

    public static ConceptProxy make(int nid) {
        return new ConceptProxy(nid);
    }

    public static ConceptProxy make(String name, UUID... uuids) {
        return new ConceptProxy(name, uuids);
    }


}
