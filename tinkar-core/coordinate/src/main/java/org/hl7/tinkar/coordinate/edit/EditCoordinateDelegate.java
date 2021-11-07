package org.hl7.tinkar.coordinate.edit;

public interface EditCoordinateDelegate extends EditCoordinate {

    @Override
    default int getAuthorNidForChanges() {
        return getEditCoordinate().getAuthorNidForChanges();
    }

    EditCoordinate getEditCoordinate();

    @Override
    default int getDefaultModuleNid() {
        return getEditCoordinate().getDefaultModuleNid();
    }

    @Override
    default int getDestinationModuleNid() {
        return getEditCoordinate().getDestinationModuleNid();
    }

    @Override
    default int getDefaultPathNid() {
        return getEditCoordinate().getDefaultPathNid();
    }

    @Override
    default int getPromotionPathNid() {
        return getEditCoordinate().getPromotionPathNid();
    }

    @Override
    default EditCoordinateImmutable toEditCoordinateImmutable() {
        return getEditCoordinate().toEditCoordinateImmutable();
    }
}
