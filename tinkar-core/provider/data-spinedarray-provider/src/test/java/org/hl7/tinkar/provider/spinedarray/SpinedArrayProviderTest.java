package org.hl7.tinkar.provider.spinedarray;

import org.hl7.tinkar.common.util.time.Stopwatch;
import org.hl7.tinkar.entity.LoadEntitiesFromDTO;
import org.hl7.tinkar.entity.util.EntityCounter;
import org.hl7.tinkar.entity.util.EntityProcessor;
import org.hl7.tinkar.entity.util.EntityRealizer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.fail;

public class SpinedArrayProviderTest {

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
        if (SpinedArrayProvider.singleton == null) {
            System.out.println("Reloading SpinedArrayProvider");
            Stopwatch reloadStopwatch = new Stopwatch();
            SpinedArrayProvider provider = new SpinedArrayProvider();
            System.out.println("Reloading in: " + reloadStopwatch.elapsedTime()+ "\n");
        }
        Assertions.assertNotNull(SpinedArrayProvider.singleton);
        EntityProcessor processor = new EntityCounter();
        SpinedArrayProvider.singleton.forEach(processor);
        System.out.println("SAP Sequential count: \n" + processor.report() + "\n");
        processor = new EntityCounter();
        SpinedArrayProvider.singleton.forEachParallel(processor);
        System.out.println("SAP Parallel count: \n" + processor.report()+ "\n");
        processor = new EntityRealizer();
        SpinedArrayProvider.singleton.forEach(processor);
        System.out.println("SAP Sequential realization: \n" + processor.report() + "\n");
        processor = new EntityRealizer();
        SpinedArrayProvider.singleton.forEachParallel(processor);
        System.out.println("SAP Parallel realization: \n" + processor.report()+ "\n");
        processor = new EntityRealizer();
        SpinedArrayProvider.singleton.forEach(processor);
        System.out.println("SAP Sequential realization: \n" + processor.report() + "\n");
        processor = new EntityRealizer();
        SpinedArrayProvider.singleton.forEachParallel(processor);
        System.out.println("SAP Parallel realization: \n" + processor.report()+ "\n");
    }

    @Test
    public void openAndClose() {
        if (SpinedArrayProvider.singleton != null) {
            Stopwatch closingStopwatch = new Stopwatch();
            SpinedArrayProvider.singleton.close();
            closingStopwatch.end();
            SpinedArrayProvider.singleton = null;
            System.out.println("MVS Closed in: " + closingStopwatch.elapsedTime()+ "\n");
        }
        System.out.println("Reloading MVStoreProvider");
        Stopwatch reloadStopwatch = new Stopwatch();
        SpinedArrayProvider provider = new SpinedArrayProvider();
        System.out.println("SAP Reloading in: " + reloadStopwatch.elapsedTime()+ "\n");
        Assertions.assertNotNull(SpinedArrayProvider.singleton);
        EntityProcessor processor = new EntityCounter();
        SpinedArrayProvider.singleton.forEach(processor);
        System.out.println("SAP Sequential count: \n" + processor.report() + "\n");
        processor = new EntityCounter();
        SpinedArrayProvider.singleton.forEachParallel(processor);
        System.out.println("SAP Parallel count: \n" + processor.report()+ "\n");
        processor = new EntityRealizer();
        SpinedArrayProvider.singleton.forEach(processor);
        System.out.println("SAP Sequential realization: \n" + processor.report() + "\n");
        processor = new EntityRealizer();
        SpinedArrayProvider.singleton.forEachParallel(processor);
        System.out.println("SAP Parallel realization: \n" + processor.report()+ "\n");
        processor = new EntityRealizer();
        SpinedArrayProvider.singleton.forEach(processor);
        System.out.println("SAP Sequential realization: \n" + processor.report() + "\n");
        processor = new EntityRealizer();
        SpinedArrayProvider.singleton.forEachParallel(processor);
        System.out.println("SAP Parallel realization: \n" + processor.report()+ "\n");
    }
}
