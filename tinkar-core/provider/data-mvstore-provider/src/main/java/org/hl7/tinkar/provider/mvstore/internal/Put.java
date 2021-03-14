package org.hl7.tinkar.provider.mvstore.internal;

import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.entity.EntityFactory;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.SemanticEntity;
import org.hl7.tinkar.provider.mvstore.MVStoreProvider;

public class Put {
    public static MVStoreProvider singleton;
    public static void put(Chronology chronology) {
        Entity entity = EntityFactory.make(chronology);
        if (entity instanceof SemanticEntity) {
            SemanticEntity semanticEntity = (SemanticEntity) entity;
            singleton.merge(entity.nid(),
                    entity.definitionNid(),
                    semanticEntity.referencedComponentNid(),
                    entity.getBytes());
        } else {
            singleton.merge(entity.nid(), entity.definitionNid(), Integer.MAX_VALUE, entity.getBytes());
        }
    }
}
