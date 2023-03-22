package org.hl7.tinkar.common.flow;

import org.hl7.tinkar.common.alert.AlertObject;
import org.hl7.tinkar.common.alert.AlertStreams;
import org.hl7.tinkar.common.util.broadcast.Subscriber;

import java.util.concurrent.Flow;
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
