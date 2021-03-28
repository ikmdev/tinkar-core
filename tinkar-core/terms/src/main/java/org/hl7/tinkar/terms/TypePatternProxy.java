package org.hl7.tinkar.terms;

import org.hl7.tinkar.common.id.PublicId;

import java.util.UUID;

public class TypePatternProxy extends EntityProxy implements TypePatternFacade {

    private TypePatternProxy(int nid) {
        super(nid);
    }

    private TypePatternProxy(String name, UUID... uuids) {
        super(name, uuids);
    }

    private TypePatternProxy(String name, PublicId publicId) {
        super(name, publicId);
    }

    public static TypePatternProxy make(String name, PublicId publicId) {
        return new TypePatternProxy(name, publicId);
    }

    public static TypePatternProxy make(int nid) {
        return new TypePatternProxy(nid);
    }
    public static TypePatternProxy make(String name, UUID... uuids) {
        return new TypePatternProxy(name, uuids);
    }

}
