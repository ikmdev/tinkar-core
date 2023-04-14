package dev.ikm.tinkar.dto;


import dev.ikm.tinkar.dto.binary.*;
import io.soabase.recordbuilder.core.RecordBuilder;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.component.Pattern;
import dev.ikm.tinkar.dto.binary.*;

@RecordBuilder
public record PatternDTO(PublicId publicId)
        implements Pattern, Marshalable {
    private static final int localMarshalVersion = 3;

    @Unmarshaler
    public static PatternDTO make(TinkarInput in) {
        if (localMarshalVersion == in.getTinkerFormatVersion()) {
            PublicId publicId = in.getPublicId();
            return new PatternDTO(publicId);
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
