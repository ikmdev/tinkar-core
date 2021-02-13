package org.hl7.tinkar.common.provider.websocket.client.internal;

import org.hl7.tinkar.entity.EntityService;

import java.util.ServiceLoader;

public class Get {
    private final ServiceLoader<EntityService> serviceLoader;
    private final EntityService entityService;

    public Get() {
        this.serviceLoader = ServiceLoader.load(EntityService.class);
        this.entityService = this.serviceLoader.findFirst().get();
        System.out.println("EntityService: " + this.entityService);
    }

    private static Get singleton = new Get();

    public static EntityService entityService() {
        return singleton.entityService;
    }

}
