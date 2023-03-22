package org.hl7.tinkar.common.id.impl;

import org.hl7.tinkar.common.id.PublicId;

import java.util.UUID;

import static org.hl7.tinkar.common.id.IdCollection.TO_STRING_LIMIT;

public abstract class PublicIdA implements PublicId {
    @Override
    public String toString() {
        return PublicId.idString(asUuidArray());
    }
}
