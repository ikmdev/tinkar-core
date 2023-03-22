package org.hl7.tinkar.collection.store;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReferenceArray;

public interface IntLongArrayStore {
    Optional<AtomicReferenceArray<long[]>> get(int spineIndex);

    void put(int spineIndex, AtomicReferenceArray<long[]> spine);

    int sizeOnDisk();

    int getSpineCount();

    void writeSpineCount(int spineCount);
}
