package org.hl7.tinkar.coordinate.navigation;

import org.hl7.tinkar.common.id.IntIdList;
import org.hl7.tinkar.common.id.IntIdSet;
import org.hl7.tinkar.coordinate.stamp.StateSet;

public interface NavigationCoordinateDelegate extends NavigationCoordinate {
    NavigationCoordinate navigationCoordinate();

    @Override
    default IntIdSet navigationPatternNids() {
        return navigationCoordinate().navigationPatternNids();
    }

    @Override
    default StateSet vertexStates() {
        return navigationCoordinate().vertexStates();
    }

    @Override
    default boolean sortVertices() {
        return navigationCoordinate().sortVertices();
    }

    @Override
    default IntIdList verticesSortPatternNidList() {
        return navigationCoordinate().verticesSortPatternNidList();
    }

    @Override
    default NavigationCoordinateRecord toNavigationCoordinateRecord() {
        return navigationCoordinate().toNavigationCoordinateRecord();
    }
}
