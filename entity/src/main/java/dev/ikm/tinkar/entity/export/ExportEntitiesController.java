package dev.ikm.tinkar.entity.export;

import com.google.auto.service.AutoService;
import dev.ikm.tinkar.common.service.TinkExecutor;
import java.io.File;
import java.util.concurrent.Future;

import static dev.ikm.tinkar.entity.Entity.LOG;

@AutoService(ExportEntitiesController.class)
public class ExportEntitiesController {
    public Future<?> export(File pbFile) {
        if (pbFile.getName().toLowerCase().contains("tinkar-export")) {
            return TinkExecutor.ioThreadPool().submit(new ExportEntitiesToProtobufFile(pbFile));
        }
        else
            LOG.info("File type is not of Protobuf type. Running the export but check file.");
            throw new UnsupportedOperationException("Invalid export file type");
    }
}
