package org.hl7.tinkar.provider.mvstore.internal;

import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.entity.EntityFactory;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.provider.mvstore.MVStoreProvider;

public class Put {
    public static MVStoreProvider singleton;
    public static void put(Chronology chronology) {
        Entity entity = EntityFactory.make(chronology);
        singleton.merge(entity.nid(), entity.getBytes());
    }

}
