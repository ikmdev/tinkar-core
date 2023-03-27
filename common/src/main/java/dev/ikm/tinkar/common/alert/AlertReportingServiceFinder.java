package dev.ikm.tinkar.common.alert;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.ServiceLoader;

public enum AlertReportingServiceFinder {
    INSTANCE;

    final AlertReportingService service;

    AlertReportingServiceFinder() {
        Class serviceClass = AlertReportingService.class;
        ServiceLoader<AlertReportingService> serviceLoader = ServiceLoader.load(serviceClass);
        Optional<AlertReportingService> optionalService = serviceLoader.findFirst();
        if (optionalService.isPresent()) {
            this.service = optionalService.get();

        } else {
            throw new NoSuchElementException("No " + serviceClass.getName() +
                    " found by ServiceLoader...");
        }
    }

    public AlertReportingService get() {
        return service;
    }
}
