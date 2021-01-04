package org.hl7.tinkar.provider.chronology.persistent;

import org.hl7.tinkar.entity.LoadEntitiesFromDTO;
import org.hl7.tinkar.provider.chronology.openhft.OpenHFTProvider;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.fail;

public class OpenHFTProviderTest {

    @Test
    @Disabled
    // It is supposed to perform better than MapDb per some benchmarks.
    public void loadChronologies() {
        File file = new File("/Users/kec/Solor/tinkar-export.zip");
        OpenHFTProvider provider = new OpenHFTProvider();

        LoadEntitiesFromDTO loadTink = new LoadEntitiesFromDTO(file);
        try {
            int count = loadTink.call();
            System.out.println("Loaded. " + loadTink.report());
        } catch (Exception e) {
            fail(e);
        }
        provider.close();
    }

}