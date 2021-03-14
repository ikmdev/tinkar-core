package org.hl7.tinkar.common.util.id;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

import java.util.UUID;
import java.util.function.IntConsumer;
import java.util.function.LongConsumer;

public interface PublicId {

    UUID[] asUuidArray();

    default ImmutableList<UUID> asUuidList() {
        return Lists.immutable.of(asUuidArray());
    }

    int uuidCount();

    /**
     * Presents ordered list of longs, from the UUIDs in the order: msb, lsb, msb, lsb, ...
     * @param consumer
     */
    void forEach(LongConsumer consumer);

}
