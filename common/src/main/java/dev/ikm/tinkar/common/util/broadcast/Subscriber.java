package dev.ikm.tinkar.common.util.broadcast;

public interface Subscriber<T> {
    void onNext(T item);
}
