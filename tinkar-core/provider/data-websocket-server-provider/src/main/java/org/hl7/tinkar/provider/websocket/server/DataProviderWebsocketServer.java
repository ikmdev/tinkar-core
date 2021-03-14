package org.hl7.tinkar.provider.websocket.server;

import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import io.activej.http.AsyncServlet;
import io.activej.http.RoutingServlet;
import io.activej.http.WebSocket.Message;
import io.activej.inject.annotation.Provides;
import io.activej.launchers.http.MultithreadedHttpServerLauncher;
import org.hl7.tinkar.common.service.PrimitiveDataService;

import java.util.ServiceLoader;
import java.util.concurrent.atomic.AtomicInteger;

public class DataProviderWebsocketServer extends MultithreadedHttpServerLauncher {

    private final ServiceLoader<PrimitiveDataService> serviceLoader;
    private final PrimitiveDataService dataService;

    public DataProviderWebsocketServer() {
        this.serviceLoader = ServiceLoader.load(PrimitiveDataService.class);
        this.dataService = this.serviceLoader.findFirst().get();
    }

    @Provides
    AsyncServlet servlet() {
        AtomicInteger nid = new AtomicInteger();
        return RoutingServlet.create()
                .mapWebSocket("/", webSocket -> webSocket.readMessage()
                        .whenResult(message -> {
                            ByteBuf buf = message.getBuf();
                            PrimitiveDataService.RemoteOperations operation = PrimitiveDataService.RemoteOperations.fromToken(buf.readByte());
                            nid.set(buf.readInt());
                            System.out.println("Received: " + operation + " for: " + nid);
                        })
                        .then(() -> {

                            byte[] data = dataService.getBytes(nid.get());
                            ByteBuf buf = ByteBufPool.allocate(data.length);
                            buf.writeInt(data.length);
                            buf.write(data);
                            return webSocket.writeMessage(Message.binary(buf));
                        }));
    }

    public static void main(String[] args) throws Exception {
        DataProviderWebsocketServer server = new DataProviderWebsocketServer();
        server.launch(args);
    }
}