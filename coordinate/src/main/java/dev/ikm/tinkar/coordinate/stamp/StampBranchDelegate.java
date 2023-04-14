package dev.ikm.tinkar.coordinate.stamp;

public interface StampBranchDelegate extends StampBranch {

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
    default StampBranchRecord toStampBranchRecord() {
        return null;
    }
}
