package org.hl7.tinkar.terms;

import org.hl7.tinkar.common.id.PublicId;

import java.util.UUID;

public class PatternProxy extends EntityProxy implements PatternFacade {

    private PatternProxy(int nid) {
        super(nid);
    }

    private PatternProxy(String name, UUID... uuids) {
        super(name, uuids);
    }

    private PatternProxy(String name, PublicId publicId) {
        super(name, publicId);
    }

    public static PatternProxy make(String name, PublicId publicId) {
        return new PatternProxy(name, publicId);
    }

    public static PatternProxy make(int nid) {
        return new PatternProxy(nid);
    }
    public static PatternProxy make(String name, UUID... uuids) {
        return new PatternProxy(name, uuids);
    }

}
