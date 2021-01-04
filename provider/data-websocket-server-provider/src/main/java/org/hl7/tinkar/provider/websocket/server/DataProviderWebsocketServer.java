package org.hl7.tinkar.provider.websocket.server;

import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import io.activej.launchers.http.HttpServerLauncher;
import io.activej.http.AsyncServlet;
import io.activej.http.RoutingServlet;
import io.activej.http.WebSocket.Message;
import io.activej.inject.annotation.Provides;

public class DataProviderWebsocketServer extends HttpServerLauncher {

    @Provides
    AsyncServlet servlet() {
        return RoutingServlet.create()
                .mapWebSocket("/", webSocket -> webSocket.readMessage()
                        .whenResult(message -> System.out.println("Received:" + message.getBuf().readInt()))
                        .then(() -> {
                            ByteBuf buf = ByteBufPool.allocate(32);
                            byte[] data = new byte[] {1, 2, 3};
                            buf.writeInt(data.length);
                            buf.write(data);
                            return webSocket.writeMessage(Message.binary(buf));
                        })
                        .whenComplete(webSocket::close));
    }

    public static void main(String[] args) throws Exception {
        DataProviderWebsocketServer server = new DataProviderWebsocketServer();
        server.launch(args);
    }
}