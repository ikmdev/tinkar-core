package dev.ikm.tinkar.events.test;

import dev.ikm.tinkar.events.DefaultEvtBus;
import dev.ikm.tinkar.events.Evt;
import dev.ikm.tinkar.events.EvtBusFactory;
import dev.ikm.tinkar.events.EvtType;
import dev.ikm.tinkar.events.Subscriber;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.ref.WeakReference;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class EvtBusTest {

    private static final Logger LOG = LoggerFactory.getLogger(EvtBusTest.class);

    @Test
    public void testPubSub() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);

        Subscriber<MyEvent> subscriber = evt -> {
            LOG.info("Event received " + evt.getEventType());
            latch.countDown();
        };

        EvtBusFactory.getDefaultEvtBus().subscribe("topic", MyEvent.class, subscriber);
        EvtBusFactory.getDefaultEvtBus().publish("topic", new MyEvent(this, Evt.ANY));

        latch.await(5, TimeUnit.SECONDS);
        assertEquals(0, latch.getCount());
    }

    @Test
    public void unsubscribingASubscriberRemovesItFromEveryTopic() {
        DefaultEvtBus bus = new DefaultEvtBus();
        AtomicInteger received = new AtomicInteger();
        Subscriber<MyEvent> subscriber = evt -> received.incrementAndGet();
        bus.subscribe("a", MyEvent.class, subscriber);
        bus.subscribe("b", MyEvent.class, subscriber);

        bus.unsubscribe(subscriber);
        bus.publish("a", new MyEvent(this, Evt.ANY));
        bus.publish("b", new MyEvent(this, Evt.ANY));

        assertEquals(0, received.get(), "an unsubscribed subscriber still received events");
    }

    @Test
    public void unsubscribingATopicRemovesItsSubscribersOnly() {
        DefaultEvtBus bus = new DefaultEvtBus();
        AtomicInteger onWindow = new AtomicInteger();
        AtomicInteger onOther = new AtomicInteger();
        bus.subscribe("window", MyEvent.class, evt -> onWindow.incrementAndGet());
        bus.subscribe("window", OtherEvent.class, evt -> onWindow.incrementAndGet());
        bus.subscribe("other", MyEvent.class, evt -> onOther.incrementAndGet());

        bus.unsubscribeAll("window");
        bus.publish("window", new MyEvent(this, Evt.ANY));
        bus.publish("window", new OtherEvent(this, Evt.ANY));
        bus.publish("other", new MyEvent(this, Evt.ANY));

        assertEquals(0, onWindow.get(), "a subscriber of the dropped topic still received events");
        assertEquals(1, onOther.get(), "dropping one topic removed another topic's subscriber");
    }

    @Test
    public void publishingToATopicNobodyHearsKeepsNothing() {
        DefaultEvtBus bus = new DefaultEvtBus();
        WeakReference<Object> topic = new WeakReference<>(publishOnce(bus));
        for (int i = 0; i < 20 && topic.get() != null; i++) {
            System.gc();
        }
        assertNull(topic.get(), "the bus keeps every topic ever published to");
    }

    private Object publishOnce(DefaultEvtBus bus) {
        Object topic = new Object();
        bus.publish(topic, new MyEvent(this, Evt.ANY));
        return topic;
    }

    static class OtherEvent extends Evt {
        OtherEvent(Object source, EvtType<?> eventType) {
            super(source, eventType);
        }
    }

    static class MyEvent extends Evt {
        MyEvent(Object source, EvtType<?> eventType) {
            super(source, eventType);
        }
    }

}
