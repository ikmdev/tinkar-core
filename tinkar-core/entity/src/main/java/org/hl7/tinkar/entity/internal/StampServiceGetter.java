package org.hl7.tinkar.entity.internal;

import org.hl7.tinkar.entity.StampService;

import java.util.ServiceLoader;

public enum StampServiceGetter {
    INSTANCE;

    StampService service;

    StampServiceGetter() {
        this.service = ServiceLoader.load(StampService.class).findFirst().get();
    }

    public StampService get() {
        return service;
    }
}
