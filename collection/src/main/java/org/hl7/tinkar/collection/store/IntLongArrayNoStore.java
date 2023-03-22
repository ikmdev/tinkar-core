package org.hl7.tinkar.collection.store;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReferenceArray;

public class IntLongArrayNoStore implements IntLongArrayStore{
    @Override
    public Optional<AtomicReferenceArray<long[]>> get(int spineIndex) {
        throw new UnsupportedOperationException("Persistence is not supported");
    }

    @Override
    public void put(int spineIndex, AtomicReferenceArray<long[]> spine) {
        throw new UnsupportedOperationException("Persistence is not supported");
    }

    @Override
    public int sizeOnDisk() {
        throw new UnsupportedOperationException("Persistence is not supported");
    }

    @Override
    public int getSpineCount() {
        throw new UnsupportedOperationException("Persistence is not supported");
    }

    @Override
    public void writeSpineCount(int spineCount) {
        throw new UnsupportedOperationException("Persistence is not supported");
    }
}
