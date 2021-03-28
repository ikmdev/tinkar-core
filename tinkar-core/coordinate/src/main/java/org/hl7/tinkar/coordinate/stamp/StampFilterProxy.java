package org.hl7.tinkar.coordinate.stamp;

import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.set.primitive.ImmutableIntSet;

public interface StampFilterProxy extends StampFilter, StampFilterTemplateProxy {

    StampFilter getStampFilter();

    @Override
    default int pathNidForFilter() {
        return getStampFilter().pathNidForFilter();
    }

    @Override
    default StateSet allowedStates() {
        return getStampFilter().allowedStates();
    }

    @Override
    default ImmutableIntSet moduleNids() {
        return getStampFilter().moduleNids();
    }

    @Override
    default ImmutableIntList modulePriorityOrder() {
        return getStampFilter().modulePriorityOrder();
    }

    @Override
    default StampPosition stampPosition() {
        return getStampFilter().stampPosition();
    }

    @Override
    default RelativePositionCalculator getRelativePositionCalculator() {
        return getStampFilter().getRelativePositionCalculator();
    }

    @Override
    default StampFilter withAllowedStates(StateSet stateSet) {
        return getStampFilter().withAllowedStates(stateSet);
    }

    @Override
    default StampFilter withStampPositionTime(long stampPositionTime) {
        return getStampFilter().withStampPositionTime(stampPositionTime);
    }

}
