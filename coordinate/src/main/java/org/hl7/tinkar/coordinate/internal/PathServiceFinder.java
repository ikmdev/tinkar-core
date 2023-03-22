package org.hl7.tinkar.coordinate.internal;

import org.hl7.tinkar.coordinate.PathService;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.ServiceLoader;

public enum PathServiceFinder {
    INSTANCE;

    final PathService service;

    PathServiceFinder() {
        Class serviceClass = PathService.class;
        ServiceLoader<PathService> serviceLoader = ServiceLoader.load(serviceClass);
        Optional<PathService> optionalService = serviceLoader.findFirst();
        if (optionalService.isPresent()) {
            this.service = optionalService.get();
        } else {
            throw new NoSuchElementException("No " + serviceClass.getName() +
                    " found by ServiceLoader...");
        }
    }

    public PathService get() {
        return service;
    }
}
