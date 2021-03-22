package org.hl7.tinkar.common.service;

import com.google.auto.service.AutoService;

import java.util.List;
import java.util.ServiceLoader;
import java.util.function.ToIntFunction;

@AutoService(CachingService.class)
public class PrimitiveData implements CachingService {

    private static DataServiceController<PrimitiveDataService> controllerSingleton;

    public static Object property(DataServiceController.ControllerProperty key) {
        return controllerSingleton.property(key);
    }

    public static void start() {
        controllerSingleton.start();
    }

    public static void stop() {
        controllerSingleton.stop();
    }

    public static void save() {
        controllerSingleton.save();
    }

    public static void reload() {
        controllerSingleton.reload();
    }

    public static boolean running() {
        if (controllerSingleton == null) {
            return false;
        }
        return controllerSingleton.running();
    }

    public static PrimitiveDataService get() {
        if (controllerSingleton != null) {
            return controllerSingleton.provider();
        }
        throw new IllegalStateException("No provider. Call Select provider prior to get()");
    }

    public static void selectController(ToIntFunction<DataServiceController> scorer) {
        DataServiceController<PrimitiveDataService> topContender = null;
        int topScore = -1;
        int controllerCount = 0;
        ServiceLoader<DataServiceController> loader = ServiceLoader.load(DataServiceController.class);
        for (DataServiceController controller : loader) {
            if (PrimitiveDataService.class.isAssignableFrom(controller.serviceClass())) {
                controllerCount++;
                int score = scorer.applyAsInt(controller);
                if (score > topScore) {
                    topScore = score;
                    topContender = controller;
                }
            }
        }
        if (topScore > -1) {
            setController(topContender);
        } else {
            throw new IllegalStateException("No DataServiceController selected for provider. Tried " + controllerCount);
        }
    }

    public static List<DataServiceController> getControllerOptions() {
        return ServiceLoader.load(DataServiceController.class)
                .stream().map(dataServiceControllerProvider ->  dataServiceControllerProvider.get()).toList();
    }

    public static void selectControllerByName(String name) {
        PrimitiveData.selectController((dataServiceController) -> {
            String controllerName = (String) dataServiceController.property(DataServiceController.ControllerProperty.NAME);
            if (name.equals(controllerName)) {
                return 1;
            }
            return -1;
        });
    }

    public static void setController(DataServiceController controller) {
        controllerSingleton = controller;
    }

    public PrimitiveData() {
    }

    @Override
    public void reset() {
        controllerSingleton = null;
    }
}
