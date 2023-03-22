package org.hl7.tinkar.entity.util;

import org.hl7.tinkar.component.FieldDataType;

public class EntityCounter extends EntityProcessor {
    @Override
    public void processBytesForType(FieldDataType componentType, byte[] bytes) {
        // no need to realize, only want counts...
    }
}
