package org.hl7.tinkar.provider.entity;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.auto.service.AutoService;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.operators.multi.processors.BroadcastProcessor;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.common.service.DefaultDescriptionForNidService;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.service.PublicIdService;
import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.component.Stamp;
import org.hl7.tinkar.component.Version;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.terms.EntityFacade;
import org.hl7.tinkar.terms.TinkarTerm;
import org.reactivestreams.FlowAdapters;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.function.Consumer;

import static org.hl7.tinkar.terms.TinkarTerm.DESCRIPTION_PATTERN;

@AutoService({EntityService.class, PublicIdService.class, DefaultDescriptionForNidService.class})
public class EntityProvider implements EntityService, PublicIdService, DefaultDescriptionForNidService {

    private static final Logger LOG = LoggerFactory.getLogger(EntityProvider.class);
    private static final Cache<Integer, String> STRING_CACHE = Caffeine.newBuilder().maximumSize(1024).build();
    private static final Cache<Integer, Entity> ENTITY_CACHE = Caffeine.newBuilder().maximumSize(10240).build();
    private static final Cache<Integer, StampEntity> STAMP_CACHE = Caffeine.newBuilder().maximumSize(1024).build();
    final Multi<Entity<? extends EntityVersion>> entityStream;


    //Multi<Entity<? extends EntityVersion>> chronologyBroadcaster = BroadcastProcessor.create().toHotStream();
    //  <T extends Entity<? extends EntityVersion>>
    final BroadcastProcessor<Entity<? extends EntityVersion>> processor;

    /**
     * TODO elegant shutdown of entityStream and others
     */
    public EntityProvider() {
        LOG.info("Constructing EntityProvider");
        this.processor = BroadcastProcessor.create();
        this.entityStream = processor.toHotStream();
    }

    @Override
    public void subscribe(Flow.Subscriber<? super Entity<? extends EntityVersion>> subscriber) {
        entityStream.subscribe().withSubscriber(FlowAdapters.toSubscriber(subscriber));
    }

    @Override
    public String textFast(int nid) {
        // TODO use a default language coordinate instead of this hardcode routine.
        return STRING_CACHE.get(nid, integer -> {
            int[] semanticNids = PrimitiveData.get().semanticNidsForComponentOfPattern(nid, DESCRIPTION_PATTERN.nid());
            String anyString = null;
            String fqnString = null;
            for (int semanticNid : semanticNids) {
                SemanticEntity descriptionSemantic = Entity.getFast(semanticNid);
                PatternEntity patternEntity = Entity.getFast(descriptionSemantic.patternNid());
                // TODO: use version computer to get version
                PatternEntityVersion patternEntityVersion = patternEntity.versions().get(0);
                SemanticEntityVersion version = descriptionSemantic.versions().get(0);
                int indexForMeaning = patternEntityVersion.indexForMeaning(TinkarTerm.DESCRIPTION_TYPE);
                int indexForText = patternEntityVersion.indexForMeaning(TinkarTerm.TEXT_FOR_DESCRIPTION);
                if (version.fields().get(indexForMeaning).equals(TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE)) {
                    return (String) version.fields().get(indexForText);
                }
                if (version.fields().get(indexForMeaning).equals(TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE)) {
                    fqnString = (String) version.fields().get(indexForText);
                }
                anyString = (String) version.fields().get(indexForText);

            }
            if (fqnString != null) {
                return fqnString;
            }
            return anyString;
        });
    }

    @Override
    public <T extends Chronology<V>, V extends Version> Optional<T> getChronology(int nid) {
        return Optional.ofNullable((T) getEntityFast(nid));
    }

    @Override
    public int nidForUuids(UUID... uuids) {
        return PrimitiveData.get().nidForUuids(uuids);
    }

    @Override
    public int nidForPublicId(PublicId publicId) {
        return PrimitiveData.get().nidForUuids(publicId.asUuidArray());
    }

    @Override
    public <T extends Entity<V>, V extends EntityVersion> T getEntityFast(int nid) {
        return (T) ENTITY_CACHE.get(nid, entityNid -> {
            byte[] bytes = PrimitiveData.get().getBytes(nid);
            if (bytes == null) {
                return null;
            }
            return EntityFactory.make(bytes);
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
                    return EntityFactory.make(bytes);
                }
        );
    }

    @Override
    public void putEntity(Entity entity) {
        ENTITY_CACHE.invalidate(entity.nid());
        STAMP_CACHE.invalidate(entity.nid());
        if (entity instanceof SemanticEntity semanticEntity) {
            PrimitiveData.get().merge(entity.nid(),
                    semanticEntity.patternNid(),
                    semanticEntity.referencedComponentNid(),
                    entity.getBytes(), entity);
        } else {
            PrimitiveData.get().merge(entity.nid(), Integer.MAX_VALUE, Integer.MAX_VALUE, entity.getBytes(), entity);
        }
        processor.onNext(entity);
    }

    @Override
    public void putStamp(StampEntity stampEntity) {
        STAMP_CACHE.invalidate(stampEntity.nid());
        PrimitiveData.get().merge(stampEntity.nid(), Integer.MAX_VALUE, Integer.MAX_VALUE, stampEntity.getBytes(), stampEntity);
    }

    @Override
    public Entity unmarshalChronology(byte[] bytes) {
        return EntityFactory.make(bytes);
    }

    @Override
    public void forEachSemanticOfPattern(int patternNid, Consumer<SemanticEntity> procedure) {
        PrimitiveData.get().forEachSemanticNidOfPattern(patternNid, (int nid) -> procedure.accept(getEntityFast(nid)));
    }

    @Override
    public int[] semanticNidsOfPattern(int patternNid) {
        return PrimitiveData.get().semanticNidsOfPattern(patternNid);
    }

    @Override
    public void forEachSemanticForComponent(int componentNid, Consumer<SemanticEntity> procedure) {
        PrimitiveData.get().forEachSemanticNidForComponent(componentNid, (int nid) -> procedure.accept(getEntityFast(nid)));
    }

    @Override
    public int[] semanticNidsForComponent(int componentNid) {
        return PrimitiveData.get().semanticNidsForComponent(componentNid);
    }

    @Override
    public void forEachSemanticForComponentOfPattern(int componentNid, int patternNid, Consumer<SemanticEntity> procedure) {
        PrimitiveData.get().forEachSemanticNidForComponentOfPattern(componentNid, patternNid, (int nid) -> procedure.accept(getEntityFast(nid)));
    }

    @Override
    public int[] semanticNidsForComponentOfPattern(int componentNid, int patternNid) {
        return PrimitiveData.get().semanticNidsForComponentOfPattern(componentNid, patternNid);
    }

    @Override
    public PublicId publicId(int nid) {
        return getEntityFast(nid).publicId();
    }

    @Override
    public <T extends Chronology<V>, V extends Version> Optional<T> getChronology(PublicId publicId) {
        if (publicId instanceof EntityFacade entityFacade) {
            return Optional.ofNullable((T) getEntityFast(entityFacade.nid()));
        }
        return Optional.ofNullable((T) getEntityFast(nidForPublicId(publicId)));
    }

    @Override
    public <T extends Chronology<V>, V extends Version> void putChronology(T chronology) {
        if (chronology instanceof Entity entity) {
            ENTITY_CACHE.invalidate(entity.nid());
            putEntity(entity);
        } else {
            putEntity(EntityFactory.make(chronology));
            for (Version version : chronology.versions()) {
                Stamp stamp = version.stamp();
                int nid = PrimitiveData.get().nidForUuids(stamp.publicId().asUuidArray());
                if (PrimitiveData.get().getBytes(nid) == null) {
                    putEntity(EntityFactory.make(stamp));
                }
            }
        }
    }

    @AutoService(CachingService.class)
    public static class CacheProvider implements CachingService {

        @Override
        public void reset() {
            STRING_CACHE.invalidateAll();
            ENTITY_CACHE.invalidateAll();
        }
    }
}
