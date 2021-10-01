package org.hl7.tinkar.entity;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.common.service.PrimitiveData;

@RecordBuilder
public record SemanticRecord(
        long mostSignificantBits, long leastSignificantBits,
        long[] additionalUuidLongs, int nid, int patternNid, int referencedComponentNid,
        MutableList<SemanticEntityVersion> versionRecords)
        implements SemanticEntity<SemanticEntityVersion>, SemanticRecordBuilder.With {

    @Override
    public byte[] getBytes() {
        return EntityRecordFactory.getBytes(this);
    }

    @Override
    public String entityToStringExtras() {
        return "of pattern: «" + PrimitiveData.text(patternNid) +
                " <" +
                patternNid +
                "> " + Entity.getFast(patternNid).publicId().asUuidList() +
                "», rc: «" + PrimitiveData.text(referencedComponentNid) +
                " <" +
                referencedComponentNid +
                "> " + Entity.getFast(referencedComponentNid).publicId().asUuidList() +
                "»,";
    }

    @Override
    public String toString() {
        return entityToString();
    }

    @Override
    public ImmutableList<SemanticEntityVersion> versions() {
        return versionRecords.toImmutable();
    }
}
