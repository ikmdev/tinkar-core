package org.hl7.tinkar.common.provider.chronology.mapdb;

import org.hl7.tinkar.entity.LoadEntitiesFromDTO;
import org.hl7.tinkar.common.provider.entity.EntityCounter;
import org.hl7.tinkar.common.provider.entity.EntityProcessor;
import org.hl7.tinkar.common.provider.entity.EntityRealizer;
import org.hl7.tinkar.common.util.time.Stopwatch;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.*;

public class MapDbProviderTest {

    @Test
    public void loadChronologies() {
        File file = new File("/Users/kec/Solor/tinkar-export.zip");

        LoadEntitiesFromDTO loadTink = new LoadEntitiesFromDTO(file);
        try {
            int count = loadTink.call();
            System.out.println("Loaded. " + loadTink.report());
        } catch (Exception e) {
            fail(e);
        }
     }
    @Test
    public void count() {
        if (MapDbProvider.singleton == null) {
            System.out.println("Reloading MapDbProvider");
            Stopwatch reloadStopwatch = new Stopwatch();
            MapDbProvider provider = new MapDbProvider();
            System.out.println("MDB Reloading in: " + reloadStopwatch.elapsedTime()+ "\n");
        }
        Assertions.assertNotNull(MapDbProvider.singleton);
        EntityProcessor processor = new EntityCounter();
        MapDbProvider.singleton.forEach(processor);
        System.out.println("MDB Sequential count: \n" + processor.report() + "\n");
        processor = new EntityCounter();
        MapDbProvider.singleton.forEachParallel(processor);
        System.out.println("MDB Parallel count: \n" + processor.report()+ "\n");
        processor = new EntityRealizer();
        MapDbProvider.singleton.forEach(processor);
        System.out.println("MDB Sequential realization: \n" + processor.report() + "\n");
        processor = new EntityRealizer();
        MapDbProvider.singleton.forEachParallel(processor);
        System.out.println("MDB Parallel realization: \n" + processor.report()+ "\n");
        processor = new EntityRealizer();
        MapDbProvider.singleton.forEach(processor);
        System.out.println("MDB Sequential realization: \n" + processor.report() + "\n");
        processor = new EntityRealizer();
        MapDbProvider.singleton.forEachParallel(processor);
        System.out.println("MDB Parallel realization: \n" + processor.report()+ "\n");
    }

    @Test
    public void openAndClose() {
        if (MapDbProvider.singleton != null) {
            Stopwatch closingStopwatch = new Stopwatch();
            MapDbProvider.singleton.close();
            closingStopwatch.end();
            MapDbProvider.singleton = null;
            System.out.println("MDB Closed in: " + closingStopwatch.elapsedTime()+ "\n");
        }
        System.out.println("Reloading MapDbProvider");
        Stopwatch reloadStopwatch = new Stopwatch();
        MapDbProvider provider = new MapDbProvider();
        System.out.println("MDB Reloading in: " + reloadStopwatch.elapsedTime()+ "\n");
        Assertions.assertNotNull(MapDbProvider.singleton);
        EntityProcessor processor = new EntityCounter();
        MapDbProvider.singleton.forEach(processor);
        System.out.println("MDB Sequential count: \n" + processor.report() + "\n");
        processor = new EntityCounter();
        MapDbProvider.singleton.forEachParallel(processor);
        System.out.println("MDB Parallel count: \n" + processor.report()+ "\n");
        processor = new EntityRealizer();
        MapDbProvider.singleton.forEach(processor);
        System.out.println("MDB Sequential realization: \n" + processor.report() + "\n");
        processor = new EntityRealizer();
        MapDbProvider.singleton.forEachParallel(processor);
        System.out.println("MDB Parallel realization: \n" + processor.report()+ "\n");
        processor = new EntityRealizer();
        MapDbProvider.singleton.forEach(processor);
        System.out.println("MDB Sequential realization: \n" + processor.report() + "\n");
        processor = new EntityRealizer();
        MapDbProvider.singleton.forEachParallel(processor);
        System.out.println("MDB Parallel realization: \n" + processor.report()+ "\n");
    }

}