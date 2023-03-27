package dev.ikm.tinkar.coordinate;

import org.eclipse.collections.api.set.ImmutableSet;
import dev.ikm.tinkar.coordinate.internal.PathServiceFinder;
import dev.ikm.tinkar.coordinate.stamp.StampBranchRecord;
import dev.ikm.tinkar.coordinate.stamp.StampPathImmutable;
import dev.ikm.tinkar.coordinate.stamp.StampPositionRecord;

public interface PathService {

    static PathService get() {
        return PathServiceFinder.INSTANCE.get();
    }

    ImmutableSet<StampBranchRecord> getPathBranches(int pathNid);

    ImmutableSet<StampPathImmutable> getPaths();

    ImmutableSet<StampPositionRecord> getPathOrigins(int pathNid);
}
