package org.hl7.tinkar.provider.mvstore;

import com.google.auto.service.AutoService;
import org.hl7.tinkar.common.service.*;

import java.io.UncheckedIOException;
import java.net.MalformedURLException;

@AutoService(DataServiceController.class)
public class MvStoreOpenController extends MvStoreController {
    public static String CONTROLLER_NAME = "Open MV Store";

    @Override
    public String controllerName() {
        return CONTROLLER_NAME;
    }

    @Override
    public void start() {
        if (MVStoreProvider.singleton == null) {
            new MVStoreProvider();
        }
    }

    @Override
    public void setDataUriOption(DataUriOption option) {
        ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, option.toFile());
    }

}
