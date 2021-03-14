package org.hl7.tinkar.entity.graph;

import org.hl7.tinkar.common.util.uuid.UuidT5Generator;
import org.hl7.tinkar.entity.EntityVersion;

import java.util.UUID;

public class VersionVertex<V extends EntityVersion> extends EntityVertex {
    private final V entityVersion;

    protected VersionVertex(V entityVersion) {
        this.entityVersion = entityVersion;
        this.meaningNid = entityVersion.chronology().nid();
        UUID vertexUuid = UuidT5Generator.fromPublicIds(entityVersion.publicId(), entityVersion.stamp().publicId());
        this.mostSignificantBits = vertexUuid.getMostSignificantBits();
        this.leastSignificantBits = vertexUuid.getLeastSignificantBits();
    }

    public V version() {
        return entityVersion;
    }

    public static <V extends EntityVersion> VersionVertex<V> make(V entityVersion) {
        return new VersionVertex<>(entityVersion);
    }
}
