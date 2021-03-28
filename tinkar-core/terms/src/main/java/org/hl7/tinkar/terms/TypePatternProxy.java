package org.hl7.tinkar.entity;

import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.component.TypePattern;
import org.hl7.tinkar.entity.internal.Get;

import java.util.Arrays;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

public class TypePatternProxy implements TypePatternFacade {
    /**
     * Universal identifiers for the concept proxied by the this object.
     */
    private UUID[] uuids;

    private int cachedNid = 0;

    public TypePatternProxy(int nid) {
        this.cachedNid = nid;
    }

    public TypePatternProxy(UUID... uuids) {
        this.uuids = uuids;
    }

    @Override
    public PublicId publicId() {
        return Get.entityService().getEntityFast(nid()).publicId();
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

    public static TypePatternProxy make(int nid) {
        return new TypePatternProxy(nid);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("TypePatternProxy{");
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
