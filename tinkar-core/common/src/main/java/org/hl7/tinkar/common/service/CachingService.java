package org.hl7.tinkar.common.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ServiceLoader;

/**
 * A service that may cache configuration, objects, or other info that
 * may need to be reset at runtime.
 */
public interface CachingService {
    Logger LOG = LoggerFactory.getLogger(CachingService.class);

    static void clearAll() {
        ServiceLoader<CachingService> serviceLoader = ServiceLoader.load(CachingService.class);
        serviceLoader.forEach(cachingService -> {
            LOG.info("Resetting cache: " + cachingService.getClass().getName());
            cachingService.reset();
        });
    }

    void reset();
}
