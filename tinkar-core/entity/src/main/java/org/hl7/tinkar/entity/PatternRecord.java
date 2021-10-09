package org.hl7.tinkar.entity;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.common.id.PublicId;

import java.util.Arrays;
import java.util.Objects;

@RecordBuilder
public record PatternRecord(
        long mostSignificantBits, long leastSignificantBits,
        long[] additionalUuidLongs, int nid,
        MutableList<PatternEntityVersion> versionRecords)
        implements PatternEntity<PatternEntityVersion>, PatternRecordBuilder.With {

    @Override
    public ImmutableList<PatternEntityVersion> versions() {
        return versionRecords.toImmutable();
    }

    @Override
    public byte[] getBytes() {
        return EntityRecordFactory.getBytes(this);
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
        PatternRecord that = (PatternRecord) o;
        return mostSignificantBits == that.mostSignificantBits && leastSignificantBits == that.leastSignificantBits && nid == that.nid && Arrays.equals(additionalUuidLongs, that.additionalUuidLongs) && versionRecords.equals(that.versionRecords);
    }
}
