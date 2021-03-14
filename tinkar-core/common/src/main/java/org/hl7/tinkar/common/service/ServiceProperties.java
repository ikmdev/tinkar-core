package org.hl7.tinkar.common.service;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class ServiceProperties {
    private static final ConcurrentHashMap<Enum, Object> propertyMap = new ConcurrentHashMap<>();

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
}
