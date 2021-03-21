package org.hl7.tinkar.provider.mvstore;

import org.hl7.tinkar.common.service.DataServiceController;
import org.hl7.tinkar.common.service.PrimitiveData;
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
        PrimitiveData.selectProvider((dataServiceController) -> {
            String name = (String) dataServiceController.property(DataServiceController.ControllerProperty.NAME);
            if (name.equals(MVStoreController.PROVIDER_NAME)) {
                return 1;
            }
            return -1;
        });
        PrimitiveData.start();
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
        if (!PrimitiveData.running()) {
            System.out.println("Reloading MVStoreProvider");
            Stopwatch reloadStopwatch = new Stopwatch();
            PrimitiveData.start();
            System.out.println("Reloading in: " + reloadStopwatch.elapsedTime()+ "\n");
        }
        Assertions.assertNotNull(MVStoreProvider.singleton);
        EntityProcessor processor = new EntityCounter();
        PrimitiveData.get().forEach(processor);
        System.out.println("MVS Sequential count: \n" + processor.report() + "\n");
        processor = new EntityCounter();
        PrimitiveData.get().forEachParallel(processor);
        System.out.println("MVS Parallel count: \n" + processor.report()+ "\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEach(processor);
        System.out.println("MVS Sequential realization: \n" + processor.report() + "\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEachParallel(processor);
        System.out.println("MVS Parallel realization: \n" + processor.report()+ "\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEach(processor);
        System.out.println("MVS Sequential realization: \n" + processor.report() + "\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEachParallel(processor);
        System.out.println("MVS Parallel realization: \n" + processor.report()+ "\n");
    }

    @Test
    public void openAndClose() {
        if (PrimitiveData.running()) {
            Stopwatch closingStopwatch = new Stopwatch();
            PrimitiveData.stop();
            closingStopwatch.end();
            System.out.println("MVS Closed in: " + closingStopwatch.elapsedTime()+ "\n");
        }
        System.out.println("Reloading MVStoreProvider");
        Stopwatch reloadStopwatch = new Stopwatch();
        PrimitiveData.start();
        System.out.println("MVS Reloading in: " + reloadStopwatch.elapsedTime()+ "\n");
        EntityProcessor processor = new EntityCounter();
        PrimitiveData.get().forEach(processor);
        System.out.println("MVS Sequential count: \n" + processor.report() + "\n");
        processor = new EntityCounter();
        PrimitiveData.get().forEachParallel(processor);
        System.out.println("MVS Parallel count: \n" + processor.report()+ "\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEach(processor);
        System.out.println("MVS Sequential realization: \n" + processor.report() + "\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEachParallel(processor);
        System.out.println("MVS Parallel realization: \n" + processor.report()+ "\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEach(processor);
        System.out.println("MVS Sequential realization: \n" + processor.report() + "\n");
        processor = new EntityRealizer();
        PrimitiveData.get().forEachParallel(processor);
        System.out.println("MVS Parallel realization: \n" + processor.report()+ "\n");
        PrimitiveData.get().close();
    }

}