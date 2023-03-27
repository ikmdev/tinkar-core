package dev.ikm.tinkar.coordinate.navigation;

import dev.ikm.tinkar.coordinate.stamp.StateSet;
import dev.ikm.tinkar.common.id.IntIdList;
import dev.ikm.tinkar.common.id.IntIdSet;

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
