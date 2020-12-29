package org.hl7.tinkar.provider.chronology.persistent;

import org.hl7.tinkar.entity.LoadEntitiesFromDTO;
import org.hl7.tinkar.provider.chronology.openhft.ComponentProviderOpenHFT;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class ChronologyProviderPersistentTest {

    @Test
    @Disabled
    // TODO revisit when Chronology Map is ready for JPMS...
    // It is supposed to perform better than MapDb per some benchmarks.
    public void loadChronologies() {
        File file = new File("/Users/kec/Solor/tinkar-export.zip");
        ComponentProviderOpenHFT provider = new ComponentProviderOpenHFT();

        LoadEntitiesFromDTO loadTink = new LoadEntitiesFromDTO(file);
        try {
            int count = loadTink.call();
            System.out.println("Loaded " + count);
        } catch (Exception e) {
            fail(e);
        }
        provider.close();
    }

}