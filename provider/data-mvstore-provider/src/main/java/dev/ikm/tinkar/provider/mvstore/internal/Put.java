package dev.ikm.tinkar.provider.mvstore.internal;

import dev.ikm.tinkar.component.Chronology;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityRecordFactory;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.provider.mvstore.MVStoreProvider;

public class Put {
    public static MVStoreProvider singleton;

    public static void put(Chronology chronology) {
        Entity entity = EntityRecordFactory.make(chronology);
        if (entity instanceof SemanticEntity semanticEntity) {
            singleton.merge(entity.nid(),
                    semanticEntity.patternNid(),
                    semanticEntity.referencedComponentNid(),
                    entity.getBytes(), entity);
        } else {
            singleton.merge(entity.nid(), Integer.MAX_VALUE, Integer.MAX_VALUE, entity.getBytes(), entity);
        }
    }
}
