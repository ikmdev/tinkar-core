package org.hl7.tinkar.entity.calculator;

import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.EntityVersion;
import org.hl7.tinkar.entity.StampEntity;

public interface VersionCalculator<V extends EntityVersion, E extends Entity<V>> {

    LatestVersion<V> getLatestVersion(E chronicle);

    RelativePosition relativePosition(int stampNid, int stampNid2);

    default RelativePosition relativePosition(EntityVersion v1, EntityVersion v2) {
        return relativePosition(v1.stampNid(), v2.stampNid());
    }

    default RelativePosition relativePosition(StampEntity stamp1, StampEntity stamp2) {
        return relativePosition(stamp1.nid(), stamp2.nid());
    }
}
