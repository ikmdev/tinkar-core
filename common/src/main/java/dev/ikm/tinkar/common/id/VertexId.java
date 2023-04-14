package dev.ikm.tinkar.common.id;

import java.util.UUID;

public interface VertexId extends PublicId, Comparable<PublicId> {
    long mostSignificantBits();
    long leastSignificantBits();

    default UUID asUuid() {
        return new UUID(mostSignificantBits(), leastSignificantBits());
    }
}
