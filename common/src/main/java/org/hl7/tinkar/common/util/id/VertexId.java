package org.hl7.tinkar.common.util.id;

import java.util.UUID;

public interface VertexId extends Comparable<VertexId> {
    long mostSignificantBits();
    long leastSignificantBits();

    default UUID asUuid() {
        return new UUID(mostSignificantBits(), leastSignificantBits());
    }

    @Override
    default int compareTo(VertexId o) {
        int compare = Long.compare(mostSignificantBits(), o.mostSignificantBits());
        if (compare != 0) {
            return compare;
        }
        return Long.compare(leastSignificantBits(), o.leastSignificantBits());
    }
}
