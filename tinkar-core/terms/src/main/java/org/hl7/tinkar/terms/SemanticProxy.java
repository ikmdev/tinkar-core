package org.hl7.tinkar.terms;

import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.id.PublicIds;
import org.hl7.tinkar.common.service.PrimitiveData;


import java.util.NoSuchElementException;
import java.util.UUID;

public class SemanticProxy extends EntityProxy implements SemanticFacade {


    private SemanticProxy(String name, UUID... uuids) {
        super(name, uuids);
    }

    private SemanticProxy(int nid) {
        super(nid);
    }

    private SemanticProxy(String name, PublicId publicId) {
        super(name, publicId);
    }

    public static SemanticProxy make(String name, PublicId publicId) {
        return new SemanticProxy(name, publicId);
    }

    public static SemanticProxy make(int nid) {
        return new SemanticProxy(nid);
    }

    public static SemanticProxy make(String name, UUID... uuids) {
        return new SemanticProxy(name, uuids);
    }
}
