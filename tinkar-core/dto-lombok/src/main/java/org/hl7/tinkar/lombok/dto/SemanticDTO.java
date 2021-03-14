package org.hl7.tinkar.lombok.dto;

import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.NonFinal;
import lombok.experimental.SuperBuilder;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.component.Component;
import org.hl7.tinkar.component.PatternForSemantic;
import org.hl7.tinkar.component.Semantic;
import org.hl7.tinkar.lombok.dto.binary.*;
import org.hl7.tinkar.lombok.dto.json.JSONObject;
import org.hl7.tinkar.lombok.dto.json.JsonMarshalable;
import org.hl7.tinkar.lombok.dto.json.ComponentFieldForJson;
import org.hl7.tinkar.lombok.dto.json.JsonChronologyUnmarshaler;

import java.io.Writer;
import java.util.Objects;

@Value
@Accessors(fluent = true)
@NonFinal
@ToString(callSuper = true)
@SuperBuilder
public class SemanticDTO
    extends ComponentDTO
        implements JsonMarshalable, Marshalable, Semantic {
    private static final int localMarshalVersion = 3;

    @NonNull
    protected final PublicId definitionForSemanticPublicId;
    @NonNull
    protected final PublicId referencedComponentPublicId;

    public SemanticDTO(@NonNull PublicId componentPublicId, @NonNull PublicId definitionForSemanticPublicId, @NonNull PublicId referencedComponentPublicId) {
        super(componentPublicId);
        this.definitionForSemanticPublicId = definitionForSemanticPublicId;
        this.referencedComponentPublicId = referencedComponentPublicId;
    }

    public SemanticDTO(PublicId componentPublicId, PatternForSemantic patternForSemantic, Component referencedComponent) {
        this(componentPublicId, patternForSemantic.publicId(), referencedComponent.publicId());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SemanticDTO)) return false;
        if (!super.equals(o)) return false;
        SemanticDTO that = (SemanticDTO) o;
        return definitionForSemanticPublicId.equals(that.definitionForSemanticPublicId) && referencedComponentPublicId.equals(that.referencedComponentPublicId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), definitionForSemanticPublicId, referencedComponentPublicId);
    }

    @Override
    public Component referencedComponent() {
        return new ComponentDTO(referencedComponentPublicId);
    }

    @Override
    public PatternForSemantic patternForSemantic() {
        return new PatternForSemanticDTO(definitionForSemanticPublicId);
    }

    @Override
    public void jsonMarshal(Writer writer) {
        final JSONObject json = new JSONObject();
        json.put(ComponentFieldForJson.CLASS, this.getClass().getCanonicalName());
        json.put(ComponentFieldForJson.COMPONENT_PUBLIC_ID, publicId());
        json.put(ComponentFieldForJson.PATTERN_FOR_SEMANTIC_PUBLIC_ID, definitionForSemanticPublicId);
        json.put(ComponentFieldForJson.REFERENCED_COMPONENT_PUBLIC_ID, referencedComponentPublicId);
        json.writeJSONString(writer);
    }

    @JsonChronologyUnmarshaler
    public static SemanticDTO make(JSONObject jsonObject) {
        PublicId componentPublicId = jsonObject.asPublicId(ComponentFieldForJson.COMPONENT_PUBLIC_ID);
        PublicId definitionForSemanticPublicId = jsonObject.asPublicId(ComponentFieldForJson.PATTERN_FOR_SEMANTIC_PUBLIC_ID);
        PublicId referencedComponentPublicId = jsonObject.asPublicId(ComponentFieldForJson.REFERENCED_COMPONENT_PUBLIC_ID);
        return new SemanticDTO(componentPublicId, definitionForSemanticPublicId, referencedComponentPublicId);
    }

    @Unmarshaler
    public static SemanticDTO make(TinkarInput in) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            PublicId componentPublicId = in.getPublicId();
            PublicId definitionForSemanticPublicId = in.getPublicId();
            PublicId referencedComponentPublicId = in.getPublicId();
            return new SemanticDTO(componentPublicId, definitionForSemanticPublicId, referencedComponentPublicId);
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + marshalVersion);
        }
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        out.putPublicId(publicId());
        out.putPublicId(definitionForSemanticPublicId);
        out.putPublicId(referencedComponentPublicId);
    }
}
