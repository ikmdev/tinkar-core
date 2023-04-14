package dev.ikm.tinkar.provider.entity;

import com.google.auto.service.AutoService;
import dev.ikm.tinkar.common.service.DefaultDescriptionForNidService;

@AutoService({DefaultDescriptionForNidService.class})
public class DefaultDescriptionForNidServiceFactory {
    public static DefaultDescriptionForNidService provider() {
        return EntityServiceFactory.provider();
    }
}
