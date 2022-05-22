package org.hl7.tinkar.common.util.broadcast;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.concurrent.CopyOnWriteArrayList;

public class SimpleBroadcaster<T> implements Broadcaster<T>, Subscriber<T>{

    private static final Logger LOG = LoggerFactory.getLogger(SimpleBroadcaster.class);
    final CopyOnWriteArrayList<WeakReference<Subscriber<T>>> subscriberWeakReferenceList = new CopyOnWriteArrayList();

    public void dispatch(T item) {
        for (WeakReference<Subscriber<T>> subscriberWeakReference: subscriberWeakReferenceList) {
            try  {
                Subscriber<T> subscriber = subscriberWeakReference.get();
                if (subscriber == null) {
                    subscriberWeakReferenceList.remove(subscriberWeakReference);
                } else {
                    subscriber.onNext(item);
                }
            } catch (Throwable t) {
                LOG.error(t.getMessage(), t);
                subscriberWeakReferenceList.remove(subscriberWeakReference);
            }
        }
    }

    @Override
    public void onNext(T item) {
        this.dispatch(item);
    }

    public void addSubscriberWithWeakReference(Subscriber<T> subscriber) {
        LOG.info(subscriber + " subscribing to " + this);
        for (WeakReference<Subscriber<T>> subscriberWeakReference: subscriberWeakReferenceList) {
            if (subscriberWeakReference.get() == subscriber) {
                throw new IllegalStateException("Trying to add duplicate listener: " + subscriber);
            }
        }
        subscriberWeakReferenceList.add(new WeakReference<>(subscriber));
    }
    public void removeSubscriber(Subscriber<T> subscriber) {
        LOG.info("Removing " + subscriber + " from " + this);
        for (WeakReference<Subscriber<T>> subscriberWeakReference: subscriberWeakReferenceList) {
            if (subscriberWeakReference.get() == subscriber) {
                subscriberWeakReferenceList.remove(subscriberWeakReference);
            }
        }
    }
}
