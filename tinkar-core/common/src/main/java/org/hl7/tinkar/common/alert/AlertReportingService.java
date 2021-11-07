package org.hl7.tinkar.common.alert;

import java.util.concurrent.Flow;

public interface AlertReportingService extends Flow.Subscriber<AlertObject> {
    static AlertReportingService provider() {
        return AlertReportingServiceFinder.INSTANCE.get();
    }
}
