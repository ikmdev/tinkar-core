package org.hl7.tinkar.coordinate.stamp;

import java.util.ArrayList;
import java.util.Collection;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.UUID;

import org.hl7.tinkar.component.LatestVersion;
import org.hl7.tinkar.component.Version;
import org.hl7.tinkar.entity.DefaultDescriptionText;
import org.hl7.tinkar.coordinate.TimeBasedAnalogMaker;
import org.hl7.tinkar.entity.ConceptEntity;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.component.Concept;

public interface StampFilter extends StampFilterTemplate, TimeBasedAnalogMaker<StampFilter>, StateBasedAnalogMaker<StampFilter> {

    /**
     * @return a content based uuid, such that identical stamp coordinates
     * will have identical uuids, and that different stamp coordinates will
     * always have different uuids.
     */
    default UUID getStampFilterUuid() {
        ArrayList<UUID> uuidList = new ArrayList<>();
        for (State state: getAllowedStates().toEnumSet()) {
            Entity.provider().addSortedUuids(uuidList, state.nid());
        }
        Entity.provider().addSortedUuids(uuidList, getStampPosition().getPathForPositionNid());
        Entity.provider().addSortedUuids(uuidList, getModuleNids().toArray());
        Entity.provider().addSortedUuids(uuidList, getModulePriorityOrder().toArray());
        StringBuilder b = new StringBuilder();
        b.append(uuidList.toString());
        b.append(getStampPosition().time());
        return UUID.nameUUIDFromBytes(b.toString().getBytes());
    }

    int getPathNidForFilter();

    default Concept getPathConceptForFilter() {
        return Entity.getFast(getPathNidForFilter());
    }

    /**
     * Create a new Filter ImmutableCoordinate identical to the this coordinate, but with the modules modified.
     * @param modules the new modules list.
     * @param add - true, if the modules parameter should be appended to the existing modules, false if the 
     * supplied modules should replace the existing modules
     * @return the new path coordinate
     */
    StampFilter makeModuleAnalog(Collection<ConceptEntity> modules, boolean add);

    /**
     * Create a new Filter ImmutableCoordinate identical to the this coordinate, but with the path for position replaced.
     * @param pathForPosition the new path for position
     * @return the new path coordinate
     */
    StampFilter makePathAnalog(ConceptEntity pathForPosition);

    /**
     * Gets the stamp position.
     *
     * @return the position (time on a path) that is used to
     * compute what stamped objects versions are the latest with respect to this
     * position.
     */
    StampPosition getStampPosition();

    /**
     * @return multi-line string output suitable for presentation to user, as opposed to use in debugging.
     */

    default String toUserString() {
        final StringBuilder builder = new StringBuilder();

        builder.append("   allowed states: ");
        builder.append(this.getAllowedStates().toUserString());

        builder.append("\n   position: ")
                .append(this.getStampPosition().toUserString())
                .append("\n   modules: ");

        if (this.getModuleNids().isEmpty()) {
            builder.append("all ");
        } else {
            builder.append(DefaultDescriptionText.getList(this.getModuleNids().toArray()))
                    .append(" ");
        }

        builder.append("\n   excluded modules: ");

        if (this.getExcludedModuleNids().isEmpty()) {
            builder.append("none ");
        } else {
            builder.append(DefaultDescriptionText.getList(this.getExcludedModuleNids().toArray()))
                    .append(" ");
        }

        builder.append("\n   module priorities: ");
        if (this.getModulePriorityOrder().isEmpty()) {
            builder.append("none ");
        } else {
            builder.append(DefaultDescriptionText.getList(this.getModulePriorityOrder().toArray()))
                    .append(" ");
        }

        return builder.toString();
    }

    default StampFilterImmutable toStampFilterImmutable() {
        return StampFilterImmutable.make(getAllowedStates(),
                getStampPosition());
    }

    default long getTime() {
        return getStampPosition().time();
    }

    default Instant getTimeAsInstant() {
        return getStampPosition().instant();
    }

    RelativePositionCalculator getRelativePositionCalculator();

    default LatestVersion<Version> latestConceptVersion(int conceptNid) {
        try {
            throw new UnsupportedOperationException();
//
//            return Entity.provider().getEntityFast(conceptNid).getLatestVersion(this);
        } catch (NoSuchElementException e) {
            return new LatestVersion<>();
        }
    }

}
