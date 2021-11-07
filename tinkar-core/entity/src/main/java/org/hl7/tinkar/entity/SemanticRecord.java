package org.hl7.tinkar.entity;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.terms.PatternFacade;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

@RecordBuilder
public record SemanticRecord(
        long mostSignificantBits, long leastSignificantBits,
        long[] additionalUuidLongs, int nid, int patternNid, int referencedComponentNid,
        MutableList<SemanticEntityVersion> versionRecords)
        implements SemanticEntity<SemanticEntityVersion>, SemanticRecordBuilder.With {

    public static SemanticRecord makeNew(PublicId publicId, PatternFacade patternFacade, int referencedComponentNid) {
        return makeNew(publicId, patternFacade.nid(), referencedComponentNid);
    }

    public static SemanticRecord makeNew(PublicId publicId, int patternNid, int referencedComponentNid) {
        PublicIdentifierRecord publicIdRecord = PublicIdentifierRecord.make(publicId);
        int nid = PrimitiveData.nid(publicId);
        return new SemanticRecord(publicIdRecord.mostSignificantBits(), publicIdRecord.leastSignificantBits(),
                publicIdRecord.additionalUuidLongs(), nid, patternNid, referencedComponentNid,
                Lists.mutable.ofInitialCapacity(1));
    }

    public static SemanticRecord build(UUID semanticUuid,
                                       int patternNid,
                                       int referencedComponentNid,
                                       StampEntityVersion stampVersion,
                                       ImmutableList<Object> fields) {
        MutableList<SemanticEntityVersion> versionRecords = Lists.mutable.withInitialCapacity(1);
        SemanticRecord semanticRecord = SemanticRecordBuilder.builder()
                .leastSignificantBits(semanticUuid.getLeastSignificantBits())
                .mostSignificantBits(semanticUuid.getMostSignificantBits())
                .nid(PrimitiveData.nid(semanticUuid))
                .patternNid(patternNid)
                .referencedComponentNid(referencedComponentNid)
                .versionRecords(versionRecords).build();
        versionRecords.add(new SemanticVersionRecord(semanticRecord, stampVersion.stampNid(), fields));
        return semanticRecord;
    }

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

    public boolean deepEquals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SemanticRecord that = (SemanticRecord) o;
        return mostSignificantBits == that.mostSignificantBits && leastSignificantBits == that.leastSignificantBits && nid == that.nid && patternNid == that.patternNid && referencedComponentNid == that.referencedComponentNid && Arrays.equals(additionalUuidLongs, that.additionalUuidLongs) && versionRecords.equals(that.versionRecords);
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

    @Override
    public ImmutableList<SemanticEntityVersion> versions() {
        return versionRecords.toImmutable();
    }
}
