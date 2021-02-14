package org.hl7.tinkar.entity.util;

import org.hl7.tinkar.entity.EntityFactory;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.lombok.dto.FieldDataType;

public class EntityRealizer extends EntityProcessor {
    @Override
    public void processBytesForType(FieldDataType componentType, byte[] bytes) {
        Entity entity = EntityFactory.make(bytes);
    }
}
