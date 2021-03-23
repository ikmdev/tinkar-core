package org.hl7.tinkar.entity;

import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.component.Component;
import org.hl7.tinkar.entity.internal.Get;

import java.util.*;
import java.util.function.LongConsumer;

public class ConceptProxy implements ConceptFacade, PublicId {
    /**
     * Universal identifiers for the concept proxied by the this object.
     */
    private UUID[] uuids;

    /**
     * The fully qualified name for this object.
     */
    private String name;

    private int cachedNid = 0;

    public ConceptProxy(int conceptNid) {
        this.cachedNid = conceptNid;
    }

    public ConceptProxy(String name, UUID... uuids) {
        this.uuids = uuids;
        this.name = name;
    }

    @Override
    public UUID[] asUuidArray() {
        if (this.uuids == null) {
            this.uuids = Get.entityService().getEntityFast(cachedNid).asUuidArray();
        }
        return this.uuids;
    }

    @Override
    public int uuidCount() {
        if (this.uuids == null) {
            this.uuids = Get.entityService().getEntityFast(cachedNid).asUuidArray();
        }
        return this.uuids.length;
    }

    @Override
    public void forEach(LongConsumer consumer) {
        for (UUID uuid: uuids) {
            consumer.accept(uuid.getMostSignificantBits());
            consumer.accept(uuid.getLeastSignificantBits());
        }

    }

    @Override
    public PublicId publicId() {
        return this;
    }

    @Override
    public int nid() {
        if (cachedNid == 0) {
            try {
                cachedNid = Get.entityService().nidForPublicId(uuids);
            }
            catch (NoSuchElementException e) {
                //This it to help me bootstrap the system... normally, all metadata will be pre-assigned by the IdentifierProvider upon startup.
                throw new NoSuchElementException();
            }
        }
        return cachedNid;
    }

    @Override
    public boolean equals(Object o) {
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
        return Arrays.hashCode(uuids);
    }

    public static ConceptProxy make(int nid) {
        return new ConceptProxy(nid);
    }

    public String name() {
        if (this.name == null) {
            throw new UnsupportedOperationException("Name lookup not yet supported.");
        }
        return this.name;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("ConceptProxy{");
        Optional<String> stringOptional = DefaultDescriptionText.getOptional(nid());
        if (stringOptional.isPresent()) {
            sb.append(stringOptional.get());
            sb.append(' ');
        }
        sb.append("<");
        sb.append(nid());
        sb.append("> ");
        sb.append(Arrays.toString(publicId().asUuidArray()));
        sb.append('}');
        return sb.toString();
    }

}
