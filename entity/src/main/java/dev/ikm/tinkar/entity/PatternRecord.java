package dev.ikm.tinkar.entity;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.collections.api.list.ImmutableList;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.util.Validator;

import java.util.Arrays;
import java.util.Objects;

@RecordBuilder
public record PatternRecord(
        long mostSignificantBits, long leastSignificantBits,
        long[] additionalUuidLongs, int nid,
        ImmutableList<PatternVersionRecord> versions)
        implements PatternEntity<PatternVersionRecord>, PatternRecordBuilder.With {

    public PatternRecord {
        Validator.notZero(mostSignificantBits);
        Validator.notZero(leastSignificantBits);
        Validator.notZero(nid);
        Objects.requireNonNull(versions);
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
        return mostSignificantBits == that.mostSignificantBits &&
                leastSignificantBits == that.leastSignificantBits &&
                nid == that.nid && Arrays.equals(additionalUuidLongs, that.additionalUuidLongs) &&
                versions.equals(that.versions);
    }

    /**
     * If there is a version with the same stamp as versionToAdd, it will be removed prior to adding the
     * new version so you don't get duplicate versions with the same stamp.
     *
     * @param versionToAdd
     * @return PatternAnalogueBuilder
     */

    public PatternAnalogueBuilder with(PatternEntityVersion versionToAdd) {
        return analogueBuilder().add(versionToAdd);
    }

    public PatternAnalogueBuilder analogueBuilder() {
        RecordListBuilder<PatternVersionRecord> versionRecords = RecordListBuilder.make();
        PatternRecord patternRecord = new PatternRecord(mostSignificantBits, leastSignificantBits, additionalUuidLongs,
                nid, versionRecords);
        for (PatternVersionRecord version : versions) {
            versionRecords.add(new PatternVersionRecord(patternRecord, version.stampNid(),
                    version.semanticPurposeNid(), version.semanticMeaningNid(), version.fieldDefinitions()));
        }
        return new PatternAnalogueBuilder(patternRecord, versionRecords);
    }

    public PatternAnalogueBuilder without(PatternEntityVersion versionToAdd) {
        return analogueBuilder().remove(versionToAdd);
    }
}
