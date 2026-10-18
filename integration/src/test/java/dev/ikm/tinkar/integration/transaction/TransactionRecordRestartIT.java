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
package dev.ikm.tinkar.integration.transaction;

import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ServiceKeys;
import dev.ikm.tinkar.common.service.ServiceProperties;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.entity.ConceptRecord;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.fixtures.ForkedJvm;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.fixtures.TestTags;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.State;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * An open transaction survives a restart (IKE-Network/ike-issues#1308). A transaction that
 * writes under an uncommitted stamp keeps a record in the store, a semantic of the change
 * transaction pattern, written on each lifecycle event under committed stamps of its own. When
 * the store opens again the transaction is rebuilt from its record, before uncommitted stamps
 * outside a transaction are cancelled, so its pending versions keep their owner and can still be
 * committed. A committed or cancelled transaction leaves its record and is not rebuilt; a
 * transaction written in a load phase keeps none. Each store lifetime runs in a JVM of its own.
 */
@Tag(TestTags.STAGED)
class TransactionRecordRestartIT {

    private static final File WORK = TestConstants.createFilePathInTargetFromClassName.apply(TransactionRecordRestartIT.class);
    private static final String STORE = "store";

    static final UUID PENDING_CONCEPT = UUID.nameUUIDFromBytes("transaction-record-pending-concept".getBytes());
    static final UUID COMMITTED_CONCEPT = UUID.nameUUIDFromBytes("transaction-record-committed-concept".getBytes());
    static final UUID CANCELLED_CONCEPT = UUID.nameUUIDFromBytes("transaction-record-cancelled-concept".getBytes());
    static final UUID LOADED_CONCEPT = UUID.nameUUIDFromBytes("transaction-record-loaded-concept".getBytes());
    static final UUID SAVED_CONCEPT = UUID.nameUUIDFromBytes("transaction-record-saved-concept".getBytes());

    @Test
    void anOpenTransactionIsRebuiltAfterARestartAndCanStillCommit() {
        FileUtil.recursiveDelete(WORK);
        assertTrue(WORK.mkdirs(), "Could not create " + WORK);
        Properties in = new Properties();
        in.setProperty(STORE, new File(WORK, "store").getPath());

        Properties written = ForkedJvm.run(Write.class, in);
        assertEquals("true", written.getProperty("pending.recorded"), "a transaction with an uncommitted stamp keeps a record");
        assertEquals("false", written.getProperty("loaded.recorded"), "a transaction in a load phase keeps none");

        Properties reopened = ForkedJvm.run(Reopen.class, written);
        assertEquals("true", reopened.getProperty("pending.rebuilt"), "the open transaction is rebuilt");
        assertEquals("A pending edit", reopened.getProperty("pending.name"));
        assertEquals("on commit abc123", reopened.getProperty("pending.external"), "its external basis survives");
        assertEquals(written.getProperty("pending.basis"), reopened.getProperty("pending.basis"), "its basis survives");
        assertEquals("true", reopened.getProperty("pending.owns.version"), "it owns its pending version again");
        assertEquals("true", reopened.getProperty("pending.lists.component"), "it lists the component it wrote");
        assertEquals("true", reopened.getProperty("pending.version.uncommitted"),
                "the pending version is still uncommitted, though stamps outside a transaction were cancelled");
        assertTrue(Long.parseLong(reopened.getProperty("pending.initiated")) >= Long.parseLong(written.getProperty("pending.basis")),
                "it began no earlier than its basis");
        assertEquals("false", reopened.getProperty("committed.rebuilt"), "a committed transaction is not rebuilt");
        assertEquals(KernelConcept.TRANSACTION_COMMITTED.publicId().asUuidArray()[0].toString(), reopened.getProperty("committed.state"));
        assertEquals("false", reopened.getProperty("cancelled.rebuilt"), "a cancelled transaction is not rebuilt");
        assertEquals(KernelConcept.TRANSACTION_CANCELLED.publicId().asUuidArray()[0].toString(), reopened.getProperty("cancelled.state"),
                "a cancelled transaction leaves its record");
        assertEquals("false", reopened.getProperty("loaded.rebuilt"));
        assertEquals("true", reopened.getProperty("loaded.version.cancelled"),
                "the load phase's unrecorded uncommitted stamp is cancelled at startup, as before");
        assertEquals("true", reopened.getProperty("open.lists.rebuilt"),
                "the open transactions include the rebuilt ones, for a person to review");
        assertEquals("true", reopened.getProperty("pending.owner.found"),
                "a writer under the pending version's tuple finds the rebuilt transaction to continue");
        assertEquals("false", reopened.getProperty("pending.owner.other.author"),
                "a writer under another author's tuple does not take it over");
        assertEquals("1", reopened.getProperty("pending.committed.stamps"), "the rebuilt transaction commits its stamp");
        assertEquals("true", reopened.getProperty("saved.lists.component"),
                "a component written after a transaction's last record version reaches its record when the store is saved");

        Properties settled = ForkedJvm.run(Settled.class, reopened);
        assertEquals("false", settled.getProperty("pending.rebuilt"), "once committed, it is not rebuilt again");
        assertEquals(KernelConcept.TRANSACTION_COMMITTED.publicId().asUuidArray()[0].toString(), settled.getProperty("pending.state"));
        assertEquals("true", settled.getProperty("pending.version.committed"), "its version stays committed");
    }

    /** Writes a pending, a committed, a cancelled and a load-phase transaction, then stops the store. */
    static class Write implements ForkedJvm.Stage {
        @Override
        public void run(Properties in, Properties out) {
            out.putAll(in);
            TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, new File(in.getProperty(STORE)));
            try {
                TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);

                Transaction pending = Transaction.make("A pending edit");
                write(pending, PENDING_CONCEPT);
                pending.externalBasis("on commit abc123");
                out.setProperty("pending.uuid", pending.transactionUuid().toString());
                out.setProperty("pending.recorded", Boolean.toString(pending.recorded()));
                out.setProperty("pending.basis", Long.toString(pending.basisTime()));

                // Its record's only version is written before it writes its component, so the
                // component reaches the record when the store is saved before it stops.
                Transaction saved = Transaction.make("Recorded as the store is saved");
                write(saved, SAVED_CONCEPT);
                out.setProperty("saved.uuid", saved.transactionUuid().toString());

                Transaction committed = Transaction.make("A committed edit");
                write(committed, COMMITTED_CONCEPT);
                committed.commit();
                out.setProperty("committed.uuid", committed.transactionUuid().toString());

                Transaction cancelled = Transaction.make("A cancelled edit");
                write(cancelled, CANCELLED_CONCEPT);
                cancelled.cancel();
                out.setProperty("cancelled.uuid", cancelled.transactionUuid().toString());

                Transaction loaded = Transaction.make("Written in a load phase");
                EntityService.get().beginLoadPhase();
                try {
                    write(loaded, LOADED_CONCEPT);
                } finally {
                    EntityService.get().endLoadPhase();
                }
                out.setProperty("loaded.uuid", loaded.transactionUuid().toString());
                out.setProperty("loaded.recorded", Boolean.toString(loaded.recorded()));
            } finally {
                TestHelper.stopDatabase();
            }
        }

        private static void write(Transaction transaction, UUID concept) {
            StampEntity<?> stamp = transaction.getStamp(State.ACTIVE, KernelConcept.USER,
                    KernelConcept.PRIMORDIAL_MODULE, KernelConcept.DEVELOPMENT_PATH);
            ConceptRecord record = ConceptRecord.build(concept, stamp.lastVersion());
            transaction.addComponent(record);
            EntityService.get().putEntity(record);
        }
    }

    /** Reopens the store, cancelling uncommitted stamps outside a transaction, and commits the rebuilt one. */
    static class Reopen implements ForkedJvm.Stage {
        @Override
        public void run(Properties in, Properties out) {
            out.putAll(in);
            ServiceProperties.set(ServiceKeys.CANCEL_UNCOMMITTED_STAMPS_AT_STARTUP, Boolean.TRUE);
            TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, new File(in.getProperty(STORE)));
            try {
                UUID pendingUuid = UUID.fromString(in.getProperty("pending.uuid"));
                Transaction pending = Transaction.forUuid(pendingUuid).orElse(null);
                out.setProperty("pending.rebuilt", Boolean.toString(pending != null));
                EntityVersion version = latest(PENDING_CONCEPT);
                if (pending != null) {
                    out.setProperty("pending.name", pending.name());
                    out.setProperty("pending.external", pending.externalBasis());
                    out.setProperty("pending.basis", Long.toString(pending.basisTime()));
                    out.setProperty("pending.initiated", Long.toString(pending.initiated()));
                    out.setProperty("pending.lists.component", Boolean.toString(listsComponent(pending, version.nid())));
                    out.setProperty("pending.owns.version", Boolean.toString(
                            Transaction.forVersion(version).filter(pending::equals).isPresent()));
                }
                out.setProperty("pending.version.uncommitted", Boolean.toString(version.uncommitted()));

                Transaction saved = Transaction.forUuid(UUID.fromString(in.getProperty("saved.uuid"))).orElseThrow();
                out.setProperty("saved.lists.component", Boolean.toString(
                        listsComponent(saved, EntityHandle.get(PublicIds.of(SAVED_CONCEPT)).expectConcept().nid())));
                rebuiltAndState(out, "committed", UUID.fromString(in.getProperty("committed.uuid")));
                rebuiltAndState(out, "cancelled", UUID.fromString(in.getProperty("cancelled.uuid")));
                out.setProperty("loaded.rebuilt", Boolean.toString(
                        Transaction.forUuid(UUID.fromString(in.getProperty("loaded.uuid"))).isPresent()));
                // A cancelled version is withdrawn when its record is read.
                out.setProperty("loaded.version.cancelled", Boolean.toString(
                        EntityHandle.get(PublicIds.of(LOADED_CONCEPT)).expectConcept().versions().isEmpty()));

                long pendingNid = EntityHandle.get(PublicIds.of(PENDING_CONCEPT)).expectConcept().nid();
                out.setProperty("open.lists.rebuilt", Boolean.toString(pending != null
                        && Transaction.openTransactions().contains(pending)));
                out.setProperty("pending.owner.found", Boolean.toString(Transaction.rebuiltOwnerOfPendingVersion(pendingNid,
                        State.ACTIVE, KernelConcept.USER.nid(), KernelConcept.PRIMORDIAL_MODULE.nid(),
                        KernelConcept.DEVELOPMENT_PATH.nid()).filter(owner -> owner == pending).isPresent()));
                out.setProperty("pending.owner.other.author", Boolean.toString(Transaction.rebuiltOwnerOfPendingVersion(pendingNid,
                        State.ACTIVE, KernelConcept.KOMET_USER.nid(), KernelConcept.PRIMORDIAL_MODULE.nid(),
                        KernelConcept.DEVELOPMENT_PATH.nid()).isPresent()));
                if (pending != null) {
                    out.setProperty("pending.committed.stamps", Integer.toString(pending.commit()));
                }
            } finally {
                TestHelper.stopDatabase();
            }
        }
    }

    /** Reopens the store once more: the committed transaction stays settled. */
    static class Settled implements ForkedJvm.Stage {
        @Override
        public void run(Properties in, Properties out) {
            out.putAll(in);
            TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, new File(in.getProperty(STORE)));
            try {
                rebuiltAndState(out, "pending", UUID.fromString(in.getProperty("pending.uuid")));
                out.setProperty("pending.version.committed", Boolean.toString(latest(PENDING_CONCEPT).committed()));
            } finally {
                TestHelper.stopDatabase();
            }
        }
    }

    private static EntityVersion latest(UUID concept) {
        EntityVersion latest = null;
        for (Object version : EntityHandle.get(PublicIds.of(concept)).expectConcept().versions()) {
            EntityVersion entityVersion = (EntityVersion) version;
            if (latest == null || entityVersion.stamp().time() > latest.stamp().time()) {
                latest = entityVersion;
            }
        }
        return latest;
    }

    private static boolean listsComponent(Transaction transaction, long nid) {
        boolean[] found = new boolean[1];
        transaction.forEachComponentInTransaction(componentNid -> found[0] |= componentNid == nid);
        return found[0];
    }

    /** Whether the transaction was rebuilt, and the state its record's latest version gives. */
    private static void rebuiltAndState(Properties out, String key, UUID transactionUuid) {
        out.setProperty(key + ".rebuilt", Boolean.toString(Transaction.forUuid(transactionUuid).isPresent()));
        SemanticEntity<?> record = EntityHandle.get(PublicIds.of(transactionUuid)).expectSemantic();
        SemanticEntityVersion latest = null;
        for (Object version : record.versions()) {
            SemanticEntityVersion semanticVersion = (SemanticEntityVersion) version;
            if (latest == null || semanticVersion.stamp().time() > latest.stamp().time()) {
                latest = semanticVersion;
            }
        }
        out.setProperty(key + ".state", PrimitiveData.publicId(((EntityFacade) latest.fieldValues().get(1)).nid()).asUuidArray()[0].toString());
    }
}
