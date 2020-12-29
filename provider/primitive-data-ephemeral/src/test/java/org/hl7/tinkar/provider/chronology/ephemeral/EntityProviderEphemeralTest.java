package org.hl7.tinkar.provider.chronology.ephemeral;

import org.hl7.tinkar.entity.LoadEntitiesFromDTO;
import org.hl7.tinkar.service.PrimitiveDataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.fail;

public class EntityProviderEphemeralTest {


    @Test
    public void loadChronologies() {
        File file = new File("/Users/kec/Solor/tinkar-export.zip");
        LoadEntitiesFromDTO loadTink = new LoadEntitiesFromDTO(file);
        try {
            int count = loadTink.call();
            System.out.println("Loaded " + count);
        } catch (Exception e) {
            fail(e);
        }
    }
}