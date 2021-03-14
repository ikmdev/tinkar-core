package org.hl7.tinkar.coordinate.stamp;

public interface StampBranchProxy extends StampBranch {

    StampBranch getStampBranch();

    @Override
    default long getBranchOriginTime() {
        return getStampBranch().getBranchOriginTime();
    }

    @Override
    default int getPathOfBranchNid() {
        return getStampBranch().getPathOfBranchNid();
    }

    @Override
    default StampBranchImmutable toStampBranchImmutable() {
        return null;
    }
}
