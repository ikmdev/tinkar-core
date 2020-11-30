package org.hl7.tinkar.dto;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.binary.*;
import org.hl7.tinkar.component.DefinitionForSemantic;
import org.hl7.tinkar.component.IdentifiedThing;
import org.hl7.tinkar.component.Semantic;
import org.hl7.tinkar.json.ComponentFieldForJson;
import org.hl7.tinkar.json.JSONObject;
import org.hl7.tinkar.json.JsonChronologyUnmarshaler;
import org.hl7.tinkar.json.JsonMarshalable;

import java.io.IOException;
import java.io.Writer;
import java.util.UUID;

public record SemanticDTO(ImmutableList<UUID> componentUuids, ImmutableList<UUID> definitionForSemanticUuids,
                          ImmutableList<UUID> referencedComponentUuids)
        implements JsonMarshalable, Marshalable, Semantic {
    private static final int marshalVersion = 1;

    public SemanticDTO(ImmutableList<UUID> componentUuids, DefinitionForSemantic definitionForSemantic, IdentifiedThing referencedComponent) {
        this(componentUuids, definitionForSemantic.componentUuids(), referencedComponent.componentUuids());
    }

    @Override
    public IdentifiedThing referencedComponent() {
        return new IdentifiedThingDTO(referencedComponentUuids);
    }

    @Override
    public DefinitionForSemantic definitionForSemantic() {
        return new DefinitionForSemanticDTO(definitionForSemanticUuids);
    }

    @Override
    public void jsonMarshal(Writer writer) {
        final JSONObject json = new JSONObject();
        json.put(ComponentFieldForJson.CLASS, this.getClass().getCanonicalName());
        json.put(ComponentFieldForJson.COMPONENT_UUIDS, componentUuids);
        json.put(ComponentFieldForJson.DEFINITION_FOR_SEMANTIC_UUIDS, definitionForSemanticUuids);
        json.put(ComponentFieldForJson.REFERENCED_COMPONENT_UUIDS, referencedComponentUuids);
        json.writeJSONString(writer);
    }

    @JsonChronologyUnmarshaler
    public static SemanticDTO make(JSONObject jsonObject) {
        ImmutableList<UUID> componentUuids = jsonObject.asImmutableUuidList(ComponentFieldForJson.COMPONENT_UUIDS);
        ImmutableList<UUID> definitionForSemanticUuids = jsonObject.asImmutableUuidList(ComponentFieldForJson.DEFINITION_FOR_SEMANTIC_UUIDS);
        ImmutableList<UUID> referencedComponentUuids = jsonObject.asImmutableUuidList(ComponentFieldForJson.REFERENCED_COMPONENT_UUIDS);
        return new SemanticDTO(componentUuids, definitionForSemanticUuids, referencedComponentUuids);
    }

    @Unmarshaler
    public static SemanticDTO make(TinkarInput in) {
        try {
            int objectMarshalVersion = in.readInt();
            if (objectMarshalVersion == marshalVersion) {
                ImmutableList<UUID> componentUuids = in.readImmutableUuidList();
                ImmutableList<UUID> definitionForSemanticUuids = in.readImmutableUuidList();
                ImmutableList<UUID> referencedComponentUuids = in.readImmutableUuidList();
                return new SemanticDTO(componentUuids, definitionForSemanticUuids, referencedComponentUuids);
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
            out.writeUuidList(definitionForSemanticUuids);
            out.writeUuidList(referencedComponentUuids);
        } catch (IOException ex) {
            throw new MarshalExceptionUnchecked(ex);
        }
    }
}
