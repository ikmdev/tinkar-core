package org.hl7.tinkar.common.service;

public enum ServiceKeys {
    DATA_STORE_ROOT,
    /** Unique to each invocation of the JVM. Will persist across cache resets. */
    JVM_UUID,
    /** Unique to each cache period. It is reset each time CachingService.reset() is called. */
    CACHE_PERIOD_UUID
}
