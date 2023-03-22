package org.hl7.tinkar.provider.entity;

import com.google.auto.service.AutoService;
import org.hl7.tinkar.common.service.PublicIdService;

@AutoService({PublicIdService.class})
public class PublicIdServiceFactory {
    public static PublicIdService provider() {
        return EntityServiceFactory.provider();
    }
}