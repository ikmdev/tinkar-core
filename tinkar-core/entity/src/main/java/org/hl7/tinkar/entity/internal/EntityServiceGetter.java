package org.hl7.tinkar.entity.internal;

import org.hl7.tinkar.common.service.ServiceGetter;
import org.hl7.tinkar.entity.EntityService;

import java.util.Optional;
import java.util.ServiceLoader;

public enum EntityServiceGetter {
    INSTANCE;

    EntityService service;

    EntityServiceGetter() {
        this.service = ServiceLoader.load(EntityService.class).findFirst().get();
    }

    public EntityService get() {
        return service;
    }

}
