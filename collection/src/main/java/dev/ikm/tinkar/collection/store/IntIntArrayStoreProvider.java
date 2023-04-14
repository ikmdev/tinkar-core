package dev.ikm.tinkar.collection.store;

public interface IntIntArrayStoreProvider {
    IntIntArrayStore get(String storeName);
    IntIntArrayStore get(int patternNid);
}
