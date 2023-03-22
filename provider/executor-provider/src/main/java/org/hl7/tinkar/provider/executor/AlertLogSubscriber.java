package org.hl7.tinkar.provider.executor;

import com.google.auto.service.AutoService;
import org.hl7.tinkar.common.alert.*;
import org.hl7.tinkar.common.id.PublicIdStringKey;
import org.hl7.tinkar.common.util.broadcast.Broadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.Flow;

@AutoService(AlertReportingService.class)
public class AlertLogSubscriber implements AlertReportingService {
    private static final Logger LOG = LoggerFactory.getLogger(AlertLogSubscriber.class);

    public AlertLogSubscriber() {
        this(AlertStreams.ROOT_ALERT_STREAM_KEY);
    }

    public AlertLogSubscriber(PublicIdStringKey<Broadcaster<AlertObject>> alertStreamKey) {
        LOG.info("Constructing AlertLogSubscriber");
        AlertStreams.get(alertStreamKey).addSubscriberWithWeakReference(this);
    }

    @Override
    public void onNext(AlertObject item) {
        LOG.info("AlertLogSubscriber: \n" + item.toString());
    }
}
