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

import java.util.UUID;

@RecordBuilder
public record StampRecord(
        long mostSignificantBits, long leastSignificantBits,
        long[] additionalUuidLongs, int nid,
        MutableList<StampEntityVersion> versionRecords)
        implements StampEntity<StampEntityVersion>, StampRecordBuilder.With {

    public static StampRecord make(UUID stampUuid, State state, long time, PublicId authorId, PublicId moduleId, PublicId pathId) {
        int initialCapacity = 1;
        if (time == Long.MAX_VALUE) {
            initialCapacity = 2;
        }
        MutableList<StampEntityVersion> versionRecords = Lists.mutable.withInitialCapacity(initialCapacity);
        StampRecord stampEntity = new StampRecord(stampUuid.getMostSignificantBits(),
                stampUuid.getLeastSignificantBits(), null, PrimitiveData.nid(stampUuid),
                versionRecords);

        StampVersionRecord stampVersion = new StampVersionRecord(stampEntity, state.nid(), time, PrimitiveData.nid(authorId),
                PrimitiveData.nid(moduleId), PrimitiveData.nid(pathId));
        versionRecords.add(stampVersion);
        return stampEntity;
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
        return FieldDataType.STAMP;
    }

    @Override
    public Stamp stamp() {
        return this;
    }

    @Override
    public ImmutableList<StampEntityVersion> versions() {
        return versionRecords.toImmutable();
    }

    public StampVersionRecord addVersion(State state, long time, int authorNid, int moduleNid, int pathNid) {
        StampVersionRecord stampVersionRecord = new StampVersionRecord(this, state.nid(), time, authorNid,
                moduleNid, pathNid);
        versionRecords.add(stampVersionRecord);
        return stampVersionRecord;
    }

    @Override
    public String toString() {
        return "StampEntity{" +
                "<" + nid +
                "> , " + publicId().asUuidList() + " "
                + describe() +
                '}';
    }

}
