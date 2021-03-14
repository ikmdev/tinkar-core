package org.hl7.tinkar.provider.ephemeral;

import org.hl7.tinkar.common.service.PrimitiveDataService;
import lombok.experimental.Delegate;

public class ProviderEphemeralFactory implements PrimitiveDataService {

    @Delegate(types=PrimitiveDataService.class)
    private final ProviderEphemeral provider;

    public ProviderEphemeralFactory() {
        this.provider = ProviderEphemeral.provider();
    }

    public static PrimitiveDataService provider() {
        return ProviderEphemeral.provider();
    }
}
