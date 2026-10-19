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
package dev.ikm.tinkar.entity.transaction;

import dev.ikm.tinkar.common.id.LongIdCollection;
import dev.ikm.tinkar.common.id.LongIds;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.PublicIdentifierRecord;
import dev.ikm.tinkar.entity.RecordListBuilder;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.SemanticRecordBuilder;
import dev.ikm.tinkar.entity.SemanticVersionRecord;
import dev.ikm.tinkar.entity.SemanticVersionRecordBuilder;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import dev.ikm.tinkar.terms.State;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static dev.ikm.tinkar.common.service.PrimitiveData.SCOPED_PATTERN_PUBLICID_FOR_NID;

/**
 * The record a {@link Transaction} keeps in the store (IKE-Network/ike-issues#1308): one semantic
 * of {@link KernelPattern#CHANGE_TRANSACTION_PATTERN}, whose identity is the transaction's UUID
 * and whose referenced component is {@link KernelConcept#CHANGE_TRACKING_MODULE}, so the records
 * gather on bookkeeping no one browses rather than on each author's concept. Every version is
 * stamped by the transaction's author. Each lifecycle event writes a
 * version: opening, a new uncommitted stamp, a moved basis, the store stopping while the
 * transaction is open, and its commit or cancellation. The versions are the transaction's log.
 *
 * <p>Every version is written under a committed stamp of its own in
 * {@link KernelConcept#CHANGE_TRACKING_MODULE}, never under the transaction's uncommitted stamps:
 * a cancelled stamp's versions are withdrawn when a record is read, and a cancelled transaction
 * must leave its record. The stamps are written directly, without a transaction, so writing a
 * record neither records itself nor announces a commit. Their times only increase, so two
 * versions of one record never share a time.
 *
 * <p>A store without the pattern (one built before the starter set declared it) keeps
 * transactions in memory only, as before; that is reported once per store start.
 */
final class TransactionRecords {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(TransactionRecords.class);

    /** The fields of the change transaction pattern, in declared order. */
    private static final int NAME = 0;
    private static final int STATE = 1;
    private static final int BASIS = 2;
    private static final int BASIS_PATH = 3;
    private static final int EXTERNAL_BASIS = 4;
    private static final int COMPONENTS = 5;

    private static final AtomicLong LAST_RECORD_TIME = new AtomicLong();
    private static final AtomicBoolean WARNED = new AtomicBoolean();

    private TransactionRecords() {
    }

    /**
     * What a record says about one transaction, as of its latest version.
     *
     * @param transactionUuid the transaction's UUID, which is the record's identity
     * @param name            the transaction's name
     * @param stateNid        {@code TRANSACTION_OPEN}, {@code TRANSACTION_COMMITTED} or
     *                        {@code TRANSACTION_CANCELLED}
     * @param initiated       when the transaction began: its record's first version
     * @param basisTime       the time of the point its changes were made against
     * @param basisPathNid    the path of that point
     * @param externalBasis   what the changes were made against outside the store, or empty
     * @param authorNid       the transaction's author
     * @param componentNids   the components it wrote, as of the latest version
     */
    record Snapshot(UUID transactionUuid, String name, long stateNid, long initiated, long basisTime, long basisPathNid,
                    String externalBasis, long authorNid, long[] componentNids) {
    }

    /**
     * @return whether the open store holds the change transaction pattern and its vocabulary;
     *         when it does not, that is reported once per store start
     */
    static boolean available() {
        boolean present = present();
        if (!present && WARNED.compareAndSet(false, true)) {
            LOG.warn("This store lacks the change transaction pattern (IKE-Network/ike-issues#1308);"
                    + " open transactions are kept in memory only and do not survive a restart.");
        }
        return present;
    }

    /** @return whether the open store holds the change transaction pattern and its vocabulary */
    static boolean present() {
        boolean present;
        try {
            // Asked of the data store, which is open while the entity service starts and runs.
            present = PrimitiveData.get().hasPublicId(KernelPattern.CHANGE_TRANSACTION_PATTERN.publicId())
                    && PrimitiveData.get().hasPublicId(KernelConcept.CHANGE_TRACKING_MODULE.publicId())
                    && PrimitiveData.get().hasPublicId(KernelConcept.TRANSACTION_OPEN.publicId());
        } catch (IllegalStateException noDataStore) {
            return false;
        }
        return present;
    }

    /** Lets the next store that lacks the pattern be reported again. */
    static void storeStarted() {
        WARNED.set(false);
    }

    /**
     * Writes a version of a transaction's record.
     *
     * @param entities    the entity service to write through: the running one, or the provider
     *                    starting or stopping, which is not yet, or no longer, the running one
     * @param transaction the transaction
     * @param stateNid    its state: open, committed or cancelled
     */
    static void write(EntityService entities, Transaction transaction, long stateNid) {
        long authorNid = transaction.recordAuthorNid();
        long time = nextTime();
        PublicId authorId = PrimitiveData.publicId(authorNid);
        PublicId moduleId = KernelConcept.CHANGE_TRACKING_MODULE.publicId();
        PublicId pathId = PrimitiveData.publicId(transaction.basisPathNid());
        StampEntity<?> stamp = committedStamp(entities, time, authorId, moduleId, pathId);

        MutableValues values = new MutableValues();
        values.set(NAME, transaction.name());
        values.set(STATE, EntityProxy.Concept.make(stateNid));
        values.set(BASIS, Instant.ofEpochMilli(transaction.basisTime()));
        values.set(BASIS_PATH, EntityProxy.Concept.make(transaction.basisPathNid()));
        values.set(EXTERNAL_BASIS, transaction.externalBasis());
        // Only an open transaction lists what it has written, for a restart to rebuild; once it
        // ends, what it wrote is on its stamps, and a cancelled one's components may not travel.
        long[] components = stateNid == KernelConcept.TRANSACTION_OPEN.nid() ? transaction.componentNids() : new long[0];
        values.set(COMPONENTS, LongIds.set.of(components));

        PublicId id = PublicIds.of(transaction.transactionUuid());
        PublicId patternId = KernelPattern.CHANGE_TRANSACTION_PATTERN.publicId();
        long nid = entities.nidForSemantic(patternId, id);
        Object found = entities.getChronology(nid).orElse(null);
        SemanticRecord existing = found instanceof SemanticRecord record ? record : null;
        SemanticRecord written;
        if (existing != null) {
            written = existing.with(new SemanticVersionRecord(existing, stamp.nid(), values.toList())).build();
        } else {
            PublicIdentifierRecord identifier = PublicIdentifierRecord.make(id);
            RecordListBuilder<SemanticVersionRecord> versions = RecordListBuilder.make();
            SemanticRecord semantic = SemanticRecordBuilder.builder()
                    .nid(nid)
                    .mostSignificantBits(identifier.mostSignificantBits())
                    .leastSignificantBits(identifier.leastSignificantBits())
                    .additionalUuidLongs(identifier.additionalUuidLongs())
                    .patternNid(entities.nidForPattern(patternId))
                    .referencedComponentNid(KernelConcept.CHANGE_TRACKING_MODULE.nid())
                    .versions(versions)
                    .build();
            versions.add(SemanticVersionRecordBuilder.builder().chronology(semantic).stampNid(stamp.nid())
                    .fieldValues(values.toList()).build());
            written = SemanticRecordBuilder.builder(semantic).versions(versions.toImmutable()).build();
        }
        entities.putEntity(written);
        transaction.recordedComponents(components.length);
    }

    /**
     * The records of every transaction still open, as of each record's latest version.
     *
     * @param entities the entity service to read through
     * @return the open transactions' snapshots
     */
    static List<Snapshot> open(EntityService entities) {
        List<Snapshot> open = new ArrayList<>();
        if (!present()) {
            return open;
        }
        long patternNid = KernelPattern.CHANGE_TRANSACTION_PATTERN.nid();
        long openNid = KernelConcept.TRANSACTION_OPEN.nid();
        entities.forEachSemanticOfPattern(patternNid, semantic -> {
            SemanticEntityVersion latest = null;
            long latestTime = Long.MIN_VALUE;
            long authorNid = 0;
            long initiated = Long.MAX_VALUE;
            for (SemanticEntityVersion version : semantic.versions()) {
                StampEntity<?> stamp = entities.getStampFast(version.stampNid());
                long time = stamp.time();
                initiated = Math.min(initiated, time);
                if (latest == null || time > latestTime) {
                    latest = version;
                    latestTime = time;
                    authorNid = stamp.authorNid();
                }
            }
            if (latest == null || ((EntityFacade) latest.fieldValues().get(STATE)).nid() != openNid) {
                return;
            }
            ImmutableList<Object> fields = latest.fieldValues();
            open.add(new Snapshot(
                    semantic.publicId().asUuidArray()[0], // first-uuid: a record is minted with the one transaction UUID
                    (String) fields.get(NAME),
                    openNid,
                    initiated,
                    ((Instant) fields.get(BASIS)).toEpochMilli(),
                    ((EntityFacade) fields.get(BASIS_PATH)).nid(),
                    (String) fields.get(EXTERNAL_BASIS),
                    authorNid,
                    ((LongIdCollection) fields.get(COMPONENTS)).toArray()));
        });
        return open;
    }

    /**
     * @param transactionUuid a transaction's UUID
     * @return when its record says it began, or {@link Long#MAX_VALUE} when it has none
     */
    static long initiated(UUID transactionUuid) {
        if (!available()) {
            return Long.MAX_VALUE;
        }
        PublicId id = PublicIds.of(transactionUuid);
        if (!PrimitiveData.get().hasPublicId(id)) {
            return Long.MAX_VALUE;
        }
        if (!(EntityHandle.get(PrimitiveData.nid(id)).orNull() instanceof SemanticRecord record)) {
            return Long.MAX_VALUE;
        }
        long initiated = Long.MAX_VALUE;
        for (SemanticVersionRecord version : record.versions()) {
            initiated = Math.min(initiated, version.stamp().time());
        }
        return initiated;
    }

    /** A time later than every record version written in this JVM, and no earlier than now. */
    private static long nextTime() {
        return LAST_RECORD_TIME.updateAndGet(last -> Math.max(System.currentTimeMillis(), last + 1));
    }

    /** The committed stamp a record version is written under, made if no stamp has its tuple yet. */
    private static StampEntity<?> committedStamp(EntityService entities, long time, PublicId authorId, PublicId moduleId, PublicId pathId) {
        UUID stampUuid = Stamp.stampUuidOf(State.ACTIVE, time, authorId, moduleId, pathId);
        if (PrimitiveData.get().hasUuid(stampUuid)) {
            long stampNid = java.lang.ScopedValue
                    .where(SCOPED_PATTERN_PUBLICID_FOR_NID, dev.ikm.tinkar.terms.EntityBinding.Stamp.pattern().publicId())
                    .call(() -> PrimitiveData.nid(stampUuid));
            Object found = entities.getChronology(stampNid).orElse(null);
            if (found instanceof StampEntity<?> existing) {
                return existing;
            }
        }
        StampRecord stamp = StampRecord.make(stampUuid, State.ACTIVE, time, authorId, moduleId, pathId);
        entities.putEntity(stamp);
        return stamp;
    }

    /** The field values of one version, set by position. */
    private static final class MutableValues {
        private final Object[] values = new Object[COMPONENTS + 1];

        void set(int field, Object value) {
            values[field] = value;
        }

        ImmutableList<Object> toList() {
            return Lists.immutable.of(values);
        }
    }
}
