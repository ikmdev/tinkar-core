package org.hl7.tinkar.dto;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.id.PublicIds;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.dto.binary.*;

import java.util.UUID;

@RecordBuilder
public record ConceptDTO(PublicId publicId)
        implements Concept, DTO, Marshalable {
    private static final int localMarshalVersion = 3;

    @Unmarshaler
    public static ConceptDTO make(TinkarInput in) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            PublicId componentPublicId = in.getPublicId();
            return new ConceptDTO(componentPublicId);
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + marshalVersion);
        }
    }

    public static ConceptDTO make(String uuidListString) {
        uuidListString = uuidListString.replace("[", "");
        uuidListString = uuidListString.replace("]", "");
        uuidListString = uuidListString.replace(",", "");
        uuidListString = uuidListString.replace("\"", "");
        String[] uuidStrings = uuidListString.split(" ");
        MutableList<UUID> componentUuids = Lists.mutable.ofInitialCapacity(uuidStrings.length);
        for (String uuidString: uuidStrings) {
            componentUuids.add(UUID.fromString(uuidString));
        }
        return new ConceptDTO(PublicIds.of(componentUuids));
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        out.putPublicId(publicId());
    }
}
