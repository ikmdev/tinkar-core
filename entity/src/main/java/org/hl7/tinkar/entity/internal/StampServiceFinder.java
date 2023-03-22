package org.hl7.tinkar.entity.internal;

import org.hl7.tinkar.entity.StampService;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.ServiceLoader;

public enum StampServiceFinder {
    INSTANCE;

    StampService service;

    StampServiceFinder() {
        Class serviceClass = StampService.class;
        ServiceLoader<StampService> serviceLoader = ServiceLoader.load(serviceClass);
        Optional<StampService> optionalService = serviceLoader.findFirst();
        if (optionalService.isPresent()) {
            this.service = optionalService.get();
        } else {
            throw new NoSuchElementException("No " + serviceClass.getName() +
                    " found by ServiceLoader...");
        }
    }

    public StampService get() {
        return service;
    }
}
