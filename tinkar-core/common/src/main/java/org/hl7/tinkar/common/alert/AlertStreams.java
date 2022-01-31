package org.hl7.tinkar.common.alert;

import org.hl7.tinkar.common.flow.NoopFlowSubscriber;
import org.hl7.tinkar.common.id.PublicIdStringKey;
import org.hl7.tinkar.common.id.PublicIds;

import java.util.concurrent.ConcurrentHashMap;

public class AlertStreams {
    public static final PublicIdStringKey<AlertStream> ROOT_ALERT_STREAM_KEY =
            new PublicIdStringKey(PublicIds.of("d2733c61-fef3-4051-bc96-137819a18d0a"), "root alert stream");
    private static ConcurrentHashMap<PublicIdStringKey<AlertStream>, AlertStream> alertStreamMap = new ConcurrentHashMap<>();

    public static void dispatchToRoot(Throwable e) {
        getRoot().dispatch(AlertObject.makeError(e));
    }

    public static AlertStream getRoot() {
        return get(ROOT_ALERT_STREAM_KEY);
    }

    public static AlertStream get(PublicIdStringKey<AlertStream> alertStreamKey) {
        return AlertStreams.alertStreamMap.computeIfAbsent(alertStreamKey, alertStreamPublicIdStringKey -> {
            AlertStream rootAlertStream = new AlertStream();
            // Added to prevent this error if no subscribers: BackPressureFailure: Could not emit item downstream due to lack of requests
            NoopFlowSubscriber noopFlowSubscriber = new NoopFlowSubscriber();
            rootAlertStream.subscribe(noopFlowSubscriber);
            return rootAlertStream;
        });
    }
}
