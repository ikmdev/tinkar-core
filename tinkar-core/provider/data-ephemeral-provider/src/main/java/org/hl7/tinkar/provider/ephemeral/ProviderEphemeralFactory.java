package org.hl7.tinkar.provider.ephemeral;

import org.hl7.tinkar.common.service.PrimitiveDataService;

public class ProviderEphemeralFactory  {

    private final ProviderEphemeral provider;

    public ProviderEphemeralFactory() {
        this.provider = ProviderEphemeral.provider();
    }

    public static PrimitiveDataService provider() {
        return ProviderEphemeral.provider();
    }
}
