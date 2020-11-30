package org.hl7.tinkar.dto;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.binary.*;
import org.hl7.tinkar.component.DefinitionForSemantic;
import org.hl7.tinkar.json.ComponentFieldForJson;
import org.hl7.tinkar.json.JSONObject;
import org.hl7.tinkar.json.JsonChronologyUnmarshaler;
import org.hl7.tinkar.json.JsonMarshalable;

import java.io.IOException;
import java.io.Writer;
import java.util.UUID;

public record DefinitionForSemanticDTO(ImmutableList<UUID> componentUuids)
        implements DefinitionForSemantic, JsonMarshalable, Marshalable {
    private static final int marshalVersion = 1;

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
        try {
            int objectMarshalVersion = in.readInt();
            if (objectMarshalVersion == marshalVersion) {
                ImmutableList<UUID> componentUuids = in.readImmutableUuidList();
                return new DefinitionForSemanticDTO(componentUuids);
            } else {
                throw new UnsupportedOperationException("Unsupported version: " + objectMarshalVersion);
            }
        } catch (IOException ex) {
            throw new MarshalExceptionUnchecked(ex);
        }
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        try {
            out.writeInt(marshalVersion);
            out.writeUuidList(componentUuids);
        } catch (IOException ex) {
            throw new MarshalExceptionUnchecked(ex);
        }
    }
}
