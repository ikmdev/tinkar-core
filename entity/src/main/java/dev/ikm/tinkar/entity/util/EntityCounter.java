package dev.ikm.tinkar.entity.util;

import dev.ikm.tinkar.component.FieldDataType;

public class EntityCounter extends EntityProcessor {
    @Override
    public void processBytesForType(FieldDataType componentType, byte[] bytes) {
        // no need to realize, only want counts...
    }
}
