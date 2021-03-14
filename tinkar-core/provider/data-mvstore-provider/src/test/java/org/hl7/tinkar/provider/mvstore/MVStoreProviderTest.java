package org.hl7.tinkar.provider.mvstore;

import org.hl7.tinkar.entity.util.EntityCounter;
import org.hl7.tinkar.entity.util.EntityProcessor;
import org.hl7.tinkar.entity.util.EntityRealizer;
import org.hl7.tinkar.common.util.time.Stopwatch;
import org.hl7.tinkar.entity.LoadEntitiesFromDTO;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.fail;

public class MVStoreProviderTest {

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
        if (MVStoreProvider.singleton == null) {
            System.out.println("Reloading MVStoreProvider");
            Stopwatch reloadStopwatch = new Stopwatch();
            MVStoreProvider provider = new MVStoreProvider();
            System.out.println("Reloading in: " + reloadStopwatch.elapsedTime()+ "\n");
        }
        Assertions.assertNotNull(MVStoreProvider.singleton);
        EntityProcessor processor = new EntityCounter();
        MVStoreProvider.singleton.forEach(processor);
        System.out.println("MVS Sequential count: \n" + processor.report() + "\n");
        processor = new EntityCounter();
        MVStoreProvider.singleton.forEachParallel(processor);
        System.out.println("MVS Parallel count: \n" + processor.report()+ "\n");
        processor = new EntityRealizer();
        MVStoreProvider.singleton.forEach(processor);
        System.out.println("MVS Sequential realization: \n" + processor.report() + "\n");
        processor = new EntityRealizer();
        MVStoreProvider.singleton.forEachParallel(processor);
        System.out.println("MVS Parallel realization: \n" + processor.report()+ "\n");
        processor = new EntityRealizer();
        MVStoreProvider.singleton.forEach(processor);
        System.out.println("MVS Sequential realization: \n" + processor.report() + "\n");
        processor = new EntityRealizer();
        MVStoreProvider.singleton.forEachParallel(processor);
        System.out.println("MVS Parallel realization: \n" + processor.report()+ "\n");
    }

    @Test
    public void openAndClose() {
        if (MVStoreProvider.singleton != null) {
            Stopwatch closingStopwatch = new Stopwatch();
            MVStoreProvider.singleton.close();
            closingStopwatch.end();
            MVStoreProvider.singleton = null;
            System.out.println("MVS Closed in: " + closingStopwatch.elapsedTime()+ "\n");
        }
        System.out.println("Reloading MVStoreProvider");
        Stopwatch reloadStopwatch = new Stopwatch();
        MVStoreProvider provider = new MVStoreProvider();
        System.out.println("MVS Reloading in: " + reloadStopwatch.elapsedTime()+ "\n");
        Assertions.assertNotNull(MVStoreProvider.singleton);
        EntityProcessor processor = new EntityCounter();
        MVStoreProvider.singleton.forEach(processor);
        System.out.println("MVS Sequential count: \n" + processor.report() + "\n");
        processor = new EntityCounter();
        MVStoreProvider.singleton.forEachParallel(processor);
        System.out.println("MVS Parallel count: \n" + processor.report()+ "\n");
        processor = new EntityRealizer();
        MVStoreProvider.singleton.forEach(processor);
        System.out.println("MVS Sequential realization: \n" + processor.report() + "\n");
        processor = new EntityRealizer();
        MVStoreProvider.singleton.forEachParallel(processor);
        System.out.println("MVS Parallel realization: \n" + processor.report()+ "\n");
        processor = new EntityRealizer();
        MVStoreProvider.singleton.forEach(processor);
        System.out.println("MVS Sequential realization: \n" + processor.report() + "\n");
        processor = new EntityRealizer();
        MVStoreProvider.singleton.forEachParallel(processor);
        System.out.println("MVS Parallel realization: \n" + processor.report()+ "\n");
    }

}