package org.hl7.tinkar.entity.util;

import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.EntityRecordFactory;

public class EntityRealizer extends EntityProcessor {
    @Override
    public void processBytesForType(FieldDataType componentType, byte[] bytes) {
        Entity entity = EntityRecordFactory.make(bytes);
    }
}
