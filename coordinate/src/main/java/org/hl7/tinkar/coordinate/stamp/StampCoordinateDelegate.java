package org.hl7.tinkar.coordinate.stamp;

import org.hl7.tinkar.common.id.IntIdList;
import org.hl7.tinkar.common.id.IntIdSet;

public interface StampCoordinateDelegate extends StampCoordinate {

    StampCoordinate getStampFilter();

    @Override
    default int pathNidForFilter() {
        return getStampFilter().pathNidForFilter();
    }

    @Override
    default StateSet allowedStates() {
        return getStampFilter().allowedStates();
    }

    @Override
    default IntIdSet moduleNids() {
        return getStampFilter().moduleNids();
    }

    @Override
    default IntIdList modulePriorityNidList() {
        return getStampFilter().modulePriorityNidList();
    }

    @Override
    default StampPosition stampPosition() {
        return getStampFilter().stampPosition();
    }

    @Override
    default StampCoordinate withAllowedStates(StateSet stateSet) {
        return getStampFilter().withAllowedStates(stateSet);
    }

    @Override
    default StampCoordinate withStampPositionTime(long stampPositionTime) {
        return getStampFilter().withStampPositionTime(stampPositionTime);
    }

    @Override
    default IntIdSet excludedModuleNids() {
        return getStampFilter().excludedModuleNids();
    }

    @Override
    default StampCoordinate withStampPosition(StampPositionRecord stampPosition) {
        throw new UnsupportedOperationException();
    }

    @Override
    default StampCoordinate withModuleNids(IntIdSet moduleNids) {
        throw new UnsupportedOperationException();
    }

    @Override
    default StampCoordinate withExcludedModuleNids(IntIdSet excludedModuleNids) {
        throw new UnsupportedOperationException();
    }

    @Override
    default StampCoordinate withModulePriorityNidList(IntIdList modulePriorityNidList) {
        throw new UnsupportedOperationException();
    }
}
