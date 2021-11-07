package org.hl7.tinkar.coordinate;

import org.eclipse.collections.api.set.ImmutableSet;
import org.hl7.tinkar.coordinate.internal.PathServiceFinder;
import org.hl7.tinkar.coordinate.stamp.StampBranchRecord;
import org.hl7.tinkar.coordinate.stamp.StampPathImmutable;
import org.hl7.tinkar.coordinate.stamp.StampPositionRecord;

public interface PathService {

    static PathService get() {
        return PathServiceFinder.INSTANCE.get();
    }

    ImmutableSet<StampBranchRecord> getPathBranches(int pathNid);

    ImmutableSet<StampPathImmutable> getPaths();

    ImmutableSet<StampPositionRecord> getPathOrigins(int pathNid);
}
