package org.hl7.tinkar.lombok.dto;

import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.NonFinal;
import org.hl7.tinkar.common.util.id.PublicId;
import org.hl7.tinkar.component.Version;

import java.util.Objects;


@Value
@Accessors(fluent = true)
@NonFinal
public class VersionDTO
        implements DTO, Version {
    @NonNull
    final protected PublicId publicId;
    @NonNull
    final protected StampDTO stamp;

    public VersionDTO(@NonNull PublicId publicId, @NonNull StampDTO stamp) {
        this.publicId = publicId;
        this.stamp = stamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VersionDTO)) return false;
        VersionDTO that = (VersionDTO) o;
        return publicId.equals(that.publicId) && stamp.equals(that.stamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(publicId, stamp);
    }
}
