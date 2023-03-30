package dev.ikm.tinkar.entity.graph;

import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.EntityVersion;

import java.util.UUID;

/**
 * Used when constructing a tree of versions to return from relative position calculator method
 * getVersionGraphList. This is not the more general EntityVertex which is a property graph node.
 * TODO consider if we need this class, or if the entty vertex can be used instead.
 * Might require model data concepts specific to the version graphs of the relative position calculator.
 *
 * @param <V>
 */
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
