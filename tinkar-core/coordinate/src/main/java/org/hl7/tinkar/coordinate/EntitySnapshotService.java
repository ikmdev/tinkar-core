package org.hl7.tinkar.coordinate;

import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.coordinate.stamp.calculator.Latest;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.EntityVersion;
import org.hl7.tinkar.terms.EntityFacade;

public interface EntitySnapshotService {
    <V extends EntityVersion> Latest<V> getLatest(int nid);

    default <V extends EntityVersion> Latest<V> getLatest(EntityFacade facade) {
        return getLatest(facade.nid());
    }

    default <V extends EntityVersion> Latest<V> getLatest(PublicId publicId) {
        return getLatest(Entity.provider().nidForPublicId(publicId));
    }

}
