package org.hl7.tinkar.lombok.dto;

import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.NonFinal;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.component.DefinitionForSemantic;
import org.hl7.tinkar.lombok.dto.binary.*;
import org.hl7.tinkar.lombok.dto.json.ComponentFieldForJson;
import org.hl7.tinkar.lombok.dto.json.JSONObject;
import org.hl7.tinkar.lombok.dto.json.JsonChronologyUnmarshaler;
import org.hl7.tinkar.lombok.dto.json.JsonMarshalable;

import java.io.Writer;
import java.util.UUID;

@Value
@Accessors(fluent = true)
@NonFinal
@ToString(callSuper = true)
public class DefinitionForSemanticDTO
    extends ComponentDTO
        implements DefinitionForSemantic, JsonMarshalable, Marshalable {
    private static final int localMarshalVersion = 3;

    public DefinitionForSemanticDTO(@NonNull ImmutableList<UUID> componentUuids) {
        super(componentUuids);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof DefinitionForSemanticDTO)) return false;
        DefinitionForSemanticDTO that = (DefinitionForSemanticDTO) o;
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
        json.put(ComponentFieldForJson.COMPONENT_UUIDS, componentUuids());
        json.writeJSONString(writer);
    }

    @JsonChronologyUnmarshaler
    public static DefinitionForSemanticDTO make(JSONObject jsonObject) {
        ImmutableList<UUID> componentUuids = jsonObject.asImmutableUuidList(ComponentFieldForJson.COMPONENT_UUIDS);
        return new DefinitionForSemanticDTO(componentUuids);
    }

    @Unmarshaler
    public static DefinitionForSemanticDTO make(TinkarInput in) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            ImmutableList<UUID> componentUuids = in.readImmutableUuidList();
            return new DefinitionForSemanticDTO(componentUuids);
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + marshalVersion);
        }
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        out.writeUuidList(componentUuids());
    }

}
