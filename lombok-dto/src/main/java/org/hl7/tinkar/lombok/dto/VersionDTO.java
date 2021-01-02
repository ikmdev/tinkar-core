package org.hl7.tinkar.lombok.dto;

import lombok.NonNull;
import lombok.Value;
import lombok.experimental.Accessors;
import lombok.experimental.NonFinal;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.component.Version;

import java.util.Objects;
import java.util.UUID;


@Value
@Accessors(fluent = true)
@NonFinal
public class VersionDTO
        implements DTO, Version {
    @NonNull
    final protected ImmutableList<UUID> componentUuids;
    @NonNull
    final protected StampDTO stamp;

    public VersionDTO(@NonNull ImmutableList<UUID> componentUuids, @NonNull StampDTO stamp) {
        this.componentUuids = componentUuids;
        this.stamp = stamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof VersionDTO)) return false;
        VersionDTO that = (VersionDTO) o;
        return componentUuids.equals(that.componentUuids) && stamp.equals(that.stamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(componentUuids, stamp);
    }
}
