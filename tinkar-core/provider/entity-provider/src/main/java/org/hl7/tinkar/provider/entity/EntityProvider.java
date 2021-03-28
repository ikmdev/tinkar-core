package org.hl7.tinkar.provider.entity;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.activej.bytebuf.ByteBuf;
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

import com.google.auto.service.AutoService;
import org.hl7.tinkar.terms.EntityFacade;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

import static org.hl7.tinkar.terms.TinkarTerm.DESCRIPTION_PATTERN;

@AutoService({EntityService.class, PublicIdService.class, DefaultDescriptionForNidService.class})
public class EntityProvider implements EntityService, PublicIdService, DefaultDescriptionForNidService {

    @AutoService(CachingService.class)
    public static class CacheProvider implements CachingService {

        @Override
        public void reset() {
            STRING_CACHE.invalidateAll();
        }
    }
    private static final Cache<Integer, String> STRING_CACHE = Caffeine.newBuilder().maximumSize(1024).build();


    public EntityProvider() {
        System.out.println("Constructing EntityProvider");
    }

    @Override
    public <T extends Chronology<V>, V extends Version> void putChronology(T chronology) {
        if (chronology instanceof Entity entity) {
            putEntity(entity);
        } else {
            putEntity(EntityFactory.make(chronology));
            for (Version version: chronology.versions()) {
                Stamp stamp = version.stamp();
                int nid = PrimitiveData.get().nidForUuids(stamp.publicId().asUuidArray());
                if (PrimitiveData.get().getBytes(nid) == null) {
                    putEntity(EntityFactory.make(stamp));
                }
            }
        }
    }

    @Override
    public String textFast(int nid) {
        return STRING_CACHE.get(nid, integer -> {
            int[] semanticNids = PrimitiveData.get().semanticNidsForComponentOfType(nid, DESCRIPTION_PATTERN.nid());
            for (int semanticNid: semanticNids) {
                SemanticEntity descripitonSemantic = Entity.getFast(semanticNid);
                SemanticEntityVersion version = descripitonSemantic.versions().get(0);
                for (Object field: version.fields()) {
                    if (field instanceof String stringField) {
                        return stringField;
                    }
                }
            }
            return null;
        });
    }

    @Override
    public <T extends Chronology<V>, V extends Version> Optional<T> getChronology(int nid) {
        return Optional.ofNullable((T) getEntityFast(nid));
    }

    @Override
    public PublicId publicId(int nid) {
        return getEntityFast(nid).publicId();
    }

    @Override
    public <T extends Entity<V>, V extends EntityVersion> T getEntityFast(int nid) {
        byte[] bytes = PrimitiveData.get().getBytes(nid);
        if (bytes == null) {
            return null;
        }
       return EntityFactory.make(bytes);
    }

    @Override
    public StampEntity getStampFast(int nid) {
        byte[] bytes = PrimitiveData.get().getBytes(nid);
        if (bytes == null) {
            return null;
        }
        return EntityFactory.makeStamp(ByteBuf.wrapForReading(bytes));
    }

    @Override
    public void putEntity(Entity entity) {
        if (entity instanceof SemanticEntity semanticEntity) {
            PrimitiveData.get().merge(entity.nid(),
                    semanticEntity.typePatternNid(),
                    semanticEntity.referencedComponentNid(),
                    entity.getBytes());
        } else {
            PrimitiveData.get().merge(entity.nid(), Integer.MAX_VALUE, Integer.MAX_VALUE, entity.getBytes());
        }
    }

    @Override
    public void putStamp(StampEntity stampEntity) {
        PrimitiveData.get().merge(stampEntity.nid(), Integer.MAX_VALUE, Integer.MAX_VALUE, stampEntity.getBytes());
    }

    @Override
    public int nidForUuids(ImmutableList<UUID> uuidList) {
        return PrimitiveData.get().nidForUuids(uuidList);
    }

    @Override
    public int nidForUuids(UUID... uuids) {
        return PrimitiveData.get().nidForUuids(uuids);
    }

    @Override
    public <T extends Chronology<V>, V extends Version> Optional<T> getChronology(PublicId publicId) {
        if (publicId instanceof EntityFacade entityFacade) {
            return Optional.ofNullable((T) getEntityFast(entityFacade.nid()));
        }
        return Optional.ofNullable((T) getEntityFast(nidForPublicId(publicId)));
    }

    @Override
    public int nidForPublicId(PublicId publicId) {
        return PrimitiveData.get().nidForUuids(publicId.asUuidArray());
    }

    @Override
    public Entity unmarshalChronology(byte[] bytes) {
        return EntityFactory.make(bytes);
    }

    @Override
    public void forEachEntityOfType(int typeDefinitionNid, Consumer<SemanticEntity> procedure) {
        PrimitiveData.get().forEachEntityOfType(typeDefinitionNid, (int nid) -> procedure.accept(getEntityFast(nid)));
    }

    @Override
    public int[] entityNidsOfType(int setNid) {
        return PrimitiveData.get().entityNidsOfType(setNid);
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
    public void forEachSemanticForComponentOfType(int componentNid, int typeDefinitionNid, Consumer<SemanticEntity> procedure) {
        PrimitiveData.get().forEachSemanticNidForComponentOfType(componentNid, typeDefinitionNid, (int nid) -> procedure.accept(getEntityFast(nid)));
    }

    @Override
    public int[] semanticNidsForComponentOfType(int componentNid, int typeDefinitionNid) {
        return PrimitiveData.get().semanticNidsForComponentOfType(componentNid, typeDefinitionNid);
    }
}
