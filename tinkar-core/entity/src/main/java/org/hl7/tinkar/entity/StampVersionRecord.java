package org.hl7.tinkar.entity;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.util.time.DateTimeUtil;
import org.hl7.tinkar.component.Stamp;

/**
 * Maybe handle commit by listening to commit reactive stream, and if version is committed, add it to the chronology?
 */
@RecordBuilder
public record StampVersionRecord(StampRecord chronology,
                                 int stateNid, long time, int authorNid,
                                 int moduleNid,
                                 int pathNid) implements StampEntityVersion, StampVersionRecordBuilder.With {

    public StampVersionRecord(StampRecord chronology,
                              StampVersion version) {
        this(chronology,
                EntityService.get().nidForComponent(version.state()),
                version.time(),
                EntityService.get().nidForComponent(version.author()),
                EntityService.get().nidForComponent(version.module()),
                EntityService.get().nidForComponent(version.path()));
    }

    public StampVersionRecord(StampRecord chronology,
                              Stamp version) {
        this(chronology,
                EntityService.get().nidForComponent(version.state()),
                version.time(),
                EntityService.get().nidForComponent(version.author()),
                EntityService.get().nidForComponent(version.module()),
                EntityService.get().nidForComponent(version.path()));
    }

    @Override
    public StampRecord entity() {
        return chronology;
    }

    @Override
    public int stampNid() {
        return chronology.nid();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StampVersionRecord that = (StampVersionRecord) o;
        return stateNid == that.stateNid && time == that.time && authorNid == that.authorNid && moduleNid == that.moduleNid && pathNid == that.pathNid;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(nid());
    }

    @Override
    public String toString() {
        return "sv: ≤" + describe() + "≥";
    }

    public String describe() {
        return "s:" + PrimitiveData.text(stateNid()) +
                " t:" + DateTimeUtil.format(time()) +
                " a:" + PrimitiveData.text(authorNid()) +
                " m:" + PrimitiveData.text(moduleNid()) +
                " p:" + PrimitiveData.text(pathNid());
    }

    @Override
    public StampEntity stamp() {
        return chronology;
    }

}
