package org.hl7.tinkar.provider.spinedarray.internal;

import org.hl7.tinkar.entity.EntityFactory;
import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.entity.Entity;

public class Put {
    public static OpenHFTProvider singleton;
    public static void put(Chronology chronology) {
        Entity entity = EntityFactory.make(chronology);
        singleton.merge(entity.nid(), entity.getBytes());
    }

}
