package dev.ikm.tinkar.entity;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.component.FieldDataType;

public interface EntityVersion extends VersionData {
    default int nid() {
        return entity().nid();
    }

    Entity entity();

    int stampNid();

    Entity chronology();

    default PublicId publicId() {
        return entity().publicId();
    }

    default String toXmlFragment() {
        return VersionProxyFactory.toXmlFragment(this);
    }

    default FieldDataType versionDataType() {
        return entity().versionDataType();
    }

}
