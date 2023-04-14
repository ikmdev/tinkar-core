package dev.ikm.tinkar.common.service;

import java.util.ServiceConfigurationError;

public interface ExecutorController {
    ExecutorService create() throws ServiceConfigurationError;
    void stop();
}
