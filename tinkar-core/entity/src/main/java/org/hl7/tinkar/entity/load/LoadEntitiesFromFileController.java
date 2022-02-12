package org.hl7.tinkar.entity.load;

import com.google.auto.service.AutoService;
import org.hl7.tinkar.common.service.TinkExecutor;
import org.hl7.tinkar.common.service.LoadDataFromFileController;

import java.io.File;
import java.util.concurrent.Future;

@AutoService(LoadDataFromFileController.class)
public class LoadEntitiesFromFileController implements LoadDataFromFileController {
    @Override
    public Future<?> load(File file) {
        if (!file.getName().toLowerCase().contains("pb"))
            return TinkExecutor.ioThreadPool().submit(new LoadEntitiesFromDtoFile(file));
        else
            return TinkExecutor.ioThreadPool().submit(new LoadEntitiesFromProtocolBuffersFile(file));
    }
}
