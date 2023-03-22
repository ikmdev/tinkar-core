package org.hl7.tinkar.provider.spinedarray.internal;

import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.EntityRecordFactory;
import org.hl7.tinkar.entity.SemanticEntity;
import org.hl7.tinkar.provider.spinedarray.SpinedArrayProvider;

public class Put {
    public static SpinedArrayProvider singleton;

    public static void put(Chronology chronology) {
        Entity entity = EntityRecordFactory.make(chronology);
        if (entity instanceof SemanticEntity semanticEntity) {
            singleton.merge(entity.nid(),
                    semanticEntity.patternNid(),
                    semanticEntity.referencedComponentNid(),
                    entity.getBytes(), semanticEntity);
        } else {
            singleton.merge(entity.nid(), Integer.MAX_VALUE, Integer.MAX_VALUE, entity.getBytes(), entity);
        }
    }
}
