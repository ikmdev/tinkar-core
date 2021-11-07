package org.hl7.tinkar.provider.executor;

import com.google.auto.service.AutoService;
import org.hl7.tinkar.common.alert.*;
import org.hl7.tinkar.common.id.PublicIdStringKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.Flow;

@AutoService(AlertReportingService.class)
public class AlertLogSubscriber implements AlertReportingService {
    private static final Logger LOG = LoggerFactory.getLogger(AlertLogSubscriber.class);

    Flow.Subscription subscription;

    public AlertLogSubscriber() {
        this(AlertStreams.ROOT_ALERT_STREAM_KEY);
    }

    public AlertLogSubscriber(PublicIdStringKey<AlertStream> alertStreamKey) {
        LOG.info("Constructing AlertLogSubscriber");
        AlertStreams.get(alertStreamKey).subscribe(this);
    }

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        this.subscription.request(1);
    }

    @Override
    public void onNext(AlertObject item) {
        this.subscription.request(1);
        LOG.info("AlertLogSubscriber: \n" + item.toString());
    }

    @Override
    public void onError(Throwable throwable) {
        // Create a new alert object, and show Alert dialog.
        String alertTitle = "Error in Alert reactive stream.";
        String alertDescription = throwable.getLocalizedMessage();
        AlertType alertType = AlertType.ERROR;

        AlertCategory alertCategory = AlertCategory.ENVIRONMENT;
        Callable<Boolean> resolutionTester = null;
        int[] affectedComponents = new int[0];

        AlertObject alert = new AlertObject(alertTitle,
                alertDescription, alertType, throwable,
                alertCategory, resolutionTester, affectedComponents);
        LOG.error(alert.toString(), throwable);
    }

    @Override
    public void onComplete() {

    }
}
