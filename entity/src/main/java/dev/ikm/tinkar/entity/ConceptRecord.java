/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.entity;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.Validator;
import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.collections.api.list.ImmutableList;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

@RecordBuilder
public record ConceptRecord(
        long mostSignificantBits, long leastSignificantBits,
        long[] additionalUuidLongs, int nid,
        ImmutableList<ConceptVersionRecord> versions)
        implements ConceptEntity<ConceptVersionRecord>, ImmutableEntity<ConceptVersionRecord>, ConceptRecordBuilder.With {


    public ConceptRecord {
        Validator.notZero(mostSignificantBits);
        Validator.notZero(leastSignificantBits);
        Validator.notZero(nid);
        Objects.requireNonNull(versions);
    }

    public static ConceptRecord build(UUID conceptUuid, StampEntityVersion stampVersion) {
        RecordListBuilder<ConceptVersionRecord> versionRecords = RecordListBuilder.make();
        ConceptRecord conceptRecord = ConceptRecordBuilder.builder()
                .leastSignificantBits(conceptUuid.getLeastSignificantBits())
                .mostSignificantBits(conceptUuid.getMostSignificantBits())
                .nid(PrimitiveData.nid(conceptUuid))
                .versions(versionRecords).build();
        versionRecords.addAndBuild(new ConceptVersionRecord(conceptRecord, stampVersion.stampNid()));
        return conceptRecord;
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
        ConceptRecord that = (ConceptRecord) o;
        return mostSignificantBits == that.mostSignificantBits && leastSignificantBits == that.leastSignificantBits &&
                nid == that.nid && Arrays.equals(additionalUuidLongs, that.additionalUuidLongs) &&
                versions.equals(that.versions);
    }

    public ConceptAnalogueBuilder with(ConceptVersionRecord versionToAdd) {
        return analogueBuilder().add(versionToAdd);
    }

    public ConceptAnalogueBuilder analogueBuilder() {
        RecordListBuilder<ConceptVersionRecord> versionRecords = RecordListBuilder.make();
        ConceptRecord conceptRecord = new ConceptRecord(mostSignificantBits, leastSignificantBits, additionalUuidLongs, nid, versionRecords);
        for (ConceptVersionRecord version : versions) {
            versionRecords.add(new ConceptVersionRecord(conceptRecord, version.stampNid()));
        }
        return new ConceptAnalogueBuilder(conceptRecord, versionRecords);
    }

    public ConceptAnalogueBuilder without(ConceptVersionRecord versionToAdd) {
        return analogueBuilder().remove(versionToAdd);
    }

}
