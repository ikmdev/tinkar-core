package org.hl7.tinkar.coordinate.internal;

import org.hl7.tinkar.common.service.ServiceGetter;
import org.hl7.tinkar.coordinate.PathService;

import java.util.ServiceLoader;

public enum PathServiceGetter {
    INSTANCE;

    PathService service;

    PathServiceGetter() {
        this.service = ServiceLoader.load(PathService.class).findFirst().get();
    }

    public PathService get() {
        return service;
    }
}
