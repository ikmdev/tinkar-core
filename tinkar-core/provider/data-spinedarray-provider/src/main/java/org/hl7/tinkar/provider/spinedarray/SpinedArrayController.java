package org.hl7.tinkar.provider.spinedarray;

import org.hl7.tinkar.common.service.*;

public abstract class SpinedArrayController implements DataServiceController<PrimitiveDataService> {

    @Override
    public boolean running() {
        return SpinedArrayProvider.singleton != null;
    }

    @Override
    public Class<? extends PrimitiveDataService> serviceClass() {
        return PrimitiveDataService.class;
    }

    @Override
    public void start() {
        new SpinedArrayProvider();
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
        SpinedArrayProvider.singleton.save();
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
