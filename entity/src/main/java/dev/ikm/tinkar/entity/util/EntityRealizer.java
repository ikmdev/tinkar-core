package dev.ikm.tinkar.entity.util;

import dev.ikm.tinkar.component.FieldDataType;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityRecordFactory;

public class EntityRealizer extends EntityProcessor {
    @Override
    public void processBytesForType(FieldDataType componentType, byte[] bytes) {
        Entity entity = EntityRecordFactory.make(bytes);
    }
}
