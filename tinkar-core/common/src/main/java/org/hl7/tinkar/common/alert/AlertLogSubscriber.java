package org.hl7.tinkar.common.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.Callable;
import java.util.concurrent.Flow;

public class AlertLogSubscriber implements Flow.Subscriber<AlertObject> {
    private static final Logger LOG = LoggerFactory.getLogger(AlertLogSubscriber.class);

    Flow.Subscription subscription;

    @Override
    public void onSubscribe(Flow.Subscription subscription) {
        this.subscription = subscription;
        this.subscription.request(1);

    }

    @Override
    public void onNext(AlertObject item) {
        LOG.info(item.toString());

        this.subscription.request(1);
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
