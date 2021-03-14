package org.hl7.tinkar.provider.websocket.client;

import io.activej.bytebuf.ByteBuf;
import io.activej.bytebuf.ByteBufPool;
import io.activej.eventloop.Eventloop;
import io.activej.http.AsyncHttpClient;
import io.activej.http.HttpRequest;
import io.activej.http.WebSocket;
import io.activej.http.WebSocket.Message;
import io.activej.inject.annotation.Inject;
import io.activej.inject.annotation.Provides;
import io.activej.inject.module.Module;
import io.activej.launcher.Launcher;
import io.activej.service.ServiceGraphModule;
import org.eclipse.collections.api.block.procedure.Procedure2;
import org.eclipse.collections.api.block.procedure.primitive.IntProcedure;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.service.PrimitiveDataService;
import org.hl7.tinkar.common.util.uuid.UuidUtil;
import org.hl7.tinkar.provider.websocket.client.internal.Get;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;
import java.util.function.ObjIntConsumer;

public class DataProviderWebsocketClient
        extends Launcher
        implements PrimitiveDataService {

    @Inject
    AsyncHttpClient httpClient;

    @Inject
    Eventloop eventloop;

    ConcurrentHashMap<Integer, WebSocket> wsMap = new ConcurrentHashMap<>();

    private static final Integer wsKey = Integer.valueOf(1);

    @Provides
    Eventloop eventloop() {
        return Eventloop.create();
    }

    @Provides
    AsyncHttpClient client(Eventloop eventloop) {
        return AsyncHttpClient.create(eventloop);
    }

    WebSocket webSocket() {
        return wsMap.computeIfAbsent(wsKey, (Integer key) ->
                {
                    try {
                        return httpClient.webSocketRequest(HttpRequest.get("ws://127.0.0.1:8080/")).toCompletableFuture().get();
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                }
        );
    }

    @Override
    public void close() {
        WebSocket ws = wsMap.remove(wsKey);
        if (ws != null) {
            ws.close();
        }
    }

    @Override
    protected Module getModule() {
        return ServiceGraphModule.create();
    }

    @Override
    public void forEachEntityOfType(int typeDefinitionNid, IntProcedure procedure) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEachSemanticForComponent(int componentNid, IntProcedure procedure) {
        throw new UnsupportedOperationException();
    }

    @Override
    public int nidForUuids(UUID... uuids) {
        try {
            return nidForLongArray(UuidUtil.asArray(uuids));
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public int nidForUuids(ImmutableList<UUID> uuidList) {
        try {
            return nidForLongArray(UuidUtil.asArray(uuidList));
        } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }

    private int nidForLongArray(long[] uuidParts) throws ExecutionException, InterruptedException {
        ByteBuf buf = ByteBufPool.allocate(32);
        buf.writeByte(RemoteOperations.NID_FOR_UUIDS.token);
        buf.writeInt(uuidParts.length);
        for (long part : uuidParts) {
            buf.writeLong(part);
        }
        AtomicInteger nid = new AtomicInteger();
        final WebSocket ws = webSocket();
        CompletableFuture<?> future = eventloop.submit(() -> {
            ws.writeMessage(Message.binary(buf))
                    .then(ws::readMessage)
                    .whenResult(message -> {
                        ByteBuf readBuf = message.getBuf();
                        nid.set(readBuf.readInt());
                    });
        });
        future.get();
        return nid.get();
    }

    @Override
    public byte[] merge(int nid, int setNid, int referencedComponentNid, byte[] value) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEach(ObjIntConsumer<byte[]> action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEachParallel(ObjIntConsumer<byte[]>  action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEachSemanticForComponentOfType(int componentNid, int typeDefinitionNid, IntProcedure procedure) {
        throw new UnsupportedOperationException();
    }

    @Override
    public byte[] getBytes(int nid) {

        ByteBuf buf = ByteBufPool.allocate(32);
        buf.writeByte(RemoteOperations.GET_BYTES.token);
        buf.writeInt(nid);
        AtomicReference<byte[]> readDataReference = new AtomicReference<>();
        final WebSocket ws = webSocket();

        CompletableFuture<?> future = eventloop.submit(() -> {
            ws.writeMessage(Message.binary(buf))
                    .then(ws::readMessage)
                    .whenResult(message -> {
                        ByteBuf readBuf = message.getBuf();
                        int length = readBuf.readInt();
                        byte[] readData = new byte[length];
                        readBuf.read(readData);
                        readDataReference.set(readData);
                        System.out.println("Received: " + Get.entityService().unmarshalChronology(readData));

                    });
        });

        try {
            future.get();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }

        return readDataReference.get();
    }

    @Override
    protected void run() throws ExecutionException, InterruptedException {
        String url = args.length != 0 ? args[0] : "ws://127.0.0.1:8080/";
        System.out.println("\nWeb Socket request: " + url);
        CompletableFuture<?> future = eventloop.submit(() -> {
            getEntity(url, Integer.MIN_VALUE + 1);
        });
        future.get();
        future = eventloop.submit(() -> {
            getEntity(url, Integer.MIN_VALUE + 2);
        });
        future.get();
    }

    private void getEntity(String url, int nid) {
        System.out.println("Sending nid: " + nid);
        ByteBuf buf = ByteBufPool.allocate(32);
        buf.writeByte(RemoteOperations.GET_BYTES.token);
        buf.writeInt(nid);
        httpClient.webSocketRequest(HttpRequest.get(url))
                .then(webSocket -> webSocket.writeMessage(Message.binary(buf))
                        .then(webSocket::readMessage)
                        .whenResult(message -> {
                            ByteBuf readBuf = message.getBuf();
                            int length = readBuf.readInt();
                            byte[] readData = new byte[length];
                            readBuf.read(readData);
                            System.out.println("Received: " + Get.entityService().unmarshalChronology(readData));
                        })
                        .whenComplete(webSocket::close));
    }

    public static void main(String[] args) throws Exception {
        DataProviderWebsocketClient client = new DataProviderWebsocketClient();
        client.launch(args);
    }
}