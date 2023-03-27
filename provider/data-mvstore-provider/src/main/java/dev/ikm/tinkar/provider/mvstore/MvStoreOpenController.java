package dev.ikm.tinkar.provider.mvstore;

import com.google.auto.service.AutoService;
import dev.ikm.tinkar.common.service.DataServiceController;
import dev.ikm.tinkar.common.service.DataUriOption;
import dev.ikm.tinkar.common.service.ServiceKeys;
import dev.ikm.tinkar.common.service.ServiceProperties;

import java.io.IOException;

@AutoService(DataServiceController.class)
public class MvStoreOpenController extends MvStoreController {
    public static String CONTROLLER_NAME = "Open MV Store";

    @Override
    public void setDataUriOption(DataUriOption option) {
        ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, option.toFile());
    }

    @Override
    public String controllerName() {
        return CONTROLLER_NAME;
    }

    @Override
    public void start() {
        if (MVStoreProvider.singleton == null) {
            try {
                new MVStoreProvider();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
