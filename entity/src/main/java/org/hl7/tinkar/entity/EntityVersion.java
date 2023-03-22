package org.hl7.tinkar.entity;

import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.component.FieldDataType;

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
