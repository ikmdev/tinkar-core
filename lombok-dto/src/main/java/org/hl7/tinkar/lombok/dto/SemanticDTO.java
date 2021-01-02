package org.hl7.tinkar.lombok.dto;

import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.NonFinal;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.component.Component;
import org.hl7.tinkar.component.DefinitionForSemantic;
import org.hl7.tinkar.component.Semantic;
import org.hl7.tinkar.lombok.dto.binary.*;
import org.hl7.tinkar.lombok.dto.json.ComponentFieldForJson;
import org.hl7.tinkar.lombok.dto.json.JSONObject;
import org.hl7.tinkar.lombok.dto.json.JsonChronologyUnmarshaler;
import org.hl7.tinkar.lombok.dto.json.JsonMarshalable;

import java.io.Writer;
import java.util.Objects;
import java.util.UUID;

@Value
@Accessors(fluent = true)
@NonFinal
@ToString(callSuper = true)
public class SemanticDTO
    extends ComponentDTO
        implements JsonMarshalable, Marshalable, Semantic {
    private static final int localMarshalVersion = 3;

    @NonNull
    protected final ImmutableList<UUID> definitionForSemanticUuids;
    @NonNull
    protected final ImmutableList<UUID> referencedComponentUuids;

    public SemanticDTO(@NonNull ImmutableList<UUID> componentUuids, @NonNull ImmutableList<UUID> definitionForSemanticUuids, @NonNull ImmutableList<UUID> referencedComponentUuids) {
        super(componentUuids);
        this.definitionForSemanticUuids = definitionForSemanticUuids;
        this.referencedComponentUuids = referencedComponentUuids;
    }

    public SemanticDTO(ImmutableList<UUID> componentUuids, DefinitionForSemantic definitionForSemantic, Component referencedComponent) {
        this(componentUuids, definitionForSemantic.componentUuids(), referencedComponent.componentUuids());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SemanticDTO)) return false;
        if (!super.equals(o)) return false;
        SemanticDTO that = (SemanticDTO) o;
        return definitionForSemanticUuids.equals(that.definitionForSemanticUuids) && referencedComponentUuids.equals(that.referencedComponentUuids);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), definitionForSemanticUuids, referencedComponentUuids);
    }

    @Override
    public Component referencedComponent() {
        return new ComponentDTO(referencedComponentUuids);
    }

    @Override
    public DefinitionForSemantic definitionForSemantic() {
        return new DefinitionForSemanticDTO(definitionForSemanticUuids);
    }

    @Override
    public void jsonMarshal(Writer writer) {
        final JSONObject json = new JSONObject();
        json.put(ComponentFieldForJson.CLASS, this.getClass().getCanonicalName());
        json.put(ComponentFieldForJson.COMPONENT_UUIDS, componentUuids());
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
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            ImmutableList<UUID> componentUuids = in.readImmutableUuidList();
            ImmutableList<UUID> definitionForSemanticUuids = in.readImmutableUuidList();
            ImmutableList<UUID> referencedComponentUuids = in.readImmutableUuidList();
            return new SemanticDTO(componentUuids, definitionForSemanticUuids, referencedComponentUuids);
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + marshalVersion);
        }
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        out.writeUuidList(componentUuids());
        out.writeUuidList(definitionForSemanticUuids);
        out.writeUuidList(referencedComponentUuids);
    }
}
