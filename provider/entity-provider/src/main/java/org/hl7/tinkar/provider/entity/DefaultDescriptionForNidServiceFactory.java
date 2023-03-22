package org.hl7.tinkar.provider.entity;

import com.google.auto.service.AutoService;
import org.hl7.tinkar.common.service.DefaultDescriptionForNidService;

@AutoService({DefaultDescriptionForNidService.class})
public class DefaultDescriptionForNidServiceFactory {
    public static DefaultDescriptionForNidService provider() {
        return EntityServiceFactory.provider();
    }
}
