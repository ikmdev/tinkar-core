package dev.ikm.tinkar.provider.spinedarray;

import com.google.auto.service.AutoService;
import dev.ikm.tinkar.common.service.DataServiceController;
import dev.ikm.tinkar.common.service.DataUriOption;
import dev.ikm.tinkar.common.service.ServiceKeys;
import dev.ikm.tinkar.common.service.ServiceProperties;

@AutoService(DataServiceController.class)
public class SpinedArrayOpenController extends SpinedArrayController {
    public static String CONTROLLER_NAME = "Open SpinedArrayStore";

    @Override
    public boolean isValidDataLocation(String name) {
        return name.equals("nidToByteArrayMap");
    }

    @Override
    public void setDataUriOption(DataUriOption option) {
        ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, option.toFile());
    }

    @Override
    public String controllerName() {
        return CONTROLLER_NAME;
    }


}
