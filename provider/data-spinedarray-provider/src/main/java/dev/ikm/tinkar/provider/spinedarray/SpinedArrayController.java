package dev.ikm.tinkar.provider.spinedarray;

import dev.ikm.tinkar.common.service.DataServiceController;
import dev.ikm.tinkar.common.service.PrimitiveDataService;

import java.io.IOException;

public abstract class SpinedArrayController implements DataServiceController<PrimitiveDataService> {

    @Override
    public Class<? extends PrimitiveDataService> serviceClass() {
        return PrimitiveDataService.class;
    }

    @Override
    public boolean running() {
        return SpinedArrayProvider.singleton != null;
    }

    @Override
    public void start() {
        try {
            new SpinedArrayProvider();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop() {
        if (SpinedArrayProvider.singleton != null) {
            SpinedArrayProvider.singleton.close();
            SpinedArrayProvider.singleton = null;
        }
    }

    @Override
    public void save() {
        if (SpinedArrayProvider.singleton != null) {
            SpinedArrayProvider.singleton.save();
        }
    }

    @Override
    public void reload() {
        throw new UnsupportedOperationException();
    }

    @Override
    public PrimitiveDataService provider() {
        if (SpinedArrayProvider.singleton == null) {
            start();
        }
        return SpinedArrayProvider.singleton;
    }

    @Override
    public String toString() {
        return controllerName();
    }
}
