package org.hl7.tinkar.entity.load;

import com.google.auto.service.AutoService;
import org.hl7.tinkar.common.service.Executor;
import org.hl7.tinkar.common.service.LoadDataFromFileController;

import java.io.File;
import java.util.concurrent.Future;

@AutoService(LoadDataFromFileController.class)
public class LoadEntitiesFromFileController implements LoadDataFromFileController {
    @Override
    public Future<?> load(File file) {
        LoadEntitiesFromDtoFile loader = new LoadEntitiesFromDtoFile(file);
        return Executor.ioThreadPool().submit(loader);
    }
}
