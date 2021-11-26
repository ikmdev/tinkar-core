package org.hl7.tinkar.provider.entity;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.auto.service.AutoService;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import io.smallrye.mutiny.subscription.BackPressureFailure;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.alert.AlertObject;
import org.hl7.tinkar.common.alert.AlertStreams;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.common.service.DefaultDescriptionForNidService;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.service.PublicIdService;
import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.component.Stamp;
import org.hl7.tinkar.component.Version;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.entity.transaction.Transaction;
import org.hl7.tinkar.terms.EntityFacade;
import org.hl7.tinkar.terms.State;
import org.hl7.tinkar.terms.TinkarTerm;
import org.reactivestreams.FlowAdapters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

import static org.hl7.tinkar.terms.TinkarTerm.DESCRIPTION_PATTERN;

//@AutoService({EntityService.class, PublicIdService.class, DefaultDescriptionForNidService.class})
public class EntityProvider implements EntityService, PublicIdService, DefaultDescriptionForNidService {

    private static final Logger LOG = LoggerFactory.getLogger(EntityProvider.class);
    private static final Cache<Integer, String> STRING_CACHE = Caffeine.newBuilder().maximumSize(1024).build();
    private static final Cache<Integer, Entity> ENTITY_CACHE = Caffeine.newBuilder().maximumSize(10240).build();
    private static final Cache<Integer, StampEntity> STAMP_CACHE = Caffeine.newBuilder().maximumSize(1024).build();


    //Multi<Entity<? extends EntityVersion>> chronologyBroadcaster = BroadcastProcessor.create().toHotStream();
    //  <T extends Entity<? extends EntityVersion>>
    final BroadcastProcessor<Integer> processor;

    /**
     * TODO elegant shutdown of entityStream and others
     */
    protected EntityProvider() {
        LOG.info("Constructing EntityProvider");
        this.processor = BroadcastProcessor.create();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super Integer> subscriber) {
        this.processor.subscribe().withSubscriber(FlowAdapters.toSubscriber(subscriber));
    }

    @Override
    public String textFast(int nid) {
        // TODO use a default language coordinate instead of this hardcode routine.
        return STRING_CACHE.get(nid, integer -> {
            int[] semanticNids = PrimitiveData.get().semanticNidsForComponentOfPattern(nid, DESCRIPTION_PATTERN.nid());
            String anyString = null;
            String fqnString = null;
            for (int semanticNid : semanticNids) {
                Entity descriptionSemanticEntity = Entity.getFast(semanticNid);
                if (descriptionSemanticEntity instanceof SemanticEntity descriptionSemantic) {
                    Entity entity = Entity.getFast(descriptionSemantic.patternNid());
                    if (entity instanceof PatternEntity pattern) {
                        // TODO: use version computer to get version
                        PatternEntityVersion patternEntityVersion = (PatternEntityVersion) pattern.versions().get(0);
                        SemanticEntityVersion version = (SemanticEntityVersion) descriptionSemantic.versions().get(0);
                        int indexForMeaning = patternEntityVersion.indexForMeaning(TinkarTerm.DESCRIPTION_TYPE);
                        int indexForText = patternEntityVersion.indexForMeaning(TinkarTerm.TEXT_FOR_DESCRIPTION);
                        if (version.fieldValues().get(indexForMeaning).equals(TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE)) {
                            return (String) version.fieldValues().get(indexForText);
                        }
                        if (version.fieldValues().get(indexForMeaning).equals(TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE)) {
                            fqnString = (String) version.fieldValues().get(indexForText);
                        }
                        anyString = (String) version.fieldValues().get(indexForText);
                    } else {
                        anyString = " <" + entity.nid() + ">" + entity.asUuidList().toString();
                        // Added in case entity.toString() itself throws an exception, at least get a UUID for the problem.
                        AlertStreams.getRoot().dispatch(AlertObject.makeError(new IllegalStateException("Expecting a pattern entity. Found entity with id:  " + anyString)));
                        AlertStreams.getRoot().dispatch(AlertObject.makeError(new IllegalStateException("Expecting a pattern entity. Found: " + entity)));
                    }
                } else {
                    anyString = " <" + descriptionSemanticEntity.nid() + "> " + descriptionSemanticEntity.asUuidList().toString();
                    LOG.error("ERROR getting string for nid: " + anyString);
                    LOG.error("ERROR Nid - 2: <" + (nid - 2) + "> " + getChronology(nid - 2));
                    LOG.error("ERROR Nid - 1: <" + (nid - 1) + "> " + getChronology(nid - 1));
                    LOG.error("ERROR Nid: <" + nid + "> " + getChronology(nid - 1));
                    LOG.error("ERROR Nid + 1: <" + (nid + 1) + "> " + getChronology(nid + 1));
                    LOG.error("ERROR Nid + 2: <" + (nid + 2) + "> " + getChronology(nid + 2));

                    // Added in case entity.toString() itself throws an exception, at least get a UUID for the problem.
                    AlertStreams.getRoot().dispatch(AlertObject.makeError(new IllegalStateException("Expecting a description semantic entity from list: " +
                            Arrays.toString(semanticNids) + "\n Found entity with id:  " + anyString)));
                    AlertStreams.getRoot().dispatch(AlertObject.makeError(new IllegalStateException("Expecting a description semantic. Found: " + descriptionSemanticEntity)));
                }
            }
            if (fqnString != null) {
                return fqnString;
            }
            return anyString;
        });
    }

    @Override
    public <T extends Chronology<V>, V extends Version> Optional<T> getChronology(int nid) {
        Entity entity = getEntityFast(nid);
        if (entity == null || entity.canceled()) {
            return Optional.empty();
        }
        return Optional.of((T) entity);
    }

    @Override
    public int nidForUuids(UUID... uuids) {
        return PrimitiveData.get().nidForUuids(uuids);
    }

    @Override
    public int nidForPublicId(PublicId publicId) {
        return PrimitiveData.get().nidForUuids(publicId.asUuidArray());
    }

    public <T extends Entity<V>, V extends EntityVersion> T getEntityFast(int nid) {
        return (T) ENTITY_CACHE.get(nid, entityNid -> {
            byte[] bytes = PrimitiveData.get().getBytes(nid);
            if (bytes == null) {
                return null;
            }
            return EntityRecordFactory.make(bytes);
        });
    }

    @Override
    public int nidForUuids(ImmutableList<UUID> uuidList) {
        return PrimitiveData.get().nidForUuids(uuidList);
    }

    @Override
    public StampEntity getStampFast(int nid) {
        return STAMP_CACHE.get(nid, stampNid -> {
                    byte[] bytes = PrimitiveData.get().getBytes(nid);
                    if (bytes == null) {
                        return null;
                    }
                    return EntityRecordFactory.make(bytes);
                }
        );
    }

    @Override
    public void putEntity(Entity entity) {
        invalidateCaches(entity);
        ENTITY_CACHE.put(entity.nid(), entity);
        if (entity instanceof StampEntity stampEntity) {
            STAMP_CACHE.put(stampEntity.nid(), stampEntity);
            if (stampEntity.lastVersion().stateNid() == State.CANCELED.nid()) {
                PrimitiveData.get().addCanceledStampNid(stampEntity.nid());
            }
        }
        if (entity instanceof SemanticEntity semanticEntity) {
            PrimitiveData.get().merge(entity.nid(),
                    semanticEntity.patternNid(),
                    semanticEntity.referencedComponentNid(),
                    entity.getBytes(), entity);
        } else {
            PrimitiveData.get().merge(entity.nid(), Integer.MAX_VALUE, Integer.MAX_VALUE, entity.getBytes(), entity);
        }
        try {
            processor.onNext(entity.nid());
            if (entity instanceof SemanticEntity semanticEntity) {
                processor.onNext(semanticEntity.referencedComponentNid());
            }
        } catch (BackPressureFailure e) {
            LOG.warn(e.getLocalizedMessage());
        }
    }

    @Override
    public void putStamp(StampEntity stampEntity) {
        putEntity(stampEntity);
    }

    private void invalidateCaches(Entity entity) {
        STRING_CACHE.invalidate(entity.nid());
        ENTITY_CACHE.invalidate(entity.nid());
        STAMP_CACHE.invalidate(entity.nid());
        if (entity instanceof SemanticEntity semanticEntity) {
            STRING_CACHE.invalidate(semanticEntity.referencedComponentNid());
            Entity parent = getEntityFast(semanticEntity.referencedComponentNid());
            while (parent != null) {
                switch (parent) {
                    case ConceptEntity conceptEntity -> {
                        parent = null;
                        STRING_CACHE.invalidate(conceptEntity.nid());
                    }
                    case PatternEntity patternEntity -> {
                        parent = null;
                        STRING_CACHE.invalidate(patternEntity.nid());
                    }
                    case SemanticEntity semantic -> {
                        // If semantic is a dialect, might invalidate preferred description,
                        // so need to go up to concept or pattern to invalidate strings in cache.
                        parent = getEntityFast(semantic.referencedComponentNid());
                        STRING_CACHE.invalidate(semantic.nid());
                    }
                    default -> throw new IllegalStateException("Unexpected value: " + parent);
                }
            }
        }
    }

    @Override
    public Entity unmarshalChronology(byte[] bytes) {
        return EntityRecordFactory.make(bytes);
    }

    @Override
    public void forEachSemanticOfPattern(int patternNid, Consumer<SemanticEntity<SemanticEntityVersion>> procedure) {
        PrimitiveData.get().forEachSemanticNidOfPattern(patternNid, (int nid) -> procedure.accept(getEntityFast(nid)));
    }

    @Override
    public int[] semanticNidsOfPattern(int patternNid) {
        return PrimitiveData.get().semanticNidsOfPattern(patternNid);
    }

    @Override
    public void forEachSemanticForComponent(int componentNid, Consumer<SemanticEntity<SemanticEntityVersion>> procedure) {
        PrimitiveData.get().forEachSemanticNidForComponent(componentNid, (int nid) -> procedure.accept(getEntityFast(nid)));
    }

    @Override
    public int[] semanticNidsForComponent(int componentNid) {
        return PrimitiveData.get().semanticNidsForComponent(componentNid);
    }

    @Override
    public void forEachSemanticForComponentOfPattern(int componentNid, int patternNid, Consumer<SemanticEntity<SemanticEntityVersion>> procedure) {
        PrimitiveData.get().forEachSemanticNidForComponentOfPattern(componentNid, patternNid, (int nid) -> procedure.accept(getEntityFast(nid)));
    }

    @Override
    public int[] semanticNidsForComponentOfPattern(int componentNid, int patternNid) {
        return PrimitiveData.get().semanticNidsForComponentOfPattern(componentNid, patternNid);
    }

    @Override
    public void notifyRefreshRequired(Transaction transaction) {
        transaction.forEachComponentInTransaction(nid -> {
            Entity.get(nid).ifPresent(entity -> invalidateCaches(entity));
            this.processor.onNext(nid);
        });
    }

    @Override
    public PublicId publicId(int nid) {
        return getEntityFast(nid).publicId();
    }

    @Override
    public <T extends Chronology<V>, V extends Version> Optional<T> getChronology(PublicId publicId) {
        Entity entity;
        if (publicId instanceof EntityFacade entityFacade) {
            entity = getEntityFast(entityFacade.nid());
        } else {
            entity = getEntityFast(nidForPublicId(publicId));
        }
        if (entity == null || entity.canceled()) {
            return Optional.empty();
        }
        return Optional.of((T) entity);
    }

    @Override
    public <T extends Chronology<V>, V extends Version> void putChronology(T chronology) {
        if (chronology instanceof Entity entity) {
            putEntity(entity);
        } else {
            putEntity(EntityRecordFactory.make((Chronology<Version>) chronology));
            for (Version version : chronology.versions()) {
                Stamp stamp = version.stamp();
                int nid = PrimitiveData.get().nidForUuids(stamp.publicId().asUuidArray());
                if (PrimitiveData.get().getBytes(nid) == null) {
                    putEntity(EntityRecordFactory.make(stamp));
                }
            }
        }
    }

    @AutoService(CachingService.class)
    public static class CacheProvider implements CachingService {

        @Override
        public void reset() {
            LOG.info("Resetting Entity Caches");
            STRING_CACHE.invalidateAll();
            ENTITY_CACHE.invalidateAll();
            STAMP_CACHE.invalidateAll();
        }
    }
}
