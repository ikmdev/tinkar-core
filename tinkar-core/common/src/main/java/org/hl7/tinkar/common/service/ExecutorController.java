package org.hl7.tinkar.common.service;

import java.util.ServiceConfigurationError;

public interface ExecutorController {
    ExecutorService create() throws ServiceConfigurationError;
    void stop();
}
