package org.hl7.tinkar.provider.websocket.client;
import io.activej.bytebuf.ByteBufPool;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncHttpClient;
import io.activej.http.HttpRequest;
import io.activej.http.WebSocket.Message;
import io.activej.inject.annotation.Inject;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.Module;
import io.activej.launcher.Launcher;
import io.activej.service.ServiceGraphModule;
import io.activej.bytebuf.ByteBuf;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

public class DataProviderWebsocketClient  extends Launcher {
    @Inject
    AsyncHttpClient httpClient;

    @Inject
    Eventloop eventloop;

    @Provides
    Eventloop eventloop() {
        return Eventloop.create();
    }

    @Provides
    AsyncHttpClient client(Eventloop eventloop) {
        return AsyncHttpClient.create(eventloop);
    }

    @Override
    protected Module getModule() {
        return ServiceGraphModule.create();
    }

    @Override
    protected void run() throws ExecutionException, InterruptedException {
        String url = args.length != 0 ? args[0] : "ws://127.0.0.1:8080/";
        System.out.println("\nWeb Socket request: " + url);
        CompletableFuture<?> future = eventloop.submit(() -> {
            System.out.println("Sending: nid");
            ByteBuf buf = ByteBufPool.allocate(32);
            buf.writeInt(Integer.MIN_VALUE + 1);
            return httpClient.webSocketRequest(HttpRequest.get(url))
                    .then(webSocket -> webSocket.writeMessage(Message.binary(buf))
                            .then(webSocket::readMessage)
                            .whenResult(message -> {
                                ByteBuf readBuf = message.getBuf();
                                int length = readBuf.readInt();
                                byte[] readData = new byte[length];
                                readBuf.read(readData);
                                System.out.println("Received: " + Arrays.toString(readData));
                            })
                            .whenComplete(webSocket::close));
        });
        future.get();
    }

    public static void main(String[] args) throws Exception {
        DataProviderWebsocketClient client = new DataProviderWebsocketClient();
        client.launch(args);
    }
}