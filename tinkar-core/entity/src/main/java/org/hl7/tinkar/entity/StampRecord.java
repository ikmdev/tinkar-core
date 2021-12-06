package org.hl7.tinkar.entity;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.terms.State;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

@RecordBuilder
public record StampRecord(
        long mostSignificantBits, long leastSignificantBits,
        long[] additionalUuidLongs, int nid,
        ImmutableList<StampVersionRecord> versions)
        implements StampEntity<StampVersionRecord>, StampRecordBuilder.With {

    public static StampRecord make(UUID stampUuid, State state, long time, PublicId authorId, PublicId moduleId, PublicId pathId) {
        RecordListBuilder<StampVersionRecord> versionRecords = RecordListBuilder.make();
        StampRecord stampEntity = new StampRecord(stampUuid.getMostSignificantBits(),
                stampUuid.getLeastSignificantBits(), null, PrimitiveData.nid(stampUuid),
                versionRecords);

        StampVersionRecord stampVersion = new StampVersionRecord(stampEntity, state.nid(), time, PrimitiveData.nid(authorId),
                PrimitiveData.nid(moduleId), PrimitiveData.nid(pathId));
        versionRecords.add(stampVersion);
        versionRecords.build();
        return stampEntity;
    }

    public boolean deepEquals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StampRecord that = (StampRecord) o;
        return mostSignificantBits == that.mostSignificantBits &&
                leastSignificantBits == that.leastSignificantBits &&
                nid == that.nid &&
                Arrays.equals(additionalUuidLongs, that.additionalUuidLongs) &&
                versions.equals(that.versions);
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
        for (StampEntityVersion version : versions) {
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
    public FieldDataType entityDataType() {
        return FieldDataType.STAMP;
    }

    @Override
    public FieldDataType versionDataType() {
        return FieldDataType.STAMP_VERSION;
    }

    @Override
    public StampEntity<StampVersionRecord> stamp() {
        return this;
    }

    @Override
    public StampVersionRecord lastVersion() {
        return (StampVersionRecord) StampEntity.super.lastVersion();
    }

    public StampAnalogueBuilder with(StampVersionRecord versionRecord) {
        return analogueBuilder().with(versionRecord);
    }

    public StampAnalogueBuilder analogueBuilder() {
        RecordListBuilder<StampVersionRecord> versionRecords = RecordListBuilder.make();
        StampRecord analogueStampRecord = new StampRecord(mostSignificantBits, leastSignificantBits, additionalUuidLongs, nid, versionRecords);
        for (StampVersionRecord version : versions) {
            versionRecords.add(new StampVersionRecord(analogueStampRecord, version.stateNid(), version.time(), version.authorNid(),
                    version.moduleNid(), version.pathNid()));
        }
        return new StampAnalogueBuilder(analogueStampRecord, versionRecords);
    }

    public StampRecord withAndBuild(StampVersionRecord versionRecord) {
        return analogueBuilder().with(versionRecord).build();
    }

    public StampAnalogueBuilder without(StampVersionRecord versionRecord) {
        return analogueBuilder().without(versionRecord);
    }
}
