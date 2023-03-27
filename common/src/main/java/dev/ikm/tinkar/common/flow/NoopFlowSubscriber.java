package dev.ikm.tinkar.common.flow;

import dev.ikm.tinkar.common.alert.AlertObject;
import dev.ikm.tinkar.common.alert.AlertStreams;

import java.util.concurrent.Flow;

public class NoopFlowSubscriber implements Flow.Subscriber<Object> {
    Flow.Subscription subscription;

    public Flow.Subscription subscription() {
        return subscription;
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        this.subscription.request(Long.MAX_VALUE);
    }

    @Override
    public void onNext(Object item) {
        // Do nothing with item, but request another...
        this.subscription.request(1);
    }

    @Override
    public void onError(Throwable throwable) {
        AlertStreams.getRoot().dispatch(AlertObject.makeError(throwable));
    }

    @Override
    public void onComplete() {
        // Do nothing
    }
}
