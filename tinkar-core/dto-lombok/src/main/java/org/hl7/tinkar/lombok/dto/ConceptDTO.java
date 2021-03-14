package org.hl7.tinkar.lombok.dto;

import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.id.PublicIds;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.lombok.dto.binary.*;
import org.hl7.tinkar.lombok.dto.json.JSONObject;
import org.hl7.tinkar.lombok.dto.json.JsonMarshalable;
import org.hl7.tinkar.lombok.dto.json.ComponentFieldForJson;
import org.hl7.tinkar.lombok.dto.json.JsonChronologyUnmarshaler;

import java.io.Writer;
import java.util.UUID;

@Value
@Accessors(fluent = true)
@NonFinal
@ToString(callSuper = true)
@SuperBuilder
public class ConceptDTO extends ComponentDTO
        implements Concept, DTO, JsonMarshalable, Marshalable {
    private static final int localMarshalVersion = 3;

    public ConceptDTO(@NonNull PublicId componentPublicId) {
        super(componentPublicId);
    }
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ConceptDTO)) return false;
        ConceptDTO that = (ConceptDTO) o;
        return super.equals(that);
    }

    @Override
    public int hashCode() {
        return super.hashCode();
    }

    @Override
    public void jsonMarshal(Writer writer) {
        final JSONObject json = new JSONObject();
        json.put(ComponentFieldForJson.CLASS, this.getClass().getCanonicalName());
        json.put(ComponentFieldForJson.COMPONENT_PUBLIC_ID, publicId());
        json.writeJSONString(writer);
    }

    @JsonChronologyUnmarshaler
    public static ConceptDTO make(JSONObject jsonObject) {
        return new ConceptDTO(jsonObject.asPublicId(ComponentFieldForJson.COMPONENT_PUBLIC_ID));
    }

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
