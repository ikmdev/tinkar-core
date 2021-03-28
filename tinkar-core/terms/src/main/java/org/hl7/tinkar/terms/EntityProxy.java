package org.hl7.tinkar.terms;

import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.id.PublicIds;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.component.Component;

import java.util.Arrays;
import java.util.NoSuchElementException;
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
     * @param nid
     */
    protected EntityProxy(int nid) {
        this.cachedNid = nid;
    }
    protected EntityProxy(String description, UUID[] uuids) {
        this.uuids = uuids;
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
    public UUID[] asUuidArray() {
        if (this.uuids == null) {
            this.uuids =PrimitiveData.publicId(nid()).asUuidArray();
        }
        return this.uuids;
    }

    @Override
    public int uuidCount() {
        return asUuidArray().length;
    }

    @Override
    public void forEach(LongConsumer consumer) {
        for (UUID uuid: asUuidArray()) {
            consumer.accept(uuid.getMostSignificantBits());
            consumer.accept(uuid.getLeastSignificantBits());
        }

    }

    @Override
    public final PublicId publicId() {
        return this;
    }

    @Override
    public final boolean equals(Object o) {
        if (this == o) return true;
        if (o instanceof ComponentWithNid) {
            return this.nid() == ((ComponentWithNid)o).nid();
        }
        if (o instanceof PublicId) {
            return PublicId.equals(this, (PublicId) o);
        }
        if  (o instanceof Component) {
            return PublicId.equals(this, ((Component) o).publicId());
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(asUuidArray());
    }

    @Override
    public final int nid() {
        if (cachedNid == 0) {
            try {
                cachedNid = PrimitiveData.get().nidForUuids(uuids);
            } catch (NoSuchElementException e) {
                //This is to help bootstrap the system...
                throw new NoSuchElementException();
            }
        }
        return cachedNid;
    }

    public final String description() {
        if (description == null) {
            description = PrimitiveData.textFast(nid());
        }
        return description;
    }

    @Override
    public final String toString() {
        return this.getClass().getSimpleName() + "{"
                 + description() +
                " " + Arrays.toString(uuids) +
                "<" + cachedNid +
                ">}";
    }
}
