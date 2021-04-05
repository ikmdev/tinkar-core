package org.hl7.tinkar.coordinate.stamp;

import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.coordinate.TimeBasedAnalogMaker;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.terms.ConceptFacade;
import org.hl7.tinkar.terms.State;

import java.util.ArrayList;
import java.util.Collection;
import java.util.UUID;

public interface StampFilter extends StampFilterTemplate, TimeBasedAnalogMaker<StampFilter>, StateBasedAnalogMaker<StampFilter> {

    /**
     * @return a content based uuid, such that identical stamp coordinates
     * will have identical uuids, and that different stamp coordinates will
     * always have different uuids.
     */
    default UUID getStampFilterUuid() {
        ArrayList<UUID> uuidList = new ArrayList<>();
        for (State state: allowedStates().toEnumSet()) {
            Entity.provider().addSortedUuids(uuidList, state.nid());
        }
        Entity.provider().addSortedUuids(uuidList, stampPosition().getPathForPositionNid());
        Entity.provider().addSortedUuids(uuidList, moduleNids().toArray());
        Entity.provider().addSortedUuids(uuidList, modulePriorityOrder().toArray());
        StringBuilder b = new StringBuilder();
        b.append(uuidList.toString());
        b.append(stampPosition().time());
        return UUID.nameUUIDFromBytes(b.toString().getBytes());
    }

    int pathNidForFilter();

    default org.hl7.tinkar.terms.ConceptFacade pathForFilter() {
        return Entity.getFast(pathNidForFilter());
    }

    /**
     * Create a new Filter ImmutableCoordinate identical to the this coordinate, but with the modules modified.
     * @param modules the new modules list.
     * @return the new path coordinate
     */
    StampFilter withModules(Collection<ConceptFacade> modules);

    /**
     * Create a new Filter ImmutableCoordinate identical to the this coordinate, but with the path for position replaced.
     * @param pathForPosition the new path for position
     * @return the new path coordinate
     */
    StampFilter withPath(ConceptFacade pathForPosition);

    /**
     * Gets the stamp position.
     *
     * @return the position (time on a path) that is used to
     * compute what stamped objects versions are the latest with respect to this
     * position.
     */
    StampPosition stampPosition();

    /**
     * @return multi-line string output suitable for presentation to user, as opposed to use in debugging.
     */

    default String toUserString() {
        final StringBuilder builder = new StringBuilder();

        builder.append("allowed states: ");
        builder.append(this.allowedStates().toUserString());

        builder.append("\n   position: ")
                .append(this.stampPosition().toUserString())
                .append("\n   modules: ");

        if (this.moduleNids().isEmpty()) {
            builder.append("all ");
        } else {
            builder.append(PrimitiveData.textList(this.moduleNids().toArray()))
                    .append(" ");
        }

        builder.append("\n   excluded modules: ");

        if (this.excludedModuleNids().isEmpty()) {
            builder.append("none ");
        } else {
            builder.append(PrimitiveData.textList(this.excludedModuleNids().toArray()))
                    .append(" ");
        }

        builder.append("\n   module priorities: ");
        if (this.modulePriorityOrder().isEmpty()) {
            builder.append("none");
        } else {
            builder.append(PrimitiveData.textList(this.modulePriorityOrder().toArray()));
        }

        return builder.toString();
    }

    default StampFilterRecord toStampFilterImmutable() {
        return StampFilterRecord.make(allowedStates(),
                stampPosition());
    }

    default long time() {
        return stampPosition().time();
    }

    StampCalculator stampCalculator();


}
