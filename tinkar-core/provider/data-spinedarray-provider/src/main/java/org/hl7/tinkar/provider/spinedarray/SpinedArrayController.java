package org.hl7.tinkar.provider.spinedarray;

import com.google.auto.service.AutoService;
import org.hl7.tinkar.common.service.DataServiceController;
import org.hl7.tinkar.common.service.PrimitiveDataService;
import org.hl7.tinkar.common.service.ServiceProperties;

@AutoService(DataServiceController.class)
public class SpinedArrayController implements DataServiceController<PrimitiveDataService> {
    public static String PROVIDER_NAME = "SpinedArrayStore";

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
}
