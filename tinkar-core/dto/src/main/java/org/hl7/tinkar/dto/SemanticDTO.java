package org.hl7.tinkar.dto;


import io.soabase.recordbuilder.core.RecordBuilder;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.component.Semantic;
import org.hl7.tinkar.dto.binary.*;

@RecordBuilder
public record SemanticDTO(PublicId publicId)
        implements Marshalable, Semantic {
    private static final int localMarshalVersion = 3;

    @Unmarshaler
    public static SemanticDTO make(TinkarInput in) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            PublicId publicId = in.getPublicId();
            return new SemanticDTO(publicId);
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
