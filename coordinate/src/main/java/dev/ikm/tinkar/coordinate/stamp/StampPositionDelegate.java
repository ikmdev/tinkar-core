package dev.ikm.tinkar.coordinate.stamp;


public interface StampPositionDelegate extends StampPosition {
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
    default StampPositionRecord toStampPositionImmutable() {
        return getStampPosition().toStampPositionImmutable();
    }

    @Override
    default StampPosition withTime(long time) {
        throw new UnsupportedOperationException();
    }

    @Override
    default StampPosition withPathForPositionNid(int pathForPositionNid) {
        throw new UnsupportedOperationException();
    }
}
