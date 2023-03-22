package org.hl7.tinkar.common.util.broadcast;

public interface Subscriber<T> {
    void onNext(T item);
}
