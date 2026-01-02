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
package dev.ikm.tinkar.common.service;

import dev.ikm.tinkar.common.alert.AlertObject;
import dev.ikm.tinkar.common.alert.AlertStreams;
import dev.ikm.tinkar.common.id.IntIdCollection;
import dev.ikm.tinkar.common.id.PublicId;
import org.eclipse.collections.api.list.primitive.IntList;
import org.eclipse.collections.api.set.primitive.IntSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.ToIntFunction;

public class PrimitiveData {
    private static final Logger LOG = LoggerFactory.getLogger(PrimitiveData.class);


    public static long PREMUNDANE_TIME = Long.MIN_VALUE + 1;

    public static Instant PREMUNDANE_INSTANT = Instant.ofEpochSecond(Instant.MIN.getEpochSecond() + 1, 0);

    public static UUID NONEXISTENT_STAMP_UUID = UUID.fromString("00fea511-30eb-4bbb-9105-c846db5bf0ad");
    private static DataServiceController<PrimitiveDataService> controllerSingleton;
    private static DefaultDescriptionForNidService defaultDescriptionForNidServiceSingleton;
    private static PublicIdService publicIdServiceSingleton;
    private static PrimitiveData singleton;
    private static final CopyOnWriteArrayList<SaveState> statesToSave = new CopyOnWriteArrayList<>();

    static {
        try {
            singleton = new PrimitiveData();
        } catch (Throwable throwable) {
            //TODO: Understand why.
            //throwable.printStackTrace();
            //We don't want to swallow exceptions...
            throwable.printStackTrace();       
          }
    }

    private PrimitiveData() {
    }

    public static void start() {
        controllerSingleton.start();
        ServiceLoader<DefaultDescriptionForNidService> loader = PluggableService.load(DefaultDescriptionForNidService.class);
        PrimitiveData.defaultDescriptionForNidServiceSingleton = loader.findFirst().get();
        ServiceLoader<PublicIdService> publicIdLoader = PluggableService.load(PublicIdService.class);
        PrimitiveData.publicIdServiceSingleton = publicIdLoader.findFirst().get();
        LOG.info("Default desc service: " + defaultDescriptionForNidServiceSingleton);
    }

    public static void stop() {
        SimpleIndeterminateTracker progressTask = new SimpleIndeterminateTracker("Stop primitive data provider");
        TinkExecutor.threadPool().submit(progressTask);
        try {
            save();
            if (controllerSingleton != null) {
                controllerSingleton.stop();
            }
        } catch (Throwable ex) {
            LOG.error(ex.getLocalizedMessage(), ex);
        } finally {
            progressTask.finished();
            TinkExecutor.stop();
        }
    }

    public static void save() {
        if (controllerSingleton != null) {
            controllerSingleton.save();
        }
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (SaveState state : statesToSave) {
            try {
                CompletableFuture<Void> savedState = state.save();
                if (savedState != null) {
                    futures.add(savedState);
                }
            } catch (Exception e) {
                AlertStreams.getRoot().dispatch(AlertObject.makeError(e));
            }
        }
        if (!futures.isEmpty()) {
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            } catch (Exception e) {
                AlertStreams.getRoot().dispatch(AlertObject.makeError(e));
            }
        }
    }

    public static CopyOnWriteArrayList<SaveState> getStatesToSave() {
        return statesToSave;
    }

    public static void reload() {
        controllerSingleton.reload();
    }

    public static boolean running() {
        if (controllerSingleton == null) {
            return false;
        }
        return controllerSingleton.running();
    }

    public static List<DataServiceController> getControllerOptions() {
        final List<DataServiceController> dataServiceControllers = PluggableService.load(DataServiceController.class)
                .stream().map(dataServiceControllerProvider -> dataServiceControllerProvider.get()).toList();
        return dataServiceControllers;
    }

    public static void selectControllerByName(String name) {
        PrimitiveData.selectController((dataServiceController) -> {
            if (name.equals(dataServiceController.controllerName())) {
                return 1;
            }
            return -1;
        });
    }

    /**
     * Selects a controller by class. Provides compile-time safety.
     * <p>
     * This method provides type safety over {@link #selectControllerByName(String)}
     * by requiring the actual controller class at compile time, preventing runtime
     * errors from typos or references to non-existent controllers.
     * </p>
     *
     * @param controllerClass the controller class to select (e.g., {@code ProviderEphemeral.NewController.class})
     * @throws IllegalStateException if no matching controller is found
     */
    public static void selectControllerByClass(Class<? extends DataServiceController<?>> controllerClass) {
        PrimitiveData.selectController((dataServiceController) -> {
            if (controllerClass.isInstance(dataServiceController)) {
                return 1;
            }
            return -1;
        });
    }

    public static void selectController(ToIntFunction<DataServiceController<?>> scorer) {
        DataServiceController<PrimitiveDataService> topContender = null;
        int topScore = -1;
        int controllerCount = 0;
        ServiceLoader<DataServiceController> loader = PluggableService.load(DataServiceController.class);
        for (DataServiceController controller : loader) {
            if (PrimitiveDataService.class.isAssignableFrom(controller.serviceClass())) {
                controllerCount++;
                int score = scorer.applyAsInt(controller);
                if (score > topScore) {
                    topScore = score;
                    topContender = controller;
                }
            }
        }
        if (topScore > -1) {
            setController(topContender);
        } else {
            throw new IllegalStateException("No DataServiceController selected for provider. Tried " + controllerCount);
        }
    }

    public static DataServiceController getController() {
        return controllerSingleton;
    }

    public static void setController(DataServiceController controller) {
        controllerSingleton = controller;
    }

    public static String textFast(int nid) {
        return PrimitiveData.defaultDescriptionForNidServiceSingleton.textFast(nid);
    }

    public static String text(int nid) {
        Optional<String> textOptional = textOptional(nid);
        if (textOptional.isPresent()) {
            return textOptional.get();
        }
        return "<" + nid + ">";
    }

    public static Optional<String> textOptional(int nid) {
        try {
            return defaultDescriptionForNidServiceSingleton.textOptional(nid);
        } catch (RuntimeException ex) {
            AlertStreams.dispatchToRoot(ex);
            return Optional.empty();
        }
    }

    public static String textWithNid(int nid) {
        StringBuilder sb = new StringBuilder();
        textOptional(nid).ifPresent(s -> sb.append(s).append(" "));
        sb.append("<").append(nid).append(">");
        return sb.toString();
    }

    public static List<Optional<String>> optionalTextList(int... nids) {
        return defaultDescriptionForNidServiceSingleton.optionalTextList(nids);
    }

    public static List<Optional<String>> optionalTextList(IntIdCollection nids) {
        return defaultDescriptionForNidServiceSingleton.optionalTextList(nids);
    }

    public static List<Optional<String>> optionalTextList(IntList nids) {
        return defaultDescriptionForNidServiceSingleton.optionalTextList(nids);
    }

    public static List<Optional<String>> optionalTextList(IntSet nids) {
        return defaultDescriptionForNidServiceSingleton.optionalTextList(nids);
    }

    public static List<String> textList(int... nids) {
        return defaultDescriptionForNidServiceSingleton.textList(nids);
    }

    public static List<Optional<String>> textList(IntIdCollection nids) {
        return defaultDescriptionForNidServiceSingleton.textList(nids);
    }

    public static List<Optional<String>> textList(IntList nids) {
        return defaultDescriptionForNidServiceSingleton.textList(nids);
    }

    public static List<Optional<String>> textList(IntSet nids) {
        return defaultDescriptionForNidServiceSingleton.textList(nids);
    }

    public static PublicId publicId(int nid) {
        return publicIdServiceSingleton.publicId(nid);
    }

    public static int nid(PublicId publicId) {
        return get().nidForPublicId(publicId);
    }

    public static PrimitiveDataService get() {
        if (controllerSingleton != null) {
            return controllerSingleton.provider();
        }
        throw new IllegalStateException("No provider. Call Select provider prior to get()");
    }

    public static final ScopedValue<PublicId> SCOPED_PATTERN_PUBLICID_FOR_NID = ScopedValue.newInstance();

    /**
     * Example call when resolving via RocksDB:
     *
     * <pre>{@code
     * int nid = ScopedValue
     *         .where(SCOPED_PATTERN_PUBLICID_FOR_NID, patternFacade.publicId())
     *         .call(() -> PrimitiveData.nid(semanticUUID));
     * }</pre>
     *
     * @param uuids one or more UUIDs that identify the component
     * @return the nid corresponding to the provided UUIDs
     */
    public static int nid(UUID... uuids) {
        return get().nidForUuids(uuids);
    }


    /**
     * Calculate optimal chunk size for RocksDB iteration with parallel processing
     *
     * @param totalItems Total number of items to process
     * @return Optimal chunk size balancing parallelism and iterator overhead
     */
    public static int calculateOptimalChunkSize(int totalItems) {
        int numCores = Runtime.getRuntime().availableProcessors();

        // Target: 3-4x more chunks than cores for work stealing
        int targetChunks = numCores * 4;

        // Minimum chunk size to amortize RocksDB iterator overhead
        int minChunkSize;
        if (totalItems < 10_000) {
            minChunkSize = 1_000;      // Small datasets: reduce overhead tolerance
        } else if (totalItems < 100_000) {
            minChunkSize = 5_000;      // Medium datasets
        } else {
            minChunkSize = 25_000;     // Large datasets: prioritize amortizing overhead
        }

        // Maximum chunk size to ensure parallelism
        int maxChunkSize = 100_000;

        // Calculate chunk size
        int chunkSize = Math.max(minChunkSize, totalItems / targetChunks);
        chunkSize = Math.min(maxChunkSize, chunkSize);

        // Ensure at least 1 chunk
        return Math.max(1, chunkSize);
    }
    public static class CacheProvider implements CachingService {

        @Override
        public void reset() {
            controllerSingleton = null;
            defaultDescriptionForNidServiceSingleton = null;
            publicIdServiceSingleton = null;
            statesToSave.clear();
            singleton = new PrimitiveData();
        }
    }

}
