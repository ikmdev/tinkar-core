package dev.ikm.tinkar.common.util.broadcast;


public interface Broadcaster<T> {

    void dispatch(T item);
    void addSubscriberWithWeakReference(Subscriber<T> subscriber);
    void removeSubscriber(Subscriber<T> subscriber);

}
