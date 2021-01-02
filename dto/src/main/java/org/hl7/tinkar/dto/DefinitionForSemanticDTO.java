package org.hl7.tinkar.dto;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.component.DefinitionForSemantic;
import org.hl7.tinkar.dto.binary.*;
import org.hl7.tinkar.dto.json.ComponentFieldForJson;
import org.hl7.tinkar.dto.json.JSONObject;
import org.hl7.tinkar.dto.json.JsonChronologyUnmarshaler;
import org.hl7.tinkar.dto.json.JsonMarshalable;

import java.io.Writer;
import java.util.UUID;

public record DefinitionForSemanticDTO(ImmutableList<UUID> componentUuids)
        implements DefinitionForSemantic, JsonMarshalable, Marshalable {
    private static final int localMarshalVersion = 3;

    @Override
    public void jsonMarshal(Writer writer) {
        final JSONObject json = new JSONObject();
        json.put(ComponentFieldForJson.CLASS, this.getClass().getCanonicalName());
        json.put(ComponentFieldForJson.COMPONENT_UUIDS, componentUuids);
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
        out.writeUuidList(componentUuids);
    }
}
