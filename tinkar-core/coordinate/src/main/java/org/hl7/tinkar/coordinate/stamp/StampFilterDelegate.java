package org.hl7.tinkar.coordinate.stamp;

import org.hl7.tinkar.common.id.IntIdList;
import org.hl7.tinkar.common.id.IntIdSet;

public interface StampFilterDelegate extends StampFilter {

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
    default StampFilter withAllowedStates(StateSet stateSet) {
        return getStampFilter().withAllowedStates(stateSet);
    }

    @Override
    default StampFilter withStampPositionTime(long stampPositionTime) {
        return getStampFilter().withStampPositionTime(stampPositionTime);
    }

    @Override
    default IntIdSet excludedModuleNids() {
        return getStampFilter().excludedModuleNids();
    }

    @Override
    default StampFilter withStampPosition(StampPositionRecord stampPosition) {
        throw new UnsupportedOperationException();
    }

    @Override
    default StampFilter withModuleNids(IntIdSet moduleNids) {
        throw new UnsupportedOperationException();
    }

    @Override
    default StampFilter withExcludedModuleNids(IntIdSet excludedModuleNids) {
        throw new UnsupportedOperationException();
    }

    @Override
    default StampFilter withModulePriorityNidList(IntIdList modulePriorityNidList) {
        throw new UnsupportedOperationException();
    }
}
