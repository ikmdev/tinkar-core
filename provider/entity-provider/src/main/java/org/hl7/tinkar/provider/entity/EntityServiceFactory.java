package org.hl7.tinkar.provider.entity;

import com.google.auto.service.AutoService;
import org.hl7.tinkar.entity.EntityService;

import java.util.concurrent.atomic.AtomicReference;

@AutoService({EntityService.class})
public class EntityServiceFactory {
    protected static final AtomicReference<EntityProvider> provider = new AtomicReference<>();

    public static EntityProvider provider() {
        return provider.updateAndGet(entityProvider -> {
            if (entityProvider == null) {
                return new EntityProvider();
            }
            return entityProvider;
        });
    }


}
