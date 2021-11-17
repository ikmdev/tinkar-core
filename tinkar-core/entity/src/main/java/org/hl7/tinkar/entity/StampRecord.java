package org.hl7.tinkar.entity;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.component.Stamp;
import org.hl7.tinkar.terms.State;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

@RecordBuilder
public record StampRecord(
        long mostSignificantBits, long leastSignificantBits,
        long[] additionalUuidLongs, int nid,
        MutableList<StampVersionRecord> versionRecords)
        implements StampEntity<StampVersionRecord>, StampRecordBuilder.With {

    public static StampRecord make(UUID stampUuid, State state, long time, PublicId authorId, PublicId moduleId, PublicId pathId) {
        int initialCapacity = 1;
        if (time == Long.MAX_VALUE) {
            initialCapacity = 2;
        }
        MutableList<StampVersionRecord> versionRecords = Lists.mutable.withInitialCapacity(initialCapacity);
        StampRecord stampEntity = new StampRecord(stampUuid.getMostSignificantBits(),
                stampUuid.getLeastSignificantBits(), null, PrimitiveData.nid(stampUuid),
                versionRecords);

        StampVersionRecord stampVersion = new StampVersionRecord(stampEntity, state.nid(), time, PrimitiveData.nid(authorId),
                PrimitiveData.nid(moduleId), PrimitiveData.nid(pathId));
        versionRecords.add(stampVersion);
        return stampEntity;
    }

    public boolean deepEquals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StampRecord that = (StampRecord) o;
        return mostSignificantBits == that.mostSignificantBits && leastSignificantBits == that.leastSignificantBits && nid == that.nid && Arrays.equals(additionalUuidLongs, that.additionalUuidLongs) && versionRecords.equals(that.versionRecords);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (o instanceof PublicId publicId) {
            return PublicId.equals(this.publicId(), publicId);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(nid);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("StampRecord{");
        sb.append("<").append(nid);
        sb.append("> ").append(publicId().asUuidList()).append(" ");
        for (StampEntityVersion version : versionRecords) {
            sb.append(version.describe());
        }
        sb.append('}');
        return sb.toString();
    }

    @Override
    public byte[] getBytes() {
        return EntityRecordFactory.getBytes(this);
    }

    @Override
    public Stamp stamp() {
        return this;
    }

    @Override
    public StampVersionRecord lastVersion() {
        return (StampVersionRecord) StampEntity.super.lastVersion();
    }

    @Override
    public ImmutableList<StampVersionRecord> versions() {
        return versionRecords.toImmutable();
    }

    @Override
    public FieldDataType entityDataType() {
        return FieldDataType.STAMP;
    }

    @Override
    public FieldDataType versionDataType() {
        return FieldDataType.STAMP_VERSION;
    }

    public StampVersionRecord addVersion(State state, long time, int authorNid, int moduleNid, int pathNid) {
        StampVersionRecord stampVersionRecord = new StampVersionRecord(this, state.nid(), time, authorNid,
                moduleNid, pathNid);
        versionRecords.add(stampVersionRecord);
        return stampVersionRecord;
    }

}
