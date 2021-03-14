package org.hl7.tinkar.common.util.id;

import org.hl7.tinkar.common.util.id.impl.PublicId1;

import java.util.UUID;

public class VertexIds {
    public static final VertexId newRandom() {
        return new PublicId1(UUID.randomUUID());
    }

    public static final VertexId of(long msb, long lsb) {
        return new PublicId1(msb, lsb);
    }

    public static final VertexId of(UUID uuid) {
        return new PublicId1(uuid);
    }

}
