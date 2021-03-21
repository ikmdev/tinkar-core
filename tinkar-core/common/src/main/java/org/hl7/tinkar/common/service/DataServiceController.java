package org.hl7.tinkar.common.service;

import java.util.concurrent.ConcurrentHashMap;

public interface DataServiceController<T> {

    enum ControllerProperty {
        /** A String */
        NAME,
        /** A boolean. True if data is already loaded. */
        DATA_LOADED
    }

    Object property(ControllerProperty key);
    Class<? extends T> serviceClass();
    boolean running();
    void start();
    void stop();
    void save();
    void reload();
    T provider();

}
