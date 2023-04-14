package dev.ikm.tinkar.entity.load;

import com.google.auto.service.AutoService;
import dev.ikm.tinkar.common.service.LoadDataFromFileController;
import dev.ikm.tinkar.common.service.TinkExecutor;

import java.io.File;
import java.util.concurrent.Future;

@AutoService(LoadDataFromFileController.class)
public class LoadEntitiesFromFileController implements LoadDataFromFileController {
    @Override
    public Future<?> load(File file) {
        if (!file.getName().toLowerCase().contains("pb"))
            return TinkExecutor.ioThreadPool().submit(new LoadEntitiesFromDtoFile(file));
        else
            return TinkExecutor.ioThreadPool().submit(new LoadEntitiesFromProtobufFile(file));
    }
}
