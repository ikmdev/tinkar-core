package org.hl7.tinkar.collection.store;

public interface IntIntSetStoreProvider {
    IntIntSetStore get(String storeName);

    IntIntSetStore get(int patternNid);

}
