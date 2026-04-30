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
package dev.ikm.tinkar.provider.grpc;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.DataActivity;
import dev.ikm.tinkar.common.service.DataServiceController;
import dev.ikm.tinkar.common.service.DataUriOption;
import dev.ikm.tinkar.common.service.NidGenerator;
import dev.ikm.tinkar.common.service.PrimitiveDataSearchResult;
import dev.ikm.tinkar.common.service.PrimitiveDataService;
import dev.ikm.tinkar.common.service.ProviderController;
import dev.ikm.tinkar.common.service.ServiceExclusionGroup;
import dev.ikm.tinkar.common.service.ServiceLifecyclePhase;
import dev.ikm.tinkar.common.sets.ConcurrentHashSet;
import dev.ikm.tinkar.common.util.ints2long.IntsInLong;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.PatternEntity;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.transform.TinkarSchemaToEntityTransformer;
import org.eclipse.collections.api.block.procedure.primitive.IntProcedure;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.ObjIntConsumer;

/**
 * Purpose-built {@link PrimitiveDataService} implementation for gRPC mode.
 *
 * <p>Replaces {@code ProviderEphemeral} in gRPC deployments. Entities are loaded into this
 * in-memory store by {@link GrpcSearchService#loadConceptWithSemantics} (via the
 * {@code LoadConceptEntityGraph} batch call). If an entity is requested that was not
 * prefetched, {@link #getBytes} falls back to a single-entity gRPC call
 * ({@code GetEntityByPublicId}) using the reverse NID→UUID map maintained here.
 *
 * <p>Unlike {@code ProviderEphemeral}, this class has no search-indexing side effects in
 * {@link #merge} — search in gRPC mode is handled entirely by {@link GrpcSearchService}.
 */
public class GrpcPrimitiveDataService implements PrimitiveDataService, NidGenerator {

    private static final Logger LOG = LoggerFactory.getLogger(GrpcPrimitiveDataService.class);

    protected static volatile GrpcPrimitiveDataService singleton;
    protected static final LongAdder writeSequence = new LongAdder();

    // Primary entity byte store: NID → serialized entity bytes
    private final ConcurrentHashMap<Integer, byte[]> nidComponentMap = new ConcurrentHashMap<>();

    // Deduplicates concurrent gRPC fetches for the same NID (thundering-herd guard)
    private final ConcurrentHashMap<Integer, CompletableFuture<byte[]>> inFlightFetches = new ConcurrentHashMap<>();

    // NIDs for which the server returned no entity — skip future gRPC retries (negative cache)
    private final ConcurrentHashSet<Integer> notFoundNids = new ConcurrentHashSet<>();

    // UUID ↔ NID bidirectional mapping
    private final ConcurrentHashMap<UUID, Integer> uuidNidMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, List<UUID>> nidToUuidsMap = new ConcurrentHashMap<>();

    // Secondary indices (mirrors ProviderEphemeral)
    private final ConcurrentHashMap<Integer, Integer> nidToPatternNidMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, long[]> nidToCitingComponentsNidMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, ConcurrentSkipListSet<Integer>> patternToElementNidsMap = new ConcurrentHashMap<>();
    final ConcurrentHashSet<Integer> patternNids  = new ConcurrentHashSet<>();
    final ConcurrentHashSet<Integer> conceptNids  = new ConcurrentHashSet<>();
    final ConcurrentHashSet<Integer> semanticNids = new ConcurrentHashSet<>();
    final ConcurrentHashSet<Integer> stampNids    = new ConcurrentHashSet<>();

    private final AtomicInteger nextNid = new AtomicInteger(PrimitiveDataService.FIRST_NID);

    private GrpcPrimitiveDataService() {
        LOG.info("Constructing GrpcPrimitiveDataService");
    }

    public static GrpcPrimitiveDataService provider() {
        if (singleton == null) {
            synchronized (GrpcPrimitiveDataService.class) {
                if (singleton == null) {
                    singleton = new GrpcPrimitiveDataService();
                }
            }
        }
        return singleton;
    }

    // ── PrimitiveDataService ─────────────────────────────────────────

    @Override
    public long writeSequence() {
        return writeSequence.sum();
    }

    @Override
    public void close() {
        singleton = null;
    }

    @Override
    public int nidForUuids(UUID... uuids) {
        int nid = PrimitiveDataService.nidForUuids(uuidNidMap, this, uuids);
        nidToUuidsMap.putIfAbsent(nid, Arrays.asList(uuids));
        return nid;
    }

    @Override
    public int nidForUuids(ImmutableList<UUID> uuidList) {
        int nid = PrimitiveDataService.nidForUuids(uuidNidMap, this, uuidList);
        nidToUuidsMap.putIfAbsent(nid, uuidList.toList());
        return nid;
    }

    @Override
    public boolean hasUuid(UUID uuid) {
        return uuidNidMap.containsKey(uuid);
    }

    @Override
    public boolean hasPublicId(PublicId publicId) {
        return publicId.asUuidList().stream().anyMatch(uuidNidMap::containsKey);
    }

    @Override
    public void forEach(ObjIntConsumer<byte[]> action) {
        nidComponentMap.forEach((nid, bytes) -> action.accept(bytes, nid));
    }

    @Override
    public void forEachParallel(ObjIntConsumer<byte[]> action) {
        nidComponentMap.entrySet().parallelStream()
                .forEach(e -> action.accept(e.getValue(), e.getKey()));
    }

    @Override
    public void forEachParallel(ImmutableIntList nids, ObjIntConsumer<byte[]> action) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void forEach(ImmutableIntList nids, ObjIntConsumer<byte[]> action) {
        throw new UnsupportedOperationException();
    }

    /**
     * Returns serialized entity bytes for {@code nid}.
     *
     * <p>Checks the local store first. On a cache miss, looks up the UUID(s) for this NID
     * in the reverse map and issues a {@code GetEntityByPublicId} gRPC call to fetch and
     * cache the entity before returning.
     *
     * <p>Concurrent requests for the same NID are deduplicated: only one thread makes the
     * gRPC call while others wait on a shared {@link CompletableFuture} (thundering-herd guard).
     *
     * <p>The transformer callbacks write directly into {@code nidComponentMap} via
     * {@link #merge} rather than going through {@code EntityService.putEntity()}, which
     * avoids a Caffeine re-entrance problem: {@code EntityProvider.getEntityFast} calls
     * {@code getBytes} from inside a Caffeine {@code get(key, loader)} computation, and
     * a {@code cache.put()} for the same key from within that loader can be silently
     * dropped, leaving {@code nidComponentMap} empty and causing an infinite gRPC retry loop.
     */
    @Override
    public byte[] getBytes(int nid) {
        byte[] bytes = nidComponentMap.get(nid);
        if (bytes != null) {
            return bytes;
        }

        if (notFoundNids.contains(nid)) {
            return null;
        }

        List<UUID> uuids = nidToUuidsMap.get(nid);
        if (uuids == null || uuids.isEmpty() || !GrpcSearchClient.isAvailable()) {
            return null;
        }

        CompletableFuture<byte[]> myFuture = new CompletableFuture<>();
        CompletableFuture<byte[]> existing = inFlightFetches.putIfAbsent(nid, myFuture);
        if (existing != null) {
            // Another thread is already fetching this NID — wait for its result
            try {
                return existing.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (ExecutionException e) {
                LOG.warn("gRPC fallback failed (via in-flight wait) for nid {}: {}", nid, e.getCause().getMessage());
                return null;
            }
        }

        // Won the race — perform the gRPC call
        try {
            dev.ikm.tinkar.schema.PublicId protoId = dev.ikm.tinkar.schema.PublicId.newBuilder()
                    .addAllUuids(uuids.stream().map(UUID::toString).toList())
                    .build();
            var response = GrpcSearchClient.get().getEntityByPublicId(protoId);
            if (!response.getSuccess() || response.getEntitiesList().isEmpty()) {
                LOG.debug("gRPC fallback: entity not found on server for nid {}", nid);
                notFoundNids.add(nid);
                myFuture.complete(null);
                return null;
            }

            // Store bytes directly into nidComponentMap via merge() to avoid a Caffeine
            // re-entrance issue: calling EntityService.putEntity() from within a Caffeine
            // get(key, loader) for the same key may silently drop the put.
            TinkarSchemaToEntityTransformer transformer = TinkarSchemaToEntityTransformer.getInstance();
            for (dev.ikm.tinkar.schema.TinkarMsg msg : response.getEntitiesList()) {
                transformer.transform(msg,
                        entity -> merge(entity.nid(),
                                entity instanceof SemanticEntity<?> se ? se.patternNid() : Integer.MAX_VALUE,
                                entity instanceof SemanticEntity<?> se ? se.referencedComponentNid() : Integer.MAX_VALUE,
                                entity.getBytes(), entity, DataActivity.SYNCHRONIZABLE_EDIT),
                        stamp  -> merge(stamp.nid(), Integer.MAX_VALUE, Integer.MAX_VALUE,
                                stamp.getBytes(), stamp, DataActivity.SYNCHRONIZABLE_EDIT));
            }

            byte[] result = nidComponentMap.get(nid);
            if (result == null) {
                LOG.warn("gRPC fallback: entity returned but not stored in nidComponentMap for nid {}", nid);
                notFoundNids.add(nid);
            }
            myFuture.complete(result);
            return result;
        } catch (Exception e) {
            LOG.warn("gRPC fallback failed for nid {}: {}", nid, e.getMessage());
            myFuture.completeExceptionally(e);
            return null;
        } finally {
            inFlightFetches.remove(nid);
        }
    }

    @Override
    public byte[] merge(int nid, int patternNid, int referencedComponentNid,
                        byte[] value, Object sourceObject, DataActivity activity) {
        nidToPatternNidMap.computeIfAbsent(nid, k -> {
            if (patternNid != Integer.MAX_VALUE) {
                long citationLong = IntsInLong.ints2Long(nid, patternNid);
                nidToCitingComponentsNidMap.merge(referencedComponentNid,
                        new long[]{citationLong}, PrimitiveDataService::mergeCitations);
                patternToElementNidsMap.computeIfAbsent(nid, ignored -> new ConcurrentSkipListSet<>()).add(nid);
            }
            return patternNid;
        });

        if (sourceObject instanceof ConceptEntity concept) {
            conceptNids.add(concept.nid());
        } else if (sourceObject instanceof SemanticEntity semanticEntity) {
            semanticNids.add(semanticEntity.nid());
        } else if (sourceObject instanceof PatternEntity patternEntity) {
            patternNids.add(patternEntity.nid());
        } else if (sourceObject instanceof StampEntity stampEntity) {
            stampNids.add(stampEntity.nid());
        }

        byte[] mergedBytes = nidComponentMap.merge(nid, value, PrimitiveDataService::merge);
        writeSequence.increment();
        return mergedBytes;
    }

    @Override
    public PrimitiveDataSearchResult[] search(String query, int maxResultSize) {
        throw new UnsupportedOperationException(
                "Local search is not available in gRPC mode — use GrpcSearchService");
    }

    @Override
    public CompletableFuture<Void> recreateLuceneIndex() {
        throw new UnsupportedOperationException("No local Lucene index in gRPC mode");
    }

    @Override
    public void forEachSemanticNidOfPattern(int patternNid, IntProcedure procedure) {
        nidToPatternNidMap.forEach((nid, storedPatternNid) -> {
            if (patternNid == storedPatternNid) {
                procedure.accept(nid);
            }
        });
    }

    @Override
    public void forEachPatternNid(IntProcedure procedure) {
        patternNids.forEach(procedure::accept);
    }

    @Override
    public void forEachConceptNid(IntProcedure procedure) {
        conceptNids.forEach(procedure::accept);
    }

    @Override
    public void forEachStampNid(IntProcedure procedure) {
        stampNids.forEach(procedure::accept);
    }

    @Override
    public void forEachSemanticNid(IntProcedure procedure) {
        semanticNids.forEach(procedure::accept);
    }

    @Override
    public void forEachSemanticNidForComponent(int componentNid, IntProcedure procedure) {
        long[] citationLongs = nidToCitingComponentsNidMap.get(componentNid);
        if (citationLongs != null) {
            for (long citationLong : citationLongs) {
                procedure.accept((int) (citationLong >> 32));
            }
        }
    }

    @Override
    public void forEachSemanticNidForComponentOfPattern(int componentNid, int patternNid,
                                                        IntProcedure procedure) {
        long[] citationLongs = nidToCitingComponentsNidMap.get(componentNid);
        if (citationLongs != null) {
            for (long citationLong : citationLongs) {
                int citingNid        = (int) (citationLong >> 32);
                int citingPatternNid = (int)  citationLong;
                if (patternNid == citingPatternNid) {
                    procedure.accept(citingNid);
                }
            }
        }
    }

    @Override
    public String name() {
        return "gRPC Primitive Data";
    }

    // ── NidGenerator ────────────────────────────────────────────────

    @Override
    public int newNid() {
        return nextNid.getAndIncrement();
    }

    // ── Controller ──────────────────────────────────────────────────

    /**
     * Lifecycle controller for {@link GrpcPrimitiveDataService}.
     * Registered via JPMS {@code provides} in {@code module-info.java} so that
     * {@code PrimitiveData.getControllerOptions()} discovers it alongside the standard
     * providers. Komet's {@code startGrpcMode()} selects it by name.
     */
    public static class Controller extends ProviderController<GrpcPrimitiveDataService>
            implements DataServiceController<PrimitiveDataService> {

        public static final String CONTROLLER_NAME = "gRPC Data Service";

        @Override
        protected GrpcPrimitiveDataService createProvider() {
            return GrpcPrimitiveDataService.provider();
        }

        @Override
        protected void startProvider(GrpcPrimitiveDataService provider) {
            // ready on construction
        }

        @Override
        protected void stopProvider(GrpcPrimitiveDataService provider) {
            LOG.info("Stopping GrpcPrimitiveDataService");
            provider.close();
        }

        @Override
        protected String getProviderName() {
            return "GrpcPrimitiveDataService";
        }

        @Override
        public ImmutableList<Class<?>> serviceClasses() {
            return Lists.immutable.of(PrimitiveDataService.class);
        }

        @Override
        public ServiceLifecyclePhase getLifecyclePhase() {
            return ServiceLifecyclePhase.DATA_STORAGE;
        }

        @Override
        public int getSubPriority() {
            return 40;
        }

        @Override
        public Optional<ServiceExclusionGroup> getMutualExclusionGroup() {
            return Optional.of(ServiceExclusionGroup.DATA_PROVIDER);
        }

        @Override
        public String controllerName() {
            return CONTROLLER_NAME;
        }

        @Override
        public boolean running() {
            return GrpcPrimitiveDataService.singleton != null;
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
            // nothing to persist in gRPC read-only mode
        }

        @Override
        public void reload() {
            throw new UnsupportedOperationException("Cannot reload gRPC data service");
        }

        @Override
        public boolean isValidDataLocation(String name) {
            return false;
        }

        @Override
        public List<DataUriOption> providerOptions() {
            return List.of();
        }
    }
}
