package org.hl7.tinkar.provider.chronology.openhft.internal;

import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.EntityFactory;
import org.hl7.tinkar.provider.chronology.openhft.OpenHFTProvider;

public class Put {
    public static OpenHFTProvider singleton;
    public static void put(Chronology chronology) {
        Entity entity = EntityFactory.make(chronology);
        singleton.merge(entity.nid(), entity.getBytes());
    }

}
