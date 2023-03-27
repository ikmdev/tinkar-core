package dev.ikm.tinkar.common.flow;

import dev.ikm.tinkar.common.util.broadcast.Subscriber;

import java.util.function.Consumer;

public class FlowSubscriber<T> implements Subscriber<T> {

    Consumer<T> action;

    public FlowSubscriber(Consumer<T> action) {
        this.action = action;
    }


    @Override
    public void onNext(T next) {
        action.accept(next);
    }
}
