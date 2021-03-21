package org.hl7.tinkar.provider.mvstore;

import com.google.auto.service.AutoService;
import org.hl7.tinkar.common.service.DataServiceController;
import org.hl7.tinkar.common.service.PrimitiveDataService;

@AutoService(DataServiceController.class)
public class MVStoreController implements DataServiceController<PrimitiveDataService> {
    public static String PROVIDER_NAME = "MVStore";

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
        if (MVStoreProvider.singleton == null) {
            new MVStoreProvider();
        }
    }

    @Override
    public void stop() {
        MVStoreProvider.singleton.close();
        MVStoreProvider.singleton = null;
    }

    @Override
    public void save() {
        MVStoreProvider.singleton.save();
    }

    @Override
    public boolean running() {
        if (MVStoreProvider.singleton != null) {
            return true;
        }
        return false;
    }

    @Override
    public void reload() {
        throw new UnsupportedOperationException();
    }

    @Override
    public PrimitiveDataService provider() {
        if (MVStoreProvider.singleton == null) {
            start();
        }
        return MVStoreProvider.singleton;
    }
}
