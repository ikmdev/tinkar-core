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
import dev.ikm.tinkar.common.service.ServiceKeys;
import dev.ikm.tinkar.common.service.ServiceProperties;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.entity.ConceptRecord;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.fixtures.ForkedJvm;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.fixtures.TestTags;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.State;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.util.List;
import java.util.Properties;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Work moved from one open transaction into another (IKE-Network/ike-issues#1326) belongs to the
 * receiver for good. {@link Transaction#moveInto} writes each moved version again under the
 * receiver's stamp, whose UUID derives from the receiver's, so when the store opens again the
 * rebuilt receiver reclaims it, even with uncommitted stamps outside a transaction cancelled at
 * startup. The emptied transaction was cancelled and is not rebuilt. Each store lifetime runs in
 * a JVM of its own.
 */
@Tag(TestTags.STAGED)
class TransactionMoveRestartIT {

    private static final File WORK = TestConstants.createFilePathInTargetFromClassName.apply(TransactionMoveRestartIT.class);
    private static final String STORE = "store";

    static final UUID RECEIVER_CONCEPT = UUID.nameUUIDFromBytes("transaction-move-receiver-concept".getBytes());
    static final UUID MOVED_CONCEPT = UUID.nameUUIDFromBytes("transaction-move-moved-concept".getBytes());

    @Test
    void movedWorkIsReclaimedByTheReceiverAfterARestart() {
        FileUtil.recursiveDelete(WORK);
        assertTrue(WORK.mkdirs(), "Could not create " + WORK);
        Properties in = new Properties();
        in.setProperty(STORE, new File(WORK, "store").getPath());

        Properties written = ForkedJvm.run(Write.class, in);
        assertEquals("true", written.getProperty("moved"), "the work moved");
        assertEquals("false", written.getProperty("donor.open"), "the emptied transaction ended");
        assertEquals("true", written.getProperty("donor.continues.in.receiver"));
        assertEquals("true", written.getProperty("receiver.owns.moved"), "the receiver owns the moved version");
        assertEquals("1", written.getProperty("moved.versions"), "the move left no version behind");

        Properties reopened = ForkedJvm.run(Reopen.class, written);
        assertEquals("true", reopened.getProperty("receiver.rebuilt"));
        assertEquals("false", reopened.getProperty("donor.rebuilt"), "the emptied transaction is not rebuilt");
        assertEquals("true", reopened.getProperty("receiver.owns.moved"),
                "the rebuilt receiver owns the moved version again");
        assertEquals("true", reopened.getProperty("moved.uncommitted"),
                "the moved version is still pending, though stamps outside a transaction were cancelled");
        assertEquals("true", reopened.getProperty("moved.committed"), "the receiver's commit publishes the moved version");
        assertEquals("true", reopened.getProperty("receiver.concept.committed"), "and its own");
    }

    /** Writes a pending concept in each of two transactions, moves one's work into the other, then stops the store. */
    static class Write implements ForkedJvm.Stage {
        @Override
        public void run(Properties in, Properties out) {
            out.putAll(in);
            TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, new File(in.getProperty(STORE)));
            try {
                TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);

                Transaction receiver = Transaction.make("The change continued");
                receiver.leavePending();
                write(receiver, RECEIVER_CONCEPT);
                Transaction donor = Transaction.make("The edits moved");
                donor.leavePending();
                write(donor, MOVED_CONCEPT);
                out.setProperty("receiver.uuid", receiver.transactionUuid().toString());
                out.setProperty("donor.uuid", donor.transactionUuid().toString());

                out.setProperty("moved", Boolean.toString(donor.moveInto(receiver)));
                out.setProperty("donor.open", Boolean.toString(donor.isOpen()));
                out.setProperty("donor.continues.in.receiver", Boolean.toString(donor.continuation() == receiver));
                List<EntityVersion> moved = versions(MOVED_CONCEPT);
                out.setProperty("moved.versions", Integer.toString(moved.size()));
                out.setProperty("receiver.owns.moved", Boolean.toString(moved.size() == 1
                        && Transaction.forVersion(moved.getFirst()).filter(owner -> owner == receiver).isPresent()));
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

    /** Reopens the store, cancelling uncommitted stamps outside a transaction, and commits the receiver. */
    static class Reopen implements ForkedJvm.Stage {
        @Override
        public void run(Properties in, Properties out) {
            out.putAll(in);
            ServiceProperties.set(ServiceKeys.CANCEL_UNCOMMITTED_STAMPS_AT_STARTUP, Boolean.TRUE);
            TestHelper.reopenDataBase(DataStore.SPINED_ARRAY_STORE, new File(in.getProperty(STORE)));
            try {
                Transaction receiver = Transaction.forUuid(UUID.fromString(in.getProperty("receiver.uuid"))).orElse(null);
                out.setProperty("receiver.rebuilt", Boolean.toString(receiver != null));
                out.setProperty("donor.rebuilt", Boolean.toString(
                        Transaction.forUuid(UUID.fromString(in.getProperty("donor.uuid"))).isPresent()));
                List<EntityVersion> moved = versions(MOVED_CONCEPT);
                out.setProperty("moved.uncommitted", Boolean.toString(moved.size() == 1 && moved.getFirst().uncommitted()));
                out.setProperty("receiver.owns.moved", Boolean.toString(receiver != null && moved.size() == 1
                        && Transaction.forVersion(moved.getFirst()).filter(owner -> owner == receiver).isPresent()));
                if (receiver != null) {
                    receiver.commit();
                }
                out.setProperty("moved.committed", Boolean.toString(versions(MOVED_CONCEPT).stream().allMatch(EntityVersion::committed)));
                out.setProperty("receiver.concept.committed",
                        Boolean.toString(versions(RECEIVER_CONCEPT).stream().allMatch(EntityVersion::committed)));
            } finally {
                TestHelper.stopDatabase();
            }
        }
    }

    private static List<EntityVersion> versions(UUID concept) {
        return EntityHandle.get(PublicIds.of(concept)).expectConcept().versions().stream()
                .map(EntityVersion.class::cast).toList();
    }
}
