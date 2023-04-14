package dev.ikm.tinkar.entity.internal;

import dev.ikm.tinkar.entity.EntityService;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.ServiceLoader;

public enum EntityServiceFinder {
    INSTANCE;

    EntityService service;

    EntityServiceFinder() {
        Class serviceClass = EntityService.class;
        ServiceLoader<EntityService> serviceLoader = ServiceLoader.load(serviceClass);
        Optional<EntityService> optionalService = serviceLoader.findFirst();
        if (optionalService.isPresent()) {
            this.service = optionalService.get();
        } else {
            throw new NoSuchElementException("No " + serviceClass.getName() +
                    " found by ServiceLoader...");
        }
    }

    public EntityService get() {
        return service;
    }

}
