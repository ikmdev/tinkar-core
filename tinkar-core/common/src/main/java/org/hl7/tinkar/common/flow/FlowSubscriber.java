package org.hl7.tinkar.common.flow;

import org.hl7.tinkar.common.alert.AlertObject;
import org.hl7.tinkar.common.alert.AlertStreams;

import java.util.concurrent.Flow;
import java.util.function.Consumer;

public class FlowSubscriber<T> implements Flow.Subscriber<T> {
    Flow.Subscription subscription;
    Consumer<T> action;

    public FlowSubscriber(Consumer<T> action) {
        this.action = action;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        this.subscription.request(1);
    }

    @Override
    public void onNext(T next) {
        this.subscription.request(1);
        action.accept(next);
    }

    @Override
    public void onError(Throwable throwable) {
        AlertStreams.getRoot().dispatch(AlertObject.makeError(throwable));
    }

    @Override
    public void onComplete() {
        // do nothing
    }

    public void cancel() {
        if (this.subscription != null) {
            this.subscription.cancel();
            this.subscription = null;
        }
    }
}
