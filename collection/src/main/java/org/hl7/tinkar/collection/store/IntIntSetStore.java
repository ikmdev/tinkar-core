package org.hl7.tinkar.collection.store;

import java.util.Optional;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.atomic.AtomicReferenceArray;

public interface IntIntSetStore {
    Optional<AtomicReferenceArray<ConcurrentSkipListSet<Integer>>> get(int spineIndex);

    void put(int spineIndex, AtomicReferenceArray<ConcurrentSkipListSet<Integer>> intSet);

    int sizeOnDisk();

    int getSpineCount();

    void writeSpineCount(int spineCount);
}
