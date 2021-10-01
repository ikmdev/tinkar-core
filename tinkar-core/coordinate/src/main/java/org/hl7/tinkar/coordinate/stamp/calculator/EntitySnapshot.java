package org.hl7.tinkar.coordinate.stamp.calculator;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.common.id.IntIdCollection;
import org.hl7.tinkar.coordinate.view.calculator.ViewCalculator;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.EntityVersion;

/**
 * TODO: Integrate EntitySnapshot better with ObservableEntitySnapshot
 *
 * @param <V>
 * @author kec
 */
public class EntitySnapshot<V extends EntityVersion> {
    private final Latest<V> latestVersion;
    private final IntIdCollection latestStampIds;
    private final IntIdCollection allStampIds;
    private final Entity<V> entity;
    private final ImmutableList<V> uncommittedVersions;
    private final ImmutableList<V> historicVersions;


    public EntitySnapshot(ViewCalculator viewCalculator, int nid) {
        this(viewCalculator, Entity.provider().getEntityFast(nid));
    }

    public EntitySnapshot(ViewCalculator viewCalculator, Entity<V> entity) {
        this.entity = entity;
        this.latestVersion = viewCalculator.latest(entity);
        if (latestVersion.isPresent()) {
            this.allStampIds = latestVersion.get().entity().stampNids();
            this.latestStampIds = latestVersion.stampNids();
        } else {
            throw new IllegalStateException("No latest value: " + latestVersion);
        }

        MutableList<V> uncommittedVersions = Lists.mutable.empty();
        MutableList<V> historicVersions = Lists.mutable.empty();

        for (V version : this.entity.versions()) {
            if (version.uncommitted()) {
                uncommittedVersions.add(version);
            } else if (!latestStampIds.contains(version.stampNid())) {
                historicVersions.add(version);
            }
        }
        this.uncommittedVersions = uncommittedVersions.toImmutable();
        this.historicVersions = historicVersions.toImmutable();
    }


    //~--- methods -------------------------------------------------------------
    public int nid() {
        return this.entity.nid();
    }

    @Override
    public String toString() {
        return "CategorizedVersions{" + "uncommittedVersions=\n" + uncommittedVersions + "historicVersions=\n" +
                historicVersions + ", latestVersion=\n" + latestVersion +
                ", latestStampSequences=\n" + latestStampIds +
                ", allStampSequences=\n" + allStampIds + '}';
    }

    public ImmutableList<V> getUncommittedVersions() {
        return uncommittedVersions;
    }

    public ImmutableList<V> getHistoricVersions() {
        return historicVersions;
    }

    public Latest<V> getLatestVersion() {
        return latestVersion;
    }

    public VersionCategory getVersionCategory(EntityVersion version) {

        if (version.uncommitted()) {
            return VersionCategory.Uncommitted;
        }

        int stampNid = version.stampNid();

        if (latestStampIds.contains(stampNid)) {
            if (latestVersion.contradictions().isEmpty()) {
                return VersionCategory.UncontradictedLatest;
            }

            return VersionCategory.ContradictedLatest;
        }

        if (this.allStampIds.contains(stampNid)) {
            return VersionCategory.Prior;
        }
        // should never reach here.
        throw new IllegalStateException();
    }
}

