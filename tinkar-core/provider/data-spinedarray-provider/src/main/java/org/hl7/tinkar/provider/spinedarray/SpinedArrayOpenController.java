package org.hl7.tinkar.provider.spinedarray;

import com.google.auto.service.AutoService;
import org.hl7.tinkar.common.service.*;

import java.io.UncheckedIOException;
import java.net.MalformedURLException;

@AutoService(DataServiceController.class)
public class SpinedArrayOpenController extends SpinedArrayController {
    public static String CONTROLLER_NAME = "Open SpinedArrayStore";
    @Override
    public boolean isValidDataLocation(String name) {
        return name.equals("uuidNidMap");
    }

    @Override
    public void setDataUriOption(DataUriOption option) {
        try {
            ServiceProperties.get(ServiceKeys.DATA_STORE_ROOT, option.uri().toURL().getFile());
        } catch (MalformedURLException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public String controllerName() {
        return CONTROLLER_NAME;
    }


}
