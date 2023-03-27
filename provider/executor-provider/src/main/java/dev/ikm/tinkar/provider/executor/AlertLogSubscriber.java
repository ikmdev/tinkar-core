package dev.ikm.tinkar.provider.executor;

import com.google.auto.service.AutoService;
import dev.ikm.tinkar.common.id.PublicIdStringKey;
import dev.ikm.tinkar.common.util.broadcast.Broadcaster;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
