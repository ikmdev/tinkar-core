package org.hl7.tinkar.entity;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.service.PrimitiveData;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

@RecordBuilder
public record ConceptRecord(
        long mostSignificantBits, long leastSignificantBits,
        long[] additionalUuidLongs, int nid,
        MutableList<ConceptEntityVersion> versionRecords)
        implements ConceptEntity<ConceptEntityVersion>, ConceptRecordBuilder.With {

    public static ConceptRecord build(UUID conceptUuid, StampEntityVersion stampVersion) {
        MutableList<ConceptEntityVersion> versionRecords = Lists.mutable.withInitialCapacity(1);
        ConceptRecord conceptRecord = ConceptRecordBuilder.builder()
                .leastSignificantBits(conceptUuid.getLeastSignificantBits())
                .mostSignificantBits(conceptUuid.getMostSignificantBits())
                .nid(PrimitiveData.nid(conceptUuid))
                .versionRecords(versionRecords).build();
        versionRecords.add(new ConceptVersionRecord(conceptRecord, stampVersion.stampNid()));
        return conceptRecord;
    }

    @Override
    public byte[] getBytes() {
        return EntityRecordFactory.getBytes(this);
    }

    @Override
    public ImmutableList<ConceptEntityVersion> versions() {
        return versionRecords.toImmutable();
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
        return entityToString();
    }

    public boolean deepEquals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ConceptRecord that = (ConceptRecord) o;
        return mostSignificantBits == that.mostSignificantBits && leastSignificantBits == that.leastSignificantBits && nid == that.nid && Arrays.equals(additionalUuidLongs, that.additionalUuidLongs) && versionRecords.equals(that.versionRecords);
    }
}
