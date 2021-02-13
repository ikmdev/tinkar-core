package org.hl7.tinkar.common.provider.chronology.ephemeral;

import com.google.auto.service.AutoService;
import org.hl7.tinkar.common.service.PrimitiveDataService;
import lombok.experimental.Delegate;

@AutoService(PrimitiveDataService.class)
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
