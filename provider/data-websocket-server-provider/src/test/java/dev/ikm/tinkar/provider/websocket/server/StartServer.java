package dev.ikm.tinkar.provider.websocket.server;

import dev.ikm.tinkar.entity.load.LoadEntitiesFromDtoFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class StartServer {
    private static final Logger LOG = LoggerFactory.getLogger(StartServer.class);

    public static void main(String[] args) {
        try {
            File file = new File("/Users/kec/Solor/tinkar-export.zip");
            LoadEntitiesFromDtoFile loadTink = new LoadEntitiesFromDtoFile(file);
            int count = loadTink.call();
            LOG.info("Loaded. " + loadTink.report());
            DataProviderWebsocketServer server = new DataProviderWebsocketServer();
            server.launch(args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
