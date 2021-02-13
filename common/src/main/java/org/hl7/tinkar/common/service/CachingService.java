package org.hl7.tinkar.common.service;

/**
 * A service that may cache configuration, objects, or other info that
 * may need to be reset at runtime.
 */
public interface CachingService {
    void reset();
}
