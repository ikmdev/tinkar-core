package org.hl7.tinkar.provider.chronology.openhft.internal;

import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.EntityFactory;
import org.hl7.tinkar.provider.chronology.openhft.ComponentProviderOpenHFT;

public class Put {
    public static ComponentProviderOpenHFT singleton;
    public static void put(Chronology chronology) {
        Entity entity = EntityFactory.make(chronology);
        singleton.merge(entity.nid(), entity.getBytes(), Entity::merge);
    }

}
