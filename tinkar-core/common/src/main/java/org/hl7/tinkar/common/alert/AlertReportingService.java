package org.hl7.tinkar.common.alert;

import org.hl7.tinkar.common.util.broadcast.Subscriber;

public interface AlertReportingService extends Subscriber<AlertObject> {
    static AlertReportingService provider() {
        return AlertReportingServiceFinder.INSTANCE.get();
    }
}
