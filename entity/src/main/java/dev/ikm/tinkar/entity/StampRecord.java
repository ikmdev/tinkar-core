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

import dev.ikm.tinkar.terms.KernelTerm;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.Validator;
import dev.ikm.tinkar.component.FieldDataType;
import dev.ikm.tinkar.terms.EntityBinding;
import dev.ikm.tinkar.terms.StampFacade;
import dev.ikm.tinkar.terms.State;
import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.ImmutableLongList;

import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;

import static dev.ikm.tinkar.common.service.PrimitiveData.SCOPED_PATTERN_PUBLICID_FOR_NID;

@RecordBuilder
public record StampRecord(
        long mostSignificantBits, long leastSignificantBits,
        ImmutableLongList additionalUuidLongs, long nid,
        ImmutableList<StampVersionRecord> versions)
        implements StampEntity<StampVersionRecord>, ImmutableEntity<StampVersionRecord>,
                   StampFacade, IdentifierData, StampRecordBuilder.With {

    private static StampRecord nonExistentStamp;
    public StampRecord {
        Validator.notZero(mostSignificantBits);
        Validator.notZero(leastSignificantBits);
        Validator.notZero(nid);
        Objects.requireNonNull(versions);
    }

    /**
     * The non-existent stamp is for indicating that a value of a component has not yet been created at any point in history
     * where the creation and subsequent changes are not visible.
     * TODO: State.PRIMORDIAL to State.PRE_INCEPTION, eliminating PRIMORDIAL?
     * Pre-inception (historically "premundane"): before all recorded time — before Inception
     * @return a stamp that represents that the
     */
    public static StampRecord nonExistentStamp() {
        if (nonExistentStamp == null) {
            nonExistentStamp = StampRecord.make(PrimitiveData.NONEXISTENT_STAMP_UUID, State.PRIMORDIAL,
                    PrimitiveData.PRE_INCEPTION_TIME, KernelTerm.AUTHOR_FOR_VERSION, KernelTerm.UNINITIALIZED_COMPONENT, KernelTerm.UNINITIALIZED_COMPONENT);
        }
        return nonExistentStamp;
    }

    public static StampRecord make(UUID stampUuid, State state, long time, PublicId authorId, PublicId moduleId, PublicId pathId) {
        RecordListBuilder<StampVersionRecord> versionRecords = RecordListBuilder.make();

        // The stamp pattern the providers file and enumerate stamps under, as the loader
        // does: a provider that keys nids by pattern (Rocks) otherwise files a stamp made
        // here where forEachStampNid never looks.
        long stampNid = ScopedValue
                .where(SCOPED_PATTERN_PUBLICID_FOR_NID, EntityBinding.Stamp.pattern())
                .call(() -> PrimitiveData.nid(stampUuid));

        StampRecord stampEntity = new StampRecord(stampUuid.getMostSignificantBits(),
            stampUuid.getLeastSignificantBits(), null, stampNid,
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
                Objects.equals(additionalUuidLongs, that.additionalUuidLongs) &&
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
        sb.append("> ").append(publicId().asUuidList());
        sb.append(" versions=[");
        for (StampEntityVersion version : versions.toReversed()) {
            sb.append(version.describe()).append(", ");
        }
        sb.deleteCharAt(sb.length() - 1);
        sb.deleteCharAt(sb.length() - 1);
        sb.append("]}");
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

    /**
     * This stamp without the uncommitted version its commit superseded. Committing a stamp
     * adds a version at the commit time beside the uncommitted version (time
     * {@link Long#MAX_VALUE}) its transaction began with; the store and change sets keep
     * both, and {@link #lastVersion()} ignores the uncommitted one. An exported file holds
     * committed knowledge, so it writes this form. A stamp with no committed version —
     * still uncommitted, or canceled — is returned as it is.
     *
     * @return this stamp, or its analogue without the superseded uncommitted versions
     */
    public StampRecord withoutSupersededUncommittedVersions() {
        if (versions.noneSatisfy(version -> version.time() == Long.MAX_VALUE)
                || versions.noneSatisfy(version -> version.time() != Long.MAX_VALUE
                        && version.time() != Long.MIN_VALUE)) {
            return this;
        }
        RecordListBuilder<StampVersionRecord> versionRecords = RecordListBuilder.make();
        StampRecord analogue = new StampRecord(mostSignificantBits, leastSignificantBits, additionalUuidLongs, nid, versionRecords);
        for (StampVersionRecord version : versions) {
            if (version.time() != Long.MAX_VALUE) {
                versionRecords.add(new StampVersionRecord(analogue, version.stateNid(), version.time(),
                        version.authorNid(), version.moduleNid(), version.pathNid()));
            }
        }
        versionRecords.build();
        return analogue;
    }

    public StampRecord withAndBuild(StampVersionRecord versionRecord) {
        return analogueBuilder().with(versionRecord).build();
    }

    public StampAnalogueBuilder without(StampVersionRecord versionRecord) {
        return analogueBuilder().without(versionRecord);
    }
}
