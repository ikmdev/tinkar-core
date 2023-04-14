package dev.ikm.tinkar.common.alert;

import dev.ikm.tinkar.common.util.broadcast.Subscriber;

public interface AlertReportingService extends Subscriber<AlertObject> {
    static AlertReportingService provider() {
        return AlertReportingServiceFinder.INSTANCE.get();
    }
}
