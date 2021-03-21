package org.hl7.tinkar.provider.ephemeral;

import com.google.auto.service.AutoService;
import org.hl7.tinkar.common.service.DataServiceController;
import org.hl7.tinkar.common.service.PrimitiveDataService;

@AutoService(DataServiceController.class)
public class ProviderEphemeralController implements DataServiceController<PrimitiveDataService> {
    public static String PROVIDER_NAME = "EphemeralStore";

    @Override
    public Object property(ControllerProperty key) {
        switch (key) {
            case NAME -> {
                return PROVIDER_NAME;
            }
            case DATA_LOADED -> {
                return true;
            }
        }
        throw new IllegalStateException("No such key: " + key);
    }

    @Override
    public Class<? extends PrimitiveDataService> serviceClass() {
        return PrimitiveDataService.class;
    }

    @Override
    public void start() {
        ProviderEphemeral.provider();
    }

    @Override
    public void stop() {
        ProviderEphemeral.provider().close();
    }

    public boolean running() {
        if (ProviderEphemeral.singleton == null) {
            return false;
        }
        return true;
    }

    @Override
    public void save() {
        // nothing to save.
    }

    @Override
    public void reload() {
        throw new UnsupportedOperationException("Can't reload yet.");
    }

    @Override
    public PrimitiveDataService provider() {
        return ProviderEphemeral.provider();
    }
}
