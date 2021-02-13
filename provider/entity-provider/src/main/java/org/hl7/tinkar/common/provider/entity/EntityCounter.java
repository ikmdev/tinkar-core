package org.hl7.tinkar.common.provider.entity;

import org.hl7.tinkar.lombok.dto.FieldDataType;

public class EntityCounter extends EntityProcessor {
    @Override
    public void processBytesForType(FieldDataType componentType, byte[] bytes) {
        // no need to realize, only want counts...
    }
}
