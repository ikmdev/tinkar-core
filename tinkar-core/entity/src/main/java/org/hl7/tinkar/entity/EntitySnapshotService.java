package org.hl7.tinkar.entity;

import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.entity.calculator.LatestVersion;
import org.hl7.tinkar.terms.EntityFacade;

public interface EntitySnapshotService {
    <V extends EntityVersion> LatestVersion<V> getLatest(int nid);

    default <V extends EntityVersion> LatestVersion<V> getLatest(EntityFacade facade) {
        return getLatest(facade.nid());
    }

    default <V extends EntityVersion> LatestVersion<V> getLatest(PublicId publicId) {
        return getLatest(Entity.provider().nidForPublicId(publicId));
    }

}
