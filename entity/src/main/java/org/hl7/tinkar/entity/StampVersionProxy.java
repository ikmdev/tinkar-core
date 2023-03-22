package org.hl7.tinkar.entity;

public interface StampVersionProxy extends StampVersion {
    @Override
    default int stateNid() {
        return stampVersion().stateNid();
    }

    @Override
    default long time() {
        return stampVersion().time();
    }

    @Override
    default int authorNid() {
        return stampVersion().authorNid();
    }

    @Override
    default int moduleNid() {
        return stampVersion().moduleNid();
    }

    @Override
    default int pathNid() {
        return stampVersion().pathNid();
    }

    StampVersion stampVersion();
}
