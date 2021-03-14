package org.hl7.tinkar.lombok.dto;

import lombok.NonNull;
import lombok.ToString;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.NonFinal;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.component.TypePatternForSemantic;
import org.hl7.tinkar.lombok.dto.binary.*;
import org.hl7.tinkar.lombok.dto.json.JSONObject;
import org.hl7.tinkar.lombok.dto.json.JsonMarshalable;
import org.hl7.tinkar.lombok.dto.json.ComponentFieldForJson;
import org.hl7.tinkar.lombok.dto.json.JsonChronologyUnmarshaler;

import java.io.Writer;

@Value
@Accessors(fluent = true)
@NonFinal
@ToString(callSuper = true)
public class TypePatternForSemanticDTO
    extends ComponentDTO
        implements TypePatternForSemantic, JsonMarshalable, Marshalable {
    private static final int localMarshalVersion = 3;

    public TypePatternForSemanticDTO(@NonNull PublicId componentPublicId) {
        super(componentPublicId);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TypePatternForSemanticDTO that)) return false;
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
    public static TypePatternForSemanticDTO make(JSONObject jsonObject) {
        PublicId componentPublicId = jsonObject.asPublicId(ComponentFieldForJson.COMPONENT_PUBLIC_ID);
        return new TypePatternForSemanticDTO(componentPublicId);
    }

    @Unmarshaler
    public static TypePatternForSemanticDTO make(TinkarInput in) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            PublicId componentPublicId = in.getPublicId();
            return new TypePatternForSemanticDTO(componentPublicId);
        } else {
            throw new UnsupportedOperationException("Unsupported version: " + marshalVersion);
        }
    }

    @Override
    @Marshaler
    public void marshal(TinkarOutput out) {
        out.putPublicId(publicId());
    }

}
