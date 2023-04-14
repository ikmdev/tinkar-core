package dev.ikm.tinkar.provider.spinedarray.internal;

import dev.ikm.tinkar.provider.spinedarray.SpinedArrayProvider;
import dev.ikm.tinkar.component.Chronology;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityRecordFactory;
import dev.ikm.tinkar.entity.SemanticEntity;

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
