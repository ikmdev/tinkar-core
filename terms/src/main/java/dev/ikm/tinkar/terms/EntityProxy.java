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
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.component.Component;

import java.util.Arrays;
import java.util.UUID;
import java.util.function.LongConsumer;

public class EntityProxy implements EntityFacade, PublicId {
    /**
     * Universal identifiers for the concept proxied by the this object.
     */
    private UUID[] uuids;

    private int cachedNid = 0;

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
        this.uuids = uuids;
        Arrays.sort(this.uuids);
        this.description = description;
    }

    protected EntityProxy(String description, PublicId publicId) {
        this.uuids = publicId.asUuidArray();
        this.description = description;
    }

    public static EntityProxy make(String description, PublicId publicId) {
        return new EntityProxy(description, publicId.asUuidArray());
    }

    public static EntityProxy make(int nid) {
        return new EntityProxy(nid);
    }

    public static EntityProxy make(String description, UUID[] uuids) {
        return new EntityProxy(description, uuids);
    }

    @Override
    public final PublicId publicId() {
        return this;
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
                return Arrays.equals(this.uuids, other.uuids);
            }
        }
        if (o instanceof ComponentWithNid) {
            return this.nid() == ((ComponentWithNid) o).nid();
        }
        if (o instanceof PublicId) {
            return PublicId.equals(this, (PublicId) o);
        }
        if (o instanceof Component) {
            return PublicId.equals(this, ((Component) o).publicId());
        }
        return false;
    }

    @Override
    public String toString() {
        return this.getClass().getSimpleName() + "{"
                + description() +
                " " + Arrays.toString(asUuidArray()) +
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
    public UUID[] asUuidArray() {
        if (this.uuids == null) {
            this.uuids = PrimitiveData.publicId(nid()).asUuidArray();
        }
        return this.uuids;
    }

    @Override
    public int uuidCount() {
        return asUuidArray().length;
    }

    @Override
    public void forEach(LongConsumer consumer) {
        for (UUID uuid : asUuidArray()) {
            consumer.accept(uuid.getMostSignificantBits());
            consumer.accept(uuid.getLeastSignificantBits());
        }

    }

    @Override
    public final int nid() {
        if (cachedNid == 0) {
            cachedNid = PrimitiveData.get().nidForUuids(uuids);
        }
        return cachedNid;
    }

    public static class Concept extends EntityProxy implements ConceptFacade, PublicId {


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

        public static Semantic make(String name, UUID... uuids) {
            return new Semantic(name, uuids);
        }
    }


}
