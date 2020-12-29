package org.hl7.tinkar.entity.internal;

import org.hl7.tinkar.entity.EntityService;
import org.hl7.tinkar.service.CachingService;

import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.ServiceLoader;

//TODO how to initialize service after reset...

public class Get implements CachingService {
    private static Get singleton = new Get();

    private final ServiceLoader<EntityService> serviceLoader;
    private final EntityService entityService;

    private Get() {
        this.serviceLoader = ServiceLoader.load(EntityService.class);
        Optional<EntityService> optionalService = this.serviceLoader.findFirst();
        if (optionalService.isPresent()) {
            this.entityService = this.serviceLoader.findFirst().get();
        } else {
            throw new NoSuchElementException("No EntityService found by ServiceLoader ...");
        }
    }

    @Override
    public void reset() {
        singleton = null;
    }
    public static EntityService entityService() {
        return singleton.entityService;
    }
}
