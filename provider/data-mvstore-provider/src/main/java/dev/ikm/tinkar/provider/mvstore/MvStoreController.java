package dev.ikm.tinkar.provider.mvstore;

import dev.ikm.tinkar.common.service.DataServiceController;
import dev.ikm.tinkar.common.service.PrimitiveDataService;

public abstract class MvStoreController implements DataServiceController<PrimitiveDataService> {
    @Override
    public boolean isValidDataLocation(String name) {
        return name.equals("mvstore.dat");
    }

    @Override
    public Class<? extends PrimitiveDataService> serviceClass() {
        return PrimitiveDataService.class;
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

    @Override
    public String toString() {
        return controllerName();
    }
}
