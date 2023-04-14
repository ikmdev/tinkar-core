package dev.ikm.tinkar.coordinate.edit;

public interface EditCoordinateDelegate extends EditCoordinate {

    @Override
    default int getAuthorNidForChanges() {
        return editCoordinate().getAuthorNidForChanges();
    }

    EditCoordinate editCoordinate();

    @Override
    default int getDefaultModuleNid() {
        return editCoordinate().getDefaultModuleNid();
    }

    @Override
    default int getDestinationModuleNid() {
        return editCoordinate().getDestinationModuleNid();
    }

    @Override
    default int getDefaultPathNid() {
        return editCoordinate().getDefaultPathNid();
    }

    @Override
    default int getPromotionPathNid() {
        return editCoordinate().getPromotionPathNid();
    }

    @Override
    default EditCoordinateRecord toEditCoordinateRecord() {
        return editCoordinate().toEditCoordinateRecord();
    }
}
