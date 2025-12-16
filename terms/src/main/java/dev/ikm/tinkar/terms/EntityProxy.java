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
package dev.ikm.tinkar.terms;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.component.Component;
import dev.ikm.tinkar.component.Concept;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.LongConsumer;

import static dev.ikm.tinkar.common.service.PrimitiveData.SCOPED_PATTERN_PUBLICID_FOR_NID;

public class EntityProxy implements EntityFacade, PublicId {
    private static final Logger log = LoggerFactory.getLogger(EntityProxy.class);
    /**
     * Universal identifiers for the concept proxied by the this object.
     */
    private volatile ImmutableList<UUID> uuidList;

    private volatile int cachedNid = 0;

    private String description;

    /**
     * Initialization using nid is lazy, and description and UUIDs are only returned if
     * requested.
     *
     * @param nid
     */
    protected EntityProxy(int nid) {
        this.cachedNid = nid;
    }

    protected EntityProxy(String description, UUID[] uuids) {
        this.uuidList = Lists.immutable.of(uuids).toSortedList().toImmutableList();
        this.description = description;
    }

    protected EntityProxy(String description, ImmutableList<UUID> uuidList) {
        this.uuidList = uuidList;
        this.description = description;
    }

    protected EntityProxy(String description, PublicId publicId) {
        this.uuidList = publicId.asUuidList();
        this.description = description;
    }

    private ImmutableList<UUID> getUuidList() {
        ImmutableList<UUID> result = uuidList;
        if (result == null) {
            synchronized (this) {
                result = uuidList;
                if (result == null) {
                    if (cachedNid == 0) {
                        throw new IllegalStateException("Nid and UUIDs not initialized");
                    }
                    result = PrimitiveData.publicId(nid()).asUuidList();
                    uuidList = result;
                }
            }
        }
        return result;
    }

    public UUID[] uuids() {
        return getUuidList().toArray(new UUID[0]);
    }

    public static EntityProxy make(String description, PublicId publicId) {
        return new EntityProxy(description, publicId);
    }

    public static EntityProxy make(int nid) {
        return new EntityProxy(nid);
    }

    public static EntityProxy make(String description, UUID[] uuids) {
        return new EntityProxy(description, uuids);
    }

    public PublicId publicId() {
        return PublicIds.of(getUuidList());
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(nid());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof EntityProxy other) {
            if (this.cachedNid == 0 && other.cachedNid == 0) {
                return this.getUuidList().equals(other.getUuidList());
            }
        }
        if (o instanceof ComponentWithNid componentWithNid) {
            return this.nid() == componentWithNid.nid();
        }
        if (o instanceof PublicId publicId) {
            return PublicId.equals(this.publicId(), publicId);
        }
        if (o instanceof Component component) {
            return PublicId.equals(this.publicId(), component.publicId());
        }
        return false;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{"
                + description() +
                " " + getUuidList() +
                "<" + cachedNid +
                ">}";
    }

    public final String description() {
        if (description == null) {
            description = PrimitiveData.textFast(nid());
        }
        return description;
    }

    @Override
    public final int nid() {
        int result = cachedNid;
        if (result == 0) {
            synchronized (this) {
                result = cachedNid;
                if (result == 0) {
                    ImmutableList<UUID> list = getUuidList();
                    if (this instanceof EntityProxy.Concept) {
                        ScopedValue.where(SCOPED_PATTERN_PUBLICID_FOR_NID, EntityBinding.Concept.pattern().publicId()).run(() ->
                                cachedNid = PrimitiveData.nid(this.publicId()));
                        result = cachedNid;
                    } else {
                        result = PrimitiveData.get().nidForUuids(list.toArray(new UUID[0]));
                        cachedNid = result;
                    }
                }
            }
        }
        return result;
    }

    @Override
    public ImmutableList<UUID> asUuidList() {
        return getUuidList();
    }

    @Override
    public UUID[] asUuidArray() {
        return getUuidList().toArray(new UUID[0]);
    }

    @Override
    public int compareTo(PublicId o) {
        return publicId().compareTo(o);
    }

    @Override
    public boolean contains(UUID uuid) {
        return publicId().contains(uuid);
    }

    @Override
    public int uuidCount() {
        return publicId().uuidCount();
    }

    @Override
    public void forEach(LongConsumer consumer) {
        publicId().forEach(consumer);
    }

    public static class Concept extends EntityProxy implements ConceptFacade {


        private Concept(int conceptNid) {
            super(conceptNid);
        }

        private Concept(String name, UUID... uuids) {
            super(name, uuids);
        }

        private Concept(String name, PublicId publicId) {
            super(name, publicId);
        }

        public static Concept make(ConceptFacade facade) {
            return make(facade.description(), facade.publicId());
        }

        public static Concept make(String name, PublicId publicId) {
            return new Concept(name, publicId);
        }

        public static Concept make(int nid) {
            return new Concept(nid);
        }


        public static Concept make(PublicId publicId) {
            return new Concept(null, publicId);
        }

        public static Concept make(String name, UUID... uuids) {
            return new Concept(name, uuids);
        }


    }

    public static class Pattern extends EntityProxy implements PatternFacade {

        private Pattern(int nid) {
            super(nid);
        }

        private Pattern(String name, UUID... uuids) {
            super(name, uuids);
        }

        private Pattern(String name, PublicId publicId) {
            super(name, publicId);
        }

        public static Pattern make(String name, PublicId publicId) {
            return new Pattern(name, publicId);
        }

        public static Pattern make(PublicId publicId) {
            return new Pattern(null, publicId);
        }

        public static Pattern make(int nid) {
            return new Pattern(nid);
        }

        public static Pattern make(PatternFacade facade) {
            return new Pattern(facade.nid());
        }

        public static Pattern make(String name, UUID... uuids) {
            return new Pattern(name, uuids);
        }

    }

    public static class Semantic extends EntityProxy implements SemanticFacade {


        private Semantic(String name, UUID... uuids) {
            super(name, uuids);
        }

        private Semantic(int nid) {
            super(nid);
        }

        private Semantic(String name, PublicId publicId) {
            super(name, publicId);
        }

        public static Semantic make(String name, PublicId publicId) {
            return new Semantic(name, publicId);
        }

        public static Semantic make(PublicId publicId) {
            return new Semantic(null, publicId);
        }

        public static Semantic make(int nid) {
            return new Semantic(nid);
        }

        public static Semantic make(SemanticFacade semanticFacade) {
            return new Semantic(semanticFacade.nid());
        }

        public static Semantic make(String name, UUID... uuids) {
            return new Semantic(name, uuids);
        }
    }

    public static class Stamp extends EntityProxy implements StampFacade {

        private Stamp(String name, UUID... uuids) {
            super(name, uuids);
        }

        private Stamp(int nid) {
            super(nid);
        }

        private Stamp(String name, PublicId publicId) {
            super(name, publicId);
        }

        public static Stamp make(String name, PublicId publicId) {
            return new Stamp(name, publicId);
        }

        public static Stamp make(PublicId publicId) {
            return new Stamp(null, publicId);
        }

        public static Stamp make(int nid) {
            return new Stamp(nid);
        }

        public static Stamp make(StampFacade stampFacade) {
            return new Stamp(stampFacade.nid());
        }

        public static Stamp make(String name, UUID... uuids) {
            return new Stamp(name, uuids);
        }

    }
}
