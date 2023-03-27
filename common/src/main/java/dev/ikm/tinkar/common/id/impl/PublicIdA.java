package dev.ikm.tinkar.common.id.impl;

import dev.ikm.tinkar.common.id.PublicId;

public abstract class PublicIdA implements PublicId {
    @Override
    public String toString() {
        return PublicId.idString(asUuidArray());
    }
}
