package org.hl7.tinkar.coordinate.stamp;

import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.set.primitive.ImmutableIntSet;

public interface StampFilterProxy extends StampFilter, StampFilterTemplateProxy {

    StampFilter getStampFilter();

    @Override
    default int getPathNidForFilter() {
        return getStampFilter().getPathNidForFilter();
    }

    @Override
    default StateSet getAllowedStates() {
        return getStampFilter().getAllowedStates();
    }

    @Override
    default ImmutableIntSet getModuleNids() {
        return getStampFilter().getModuleNids();
    }

    @Override
    default ImmutableIntList getModulePriorityOrder() {
        return getStampFilter().getModulePriorityOrder();
    }

    @Override
    default StampPosition getStampPosition() {
        return getStampFilter().getStampPosition();
    }

    @Override
    default RelativePositionCalculator getRelativePositionCalculator() {
        return getStampFilter().getRelativePositionCalculator();
    }

    @Override
    default StampFilter makeCoordinateAnalog(StateSet stateSet) {
        return getStampFilter().makeCoordinateAnalog(stateSet);
    }

    @Override
    default StampFilter makeCoordinateAnalog(long stampPositionTime) {
        return getStampFilter().makeCoordinateAnalog(stampPositionTime);
    }

}
