package org.hl7.tinkar.entity;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;

@RecordBuilder
public record ConceptRecord(
        long mostSignificantBits, long leastSignificantBits,
        long[] additionalUuidLongs, int nid,
        MutableList<ConceptEntityVersion> versionRecords)
        implements ConceptEntity<ConceptEntityVersion>, ConceptRecordBuilder.With {

    @Override
    public byte[] getBytes() {
        return EntityRecordFactory.getBytes(this);
    }

    @Override
    public String toString() {
        return entityToString();
    }

    @Override
    public ImmutableList<ConceptEntityVersion> versions() {
        return versionRecords.toImmutable();
    }
}
