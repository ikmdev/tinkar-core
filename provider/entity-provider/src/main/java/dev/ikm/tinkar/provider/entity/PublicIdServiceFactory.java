package dev.ikm.tinkar.provider.entity;

import com.google.auto.service.AutoService;
import dev.ikm.tinkar.common.service.PublicIdService;

@AutoService({PublicIdService.class})
public class PublicIdServiceFactory {
    public static PublicIdService provider() {
        return EntityServiceFactory.provider();
    }
}