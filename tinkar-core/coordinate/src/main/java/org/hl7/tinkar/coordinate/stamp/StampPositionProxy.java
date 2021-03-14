package org.hl7.tinkar.coordinate.stamp;


public interface StampPositionProxy extends StampPosition {
    StampPosition getStampPosition();

    @Override
    default long time() {
        return getStampPosition().time();
    }

    @Override
    default int getPathForPositionNid() {
        return getStampPosition().getPathForPositionNid();
    }

    @Override
    default StampPositionImmutable toStampPositionImmutable() {
        return getStampPosition().toStampPositionImmutable();
    }
}
