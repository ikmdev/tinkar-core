package org.hl7.tinkar.common.service;

import com.google.auto.service.AutoService;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@AutoService(CachingService.class)
public class ServiceProperties implements CachingService {
    private static final ConcurrentHashMap<Enum, Object> propertyMap = new ConcurrentHashMap<>();
    static {
        propertyMap.putIfAbsent(ServiceKeys.JVM_UUID, UUID.randomUUID());
        propertyMap.putIfAbsent(ServiceKeys.CACHE_PERIOD_UUID, UUID.randomUUID());
    }

    public static final <T> T get(Enum enumKey, T defaultValue) {
        if (propertyMap.containsKey(enumKey)) {
            return (T) propertyMap.get(enumKey);
        }
        return defaultValue;
    }

    public static final <T> Optional<T> get(Enum enumKey) {
        return Optional.ofNullable((T) propertyMap.get(enumKey));
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

    @Override
    public void reset() {
        UUID currentUuid = (UUID) propertyMap.get(ServiceKeys.JVM_UUID);
        System.out.println("Resetting ServiceProperties for: " + currentUuid);
        propertyMap.clear();
        propertyMap.put(ServiceKeys.JVM_UUID, currentUuid);
        propertyMap.putIfAbsent(ServiceKeys.CACHE_PERIOD_UUID, UUID.randomUUID());
    }

    public ServiceProperties() {
    }
}
