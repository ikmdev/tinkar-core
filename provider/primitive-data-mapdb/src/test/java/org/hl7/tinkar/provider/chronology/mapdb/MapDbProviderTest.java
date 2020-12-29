package org.hl7.tinkar.provider.chronology.mapdb;

import org.hl7.tinkar.entity.LoadEntitiesFromDTO;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class MapDbProviderTest {

    @Test
    public void loadChronologies() {
        File file = new File("/Users/kec/Solor/tinkar-export.zip");
        //MapDbProvider provider = new MapDbProvider();

        LoadEntitiesFromDTO loadTink = new LoadEntitiesFromDTO(file);
        try {
            int count = loadTink.call();
            System.out.println("Loaded " + count);
        } catch (Exception e) {
            fail(e);
        }
        //provider.close();
    }

}