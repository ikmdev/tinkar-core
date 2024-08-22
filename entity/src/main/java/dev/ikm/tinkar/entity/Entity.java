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
package dev.ikm.tinkar.entity;

import dev.ikm.tinkar.common.alert.AlertStreams;
import dev.ikm.tinkar.common.id.IntIdSet;
import dev.ikm.tinkar.common.id.IntIds;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.component.Chronology;
import dev.ikm.tinkar.component.Component;
import dev.ikm.tinkar.component.FieldDataType;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.SemanticFacade;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.MutableIntList;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.Optional;

public interface Entity<T extends EntityVersion>
        extends Chronology<T>,
        EntityFacade,
        IdentifierData {

    Logger LOG = LoggerFactory.getLogger(Entity.class);

    static int nid(Component component) {
        return provider().nidForComponent(component);
    }

    static EntityService provider() {
        return EntityService.get();
    }

    static int nid(PublicId publicId) {
        return provider().nidForPublicId(publicId);
    }

    static Optional<ConceptEntity> getConceptForSemantic(SemanticFacade semanticFacade) {
        return getConceptForSemantic(semanticFacade.nid());
    }

    static <V extends EntityVersion> Optional<V> getVersion(int nid, int stampNid) {
        return Optional.ofNullable(getVersionFast(nid, stampNid));
    }

    static <V extends EntityVersion> V getVersionFast(int nid, int stampNid) {
        Entity<EntityVersion> entity = EntityService.get().getEntityFast(nid);
        if (entity != null) {
            for (EntityVersion version : entity.versions()) {
                if (version.stampNid() == stampNid) {
                    return (V) version;
                }
            }
        }
        return null;
    }

    @Override
    ImmutableList<T> versions();

    static Optional<ConceptEntity> getConceptForSemantic(int semanticNid) {
        Optional<? extends Entity<? extends EntityVersion>> optionalEntity = get(semanticNid);
        if (optionalEntity.isPresent()) {
            if (optionalEntity.get() instanceof SemanticEntity semanticEntity) {
                Entity<?> referencedEntity = getFast(semanticEntity.referencedComponentNid());
                if (referencedEntity instanceof ConceptEntity conceptEntity) {
                    return Optional.of(conceptEntity);
                } else if (referencedEntity instanceof SemanticEntity referencedSemantic) {
                    return getConceptForSemantic(referencedSemantic);
                }
            }
        }
        return Optional.empty();
    }

    static <T extends Entity<V>, V extends EntityVersion> Optional<T> get(int nid) {
        return EntityService.get().getEntity(nid);
    }

    static <T extends Entity<V>, V extends EntityVersion> T getOrThrow(int nid) {
        return (T) EntityService.get().getEntity(nid).get();
    }

    static <T extends Entity<V>, V extends EntityVersion> Optional<T> get(EntityFacade facade) {
        return EntityService.get().getEntity(facade.nid());
    }

    static <T extends Entity<V>, V extends EntityVersion> T getOrThrow(EntityFacade facade) {
        return (T) EntityService.get().getEntity(facade.nid()).get();
    }

    static <T extends Entity<V>, V extends EntityVersion> T getFast(int nid) {
        return EntityService.get().getEntityFast(nid);
    }

    static <T extends Entity<V>, V extends EntityVersion> T getFast(EntityFacade facade) {
        return EntityService.get().getEntityFast(facade.nid());
    }

    static <T extends StampEntity<? extends StampEntityVersion>> T getStamp(int nid) {
        return EntityService.get().getStampFast(nid);
    }

    default Optional<T> getVersion(int stampNid) {
        return Optional.ofNullable(getVersionFast(stampNid));
    }

    default T getVersionFast(int stampNid) {
        for (T version : versions()) {
            if (version.stampNid() == stampNid) {
                return version;
            }
        }
        return null;
    }

    default IntIdSet stampNids() {
        MutableIntList stampNids = IntLists.mutable.withInitialCapacity(versions().size());
        for (EntityVersion version : versions()) {
            stampNids.add(version.stampNid());
        }
        return IntIds.set.of(stampNids.toArray());
    }

    byte[] getBytes();

    FieldDataType entityDataType();

    FieldDataType versionDataType();

    default String entityToString() {
        StringBuilder sb = new StringBuilder();
        sb.append(this.getClass().getSimpleName());
        sb.append("{");
        try {
            Optional<String> stringOptional = PrimitiveData.textOptional(this.nid());
            if (stringOptional.isPresent()) {
                sb.append(stringOptional.get());
                sb.append(' ');
            }
        } catch (Throwable t) {
            AlertStreams.dispatchToRoot(t);
        }
        sb.append("<");
        sb.append(nid());
        sb.append("> ");
        sb.append(Arrays.toString(publicId().asUuidArray()));
        sb.append(", ");
        sb.append(entityToStringExtras());
        for (EntityVersion version : versions()) {
            try {
                sb.append("\nv: ").append(version).append(",");
            } catch (Throwable e) {
                LOG.error("Error creating string for <" + version.nid() + ">", e);
                sb.append("<").append(version.nid()).append(">,");
            }
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.append('}');
        return sb.toString();
    }

    int nid();

    @Override
    default PublicId publicId() {
        return IdentifierData.super.publicId();
    }

    default String entityToStringExtras() {
        return "";
    }

    /**
     * @return true if all versions of entity are canceled.
     */
    default boolean canceled() {
        for (EntityVersion v : versions()) {
            if (!v.canceled()) {
                return false;
            }
        }
        return true;
    }

}
