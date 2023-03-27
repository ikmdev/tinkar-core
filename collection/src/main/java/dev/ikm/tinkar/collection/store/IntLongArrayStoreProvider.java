package dev.ikm.tinkar.collection.store;

public interface IntLongArrayStoreProvider {
    IntLongArrayStore get(String storeName);
    IntLongArrayStore get(int patternNid);
}
