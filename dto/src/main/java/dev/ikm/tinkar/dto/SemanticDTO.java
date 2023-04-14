package dev.ikm.tinkar.dto;


import dev.ikm.tinkar.dto.binary.*;
import io.soabase.recordbuilder.core.RecordBuilder;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.component.Semantic;
import dev.ikm.tinkar.dto.binary.*;

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
