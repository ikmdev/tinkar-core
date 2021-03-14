package org.hl7.tinkar.provider.websocket.server;

import org.hl7.tinkar.entity.LoadEntitiesFromDTO;
import org.hl7.tinkar.provider.websocket.server.DataProviderWebsocketServer;

import java.io.File;

public class StartServer {
    public static void main(String[] args) {
        try {
            File file = new File("/Users/kec/Solor/tinkar-export.zip");
            LoadEntitiesFromDTO loadTink = new LoadEntitiesFromDTO(file);
            int count = loadTink.call();
            System.out.println("Loaded. " + loadTink.report());
            DataProviderWebsocketServer server = new DataProviderWebsocketServer();
            server.launch(args);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
