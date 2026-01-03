/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.provider.websocket.client;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.*;
import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import dev.ikm.tinkar.common.validation.ValidationRecord;
import dev.ikm.tinkar.common.validation.ValidationSeverity;
import dev.ikm.tinkar.entity.EntityService;
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
import org.eclipse.collections.api.block.procedure.primitive.IntProcedure;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.map.ImmutableMap;
import org.eclipse.collections.api.map.MutableMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.ObjIntConsumer;

public class DataProviderWebsocketClient
        extends Launcher
        implements PrimitiveDataService {
    private static final Logger LOG = LoggerFactory.getLogger(DataProviderWebsocketClient.class);
    private static final Integer wsKey = Integer.valueOf(1);
    private final URI uri;
    @Inject
    AsyncHttpClient httpClient;
    @Inject
    Eventloop eventloop;
    ConcurrentHashMap<Integer, WebSocket> wsMap = new ConcurrentHashMap<>();

    public DataProviderWebsocketClient(URI uri) {
        this.uri = uri;
    }

    public static void main(String[] args) throws Exception {
        DataProviderWebsocketClient client = new DataProviderWebsocketClient(new URI("ws://127.0.0.1:8080/"));
        client.launch(args);
    }

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
        LOG.info("\nWeb Socket request: " + url);
        CompletableFuture<?> future = eventloop.submit(() -> {
            getEntity(url, PrimitiveDataService.FIRST_NID);
        });
        future.get();
        future = eventloop.submit(() -> {
            getEntity(url, PrimitiveDataService.FIRST_NID + 1);
        });
        future.get();
    }

    private void getEntity(String url, int nid) {
        LOG.info("Sending nid: " + nid);
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
                            LOG.info("Received: " + EntityService.get().unmarshalChronology(readData));
                        })
                        .whenComplete(webSocket::close));
    }

    @Override
    public long writeSequence() {
        throw new UnsupportedOperationException();
    }

    @Override
    public void close() {
        WebSocket ws = wsMap.remove(wsKey);
        if (ws != null) {
            ws.close();
        }
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

    @Override
    public boolean hasUuid(UUID uuid) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean hasPublicId(PublicId publicId) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEach(ObjIntConsumer<byte[]> action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEachParallel(ObjIntConsumer<byte[]> action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEachParallel(ImmutableIntList nids, ObjIntConsumer<byte[]> action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEach(ImmutableIntList nids, ObjIntConsumer<byte[]> action) {
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
                        LOG.info("Received: " + EntityService.get().unmarshalChronology(readData));

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
    public byte[] merge(int nid, int patternNid, int referencedComponentNid, byte[] value, Object sourceObject, DataActivity activity) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PrimitiveDataSearchResult[] search(String query, int maxResultSize) throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public CompletableFuture<Void> recreateLuceneIndex() throws Exception {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEachSemanticNidOfPattern(int patternNid, IntProcedure procedure) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEachPatternNid(IntProcedure procedure) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEachConceptNid(IntProcedure procedure) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEachStampNid(IntProcedure procedure) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEachSemanticNid(IntProcedure procedure) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEachSemanticNidForComponent(int componentNid, IntProcedure procedure) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEachSemanticNidForComponentOfPattern(int componentNid, int patternNid, IntProcedure procedure) {
        throw new UnsupportedOperationException();
    }

    @Override
    public String name() {
        return uri.toString();
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

    WebSocket webSocket() {
        return wsMap.computeIfAbsent(wsKey, (Integer key) ->
                {
                    try {
                        return httpClient.webSocketRequest(HttpRequest.get(uri.toString())).toCompletableFuture().get();
                    } catch (Exception e) {
                        e.printStackTrace();
                        throw new RuntimeException(e);
                    }
                }
        );
    }

    /**
     * Controller for DataProviderWebsocketClient lifecycle management.
     * <p>
     * Handles websocket-based remote data provider connection.
     * </p>
     */
    public static class Controller extends ProviderController<DataProviderWebsocketClient>
            implements DataServiceController<PrimitiveDataService> {

        public static final String CONTROLLER_NAME = "Websocket";
        private static final DataServiceProperty PASSWORD_PROPERTY =
                new DataServiceProperty("password", true, true);
        private static final DataServiceProperty USERNAME_PROPERTY =
                new DataServiceProperty("username", false, false);

        private final MutableMap<DataServiceProperty, String> providerProperties = Maps.mutable.empty();
        private DataUriOption dataUriOption;

        public Controller() {
            providerProperties.put(USERNAME_PROPERTY, null);
            providerProperties.put(PASSWORD_PROPERTY, null);
        }

        @Override
        protected DataProviderWebsocketClient createProvider() throws Exception {
            if (dataUriOption == null) {
                throw new IllegalStateException("DataUriOption must be set before creating provider");
            }
            return new DataProviderWebsocketClient(dataUriOption.uri());
        }

        @Override
        protected void startProvider(DataProviderWebsocketClient provider) throws Exception {
            provider.launch(new String[]{});
        }

        @Override
        protected void stopProvider(DataProviderWebsocketClient provider) {
            // Websocket client cleanup
        }

        @Override
        protected String getProviderName() {
            return "DataProviderWebsocketClient";
        }

        @Override
        public ServiceLifecyclePhase getLifecyclePhase() {
            return ServiceLifecyclePhase.DATA_STORAGE;
        }

        @Override
        public int getSubPriority() {
            return 50; // After local providers
        }

        @Override
        public Optional<ServiceExclusionGroup> getMutualExclusionGroup() {
            return Optional.of(ServiceExclusionGroup.DATA_PROVIDER);
        }

        // ========== DataServiceController Implementation ==========

        @Override
        public ImmutableMap<DataServiceProperty, String> providerProperties() {
            return providerProperties.toImmutable();
        }

        @Override
        public void setDataServiceProperty(DataServiceProperty key, String value) {
            providerProperties.put(key, value);
        }

        @Override
        public ValidationRecord[] validate(DataServiceProperty dataServiceProperty, Object value, Object target) {
            if (PASSWORD_PROPERTY.equals(dataServiceProperty)) {
                if (value instanceof String password) {
                    if (password.isBlank()) {
                        return new ValidationRecord[]{new ValidationRecord(ValidationSeverity.ERROR,
                                "Password cannot be blank", target)};
                    } else if (password.length() < 5) {
                        return new ValidationRecord[]{new ValidationRecord(ValidationSeverity.ERROR,
                                "Password cannot be less than 5 characters", target)};
                    } else if (password.length() < 8) {
                        return new ValidationRecord[]{
                                new ValidationRecord(ValidationSeverity.WARNING,
                                        "Password recommended to be 8 or more characters", target),
                                new ValidationRecord(ValidationSeverity.INFO,
                                        "Password is " + password.length() + " characters long", target)
                        };
                    } else {
                        return new ValidationRecord[]{new ValidationRecord(ValidationSeverity.OK,
                                "Password OK", target)};
                    }
                }
            }
            return new ValidationRecord[]{};
        }

        @Override
        public List<DataUriOption> providerOptions() {
            try {
                return List.of(new DataUriOption("localhost websocket", new URI("ws://127.0.0.1:8080/")));
            } catch (URISyntaxException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public boolean isValidDataLocation(String name) {
            return name.toLowerCase(Locale.ROOT).startsWith("ws://");
        }

        @Override
        public void setDataUriOption(DataUriOption dataUriOption) {
            this.dataUriOption = dataUriOption;
        }

        @Override
        public String controllerName() {
            return CONTROLLER_NAME;
        }

        @Override
        public ImmutableList<Class<?>> serviceClasses() {
            // DataProviderWebsocketClient (the generic type parameter P) implements PrimitiveDataService
            // This establishes the contract: ProviderController<DataProviderWebsocketClient> provides PrimitiveDataService
            return Lists.immutable.of(PrimitiveDataService.class);
        }

        @Override
        public boolean running() {
            return getProvider() != null;
        }

        @Override
        public void start() {
            startup();
        }

        @Override
        public void stop() {
            shutdown();
        }

        @Override
        public void save() {
            // Nothing to save for websocket client
        }

        @Override
        public void reload() {
            throw new UnsupportedOperationException("Reload not supported for websocket client");
        }

        // Note: provider() method is inherited from ProviderController base class
    }
}
