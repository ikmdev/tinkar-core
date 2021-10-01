package org.hl7.tinkar.entity;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;

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
    public String toString() {
        return entityToString();
    }
}
