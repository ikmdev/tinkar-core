package org.hl7.tinkar.coordinate.stamp;

import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.terms.ConceptFacade;

import java.time.Instant;

public interface StampBranch
        extends Comparable<StampBranch> {

    /**
     * Gets the time.
     *
     * @return the time
     */
    long getBranchOriginTime();

    /**
     * Gets the time as instant.
     *
     * @return the time as instant
     */
    default Instant getTimeAsInstant() {
        return Instant.ofEpochMilli(this.getBranchOriginTime());
    }

    /**
     * Compare to.
     *
     * @param o the o
     * @return the int
     */
    @Override
    default int compareTo(StampBranch o) {
        final int comparison = Long.compare(this.getBranchOriginTime(), o.getBranchOriginTime());

        if (comparison != 0) {
            return comparison;
        }

        return Integer.compare(this.getPathOfBranchNid(), o.getPathOfBranchNid());
    }


    int getPathOfBranchNid();

    /**
     * Gets the stamp path ConceptFacade.
     *
     * @return the stamp path ConceptFacade
     */
    default ConceptFacade getPathOfBranchConcept() {
        return Entity.getFast(getPathOfBranchNid());
    }

    StampBranchRecord toStampBranchRecord();

    default String toUserString() {
        final StringBuilder sb = new StringBuilder();


        if (this.getBranchOriginTime() == Long.MAX_VALUE) {
            sb.append("latest");
        } else if (this.getBranchOriginTime() == Long.MIN_VALUE) {
            sb.append("CANCELED");
        } else {
            sb.append(getTimeAsInstant());
        }

        sb.append(" on '")
                .append(PrimitiveData.text(this.getPathOfBranchNid())).append("'");
        return sb.toString();
    }
}
