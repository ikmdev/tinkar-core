package org.hl7.tinkar.coordinate.navigation;

import org.hl7.tinkar.common.id.IntIdSet;

public interface NavigationCoordinateDelegate extends NavigationCoordinate {
    NavigationCoordinate navigationCoordinate();

    @Override
    default IntIdSet getNavigationConceptNids() {
        return navigationCoordinate().getNavigationConceptNids();
    }

    @Override
    default NavigationCoordinateRecord toNavigationCoordinateImmutable() {
        return navigationCoordinate().toNavigationCoordinateImmutable();
    }
}
