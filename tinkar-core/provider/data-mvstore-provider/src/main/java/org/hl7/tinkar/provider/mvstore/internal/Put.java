package org.hl7.tinkar.provider.mvstore.internal;

import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.EntityFactory;
import org.hl7.tinkar.entity.SemanticEntity;
import org.hl7.tinkar.provider.mvstore.MVStoreProvider;

public class Put {
    public static MVStoreProvider singleton;

    public static void put(Chronology chronology) {
        Entity entity = EntityFactory.make(chronology);
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
