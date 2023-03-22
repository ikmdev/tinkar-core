package org.hl7.tinkar.common.service;

import com.google.auto.service.AutoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceProperties {
    private static final Logger LOG = LoggerFactory.getLogger(ServiceProperties.class);
    private static final ConcurrentHashMap<Enum, Object> propertyMap = new ConcurrentHashMap<>();

    static {
        propertyMap.putIfAbsent(ServiceKeys.JVM_UUID, UUID.randomUUID());
        propertyMap.putIfAbsent(ServiceKeys.CACHE_PERIOD_UUID, UUID.randomUUID());
    }

    public ServiceProperties() {
    }

    public static final <T> T get(Enum enumKey, T defaultValue) {
        if (propertyMap.containsKey(enumKey)) {
            return (T) propertyMap.get(enumKey);
        }
        return defaultValue;
    }

    public static final void set(Enum enumKey, Object value) {
        propertyMap.put(enumKey, value);
    }

    public static final String jvmUuid() {
        Optional<UUID> jvmUuidOptional = ServiceProperties.get(ServiceKeys.JVM_UUID);
        StringBuilder infoString = new StringBuilder();
        if (jvmUuidOptional.isPresent()) {
            infoString.append("JVM UUID: " + jvmUuidOptional.get());
        } else {
            infoString.append("JVM UUID: Not initialized");
        }
        Optional<UUID> cachePeriodUuidOptional = ServiceProperties.get(ServiceKeys.CACHE_PERIOD_UUID);
        if (cachePeriodUuidOptional.isPresent()) {
            infoString.append(" Cache period UUID: " + cachePeriodUuidOptional.get());
        } else {
            infoString.append(" Cache period UUID: Not initialized ");
        }
        return infoString.toString();
    }

    public static final <T> Optional<T> get(Enum enumKey) {
        return Optional.ofNullable((T) propertyMap.get(enumKey));
    }


    @AutoService(CachingService.class)
    public static class CacheProvider implements CachingService {

        @Override
        public void reset() {
            UUID currentUuid = (UUID) propertyMap.get(ServiceKeys.JVM_UUID);
            LOG.info("Resetting ServiceProperties for: " + currentUuid);
            propertyMap.clear();
            propertyMap.put(ServiceKeys.JVM_UUID, currentUuid);
            propertyMap.putIfAbsent(ServiceKeys.CACHE_PERIOD_UUID, UUID.randomUUID());
        }
    }

}
