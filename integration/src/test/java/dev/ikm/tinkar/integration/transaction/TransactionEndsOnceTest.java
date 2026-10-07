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
import dev.ikm.tinkar.common.util.broadcast.CommitBroadcaster;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.fixtures.NewEphemeralKeyValueProvider;
import dev.ikm.tinkar.terms.State;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A transaction ends once: by {@link Transaction#commit()} or by {@link Transaction#cancel()}
 * (IKE-Network/ike-issues#1243). Repeating the ending it had is a silent no-op; the other ending
 * is refused. Either way nothing changes: no stamp is rewritten and no commit is announced.
 * <p>Uncommitted, canceled and committed are stamp times: {@code Long.MAX_VALUE} until commit,
 * {@code Long.MIN_VALUE} once canceled, and the commit time once committed.
 */
@ExtendWith(NewEphemeralKeyValueProvider.class)
class TransactionEndsOnceTest {

    private final List<CommitBroadcaster.CommitNotification> notifications = new ArrayList<>();
    private final Consumer<CommitBroadcaster.CommitNotification> listener = notifications::add;

    @BeforeEach
    void listen() {
        CommitBroadcaster.subscribe(listener);
    }

    @AfterEach
    void stopListening() {
        CommitBroadcaster.unsubscribe(listener);
    }

    // ---------- one ending, as today ----------

    @Test
    void commitGivesTheStampTheCommitTime() {
        Transaction transaction = Transaction.make();
        int stampNid = uncommittedStamp(transaction);

        transaction.commit();

        StampEntity stamp = EntityHandle.getStampOrThrow(stampNid);
        assertEquals(State.ACTIVE, stamp.state());
        assertEquals(transaction.commitTime(), stamp.time());
        assertEquals(List.of(stampNid), announcedStampNids(transaction));
    }

    @Test
    void cancelGivesTheStampTheCanceledTime() {
        Transaction transaction = Transaction.make();
        int stampNid = uncommittedStamp(transaction);

        transaction.cancel();

        StampEntity stamp = EntityHandle.getStampOrThrow(stampNid);
        assertEquals(State.CANCELED, stamp.state());
        assertEquals(Long.MIN_VALUE, stamp.time());
        assertEquals(List.of(), announcedStampNids(transaction));
    }

    // ---------- a second ending ----------

    @Test
    void cancelAfterCommitLeavesTheCommittedStampCommitted() {
        Transaction transaction = Transaction.make();
        int stampNid = uncommittedStamp(transaction);
        transaction.commit();
        long commitTime = transaction.commitTime();

        endAgain(transaction::cancel);

        StampEntity stamp = EntityHandle.getStampOrThrow(stampNid);
        assertEquals(State.ACTIVE, stamp.state(), "a committed stamp must not be canceled");
        assertEquals(commitTime, stamp.time(), "a committed stamp must keep its commit time");
    }

    @Test
    void cancelAfterCommitIsRefused() {
        Transaction transaction = Transaction.make();
        uncommittedStamp(transaction);
        transaction.commit();

        assertThrows(IllegalStateException.class, transaction::cancel);
    }

    @Test
    void commitAfterCancelAnnouncesNoStamp() {
        Transaction transaction = Transaction.make();
        int stampNid = uncommittedStamp(transaction);
        transaction.cancel();

        endAgain(transaction::commit);

        assertEquals(List.of(), announcedStampNids(transaction),
                "a canceled stamp must not be announced as committed");
        StampEntity stamp = EntityHandle.getStampOrThrow(stampNid);
        assertEquals(State.CANCELED, stamp.state());
        assertEquals(Long.MIN_VALUE, stamp.time());
    }

    @Test
    void commitAfterCancelIsRefused() {
        Transaction transaction = Transaction.make();
        uncommittedStamp(transaction);
        transaction.cancel();

        assertThrows(IllegalStateException.class, transaction::commit);
    }

    @Test
    void commitTwiceAnnouncesTheCommitOnce() {
        Transaction transaction = Transaction.make();
        int stampNid = uncommittedStamp(transaction);
        transaction.commit();

        endAgain(transaction::commit);

        assertEquals(List.of(stampNid), announcedStampNids(transaction),
                "one commit, so one announcement");
    }

    @Test
    void commitTwiceIsSilent() {
        Transaction transaction = Transaction.make();
        uncommittedStamp(transaction);
        assertEquals(1, transaction.commit());

        assertEquals(0, transaction.commit(), "a repeated commit finalizes nothing");
    }

    @Test
    void cancelTwiceIsSilent() {
        Transaction transaction = Transaction.make();
        int stampNid = uncommittedStamp(transaction);
        assertEquals(1, transaction.cancel());

        assertEquals(0, transaction.cancel(), "a repeated cancel cancels nothing");
        assertEquals(State.CANCELED, EntityHandle.getStampOrThrow(stampNid).state());
    }

    @Test
    void theRefusalNamesHowTheTransactionEnded() {
        Transaction committed = Transaction.make();
        uncommittedStamp(committed);
        committed.commit();
        IllegalStateException refused = assertThrows(IllegalStateException.class, committed::cancel);
        assertTrue(refused.getMessage().contains("was committed; it cannot also be canceled"), refused.getMessage());

        Transaction canceled = Transaction.make();
        uncommittedStamp(canceled);
        canceled.cancel();
        refused = assertThrows(IllegalStateException.class, canceled::commit);
        assertTrue(refused.getMessage().contains("was canceled; it cannot also be committed"), refused.getMessage());
    }

    @Test
    void aTransactionIsOpenUntilItEnds() {
        Transaction committed = Transaction.make();
        uncommittedStamp(committed);
        assertTrue(committed.isOpen());
        committed.commit();
        assertFalse(committed.isOpen());

        Transaction canceled = Transaction.make();
        uncommittedStamp(canceled);
        canceled.cancel();
        assertFalse(canceled.isOpen());
    }

    // ---------- support ----------

    /** Makes an uncommitted stamp in the transaction, with fresh author, module and path. */
    private static int uncommittedStamp(Transaction transaction) {
        StampEntity stamp = transaction.getStamp(State.ACTIVE, Long.MAX_VALUE,
                PublicIds.of(UUID.randomUUID()), PublicIds.of(UUID.randomUUID()), PublicIds.of(UUID.randomUUID()));
        assertEquals(Long.MAX_VALUE, stamp.time(), "a new stamp is uncommitted");
        return stamp.nid();
    }

    /** Ends a transaction a second time, whether it is refused or silent, to test what it changed. */
    private static void endAgain(Runnable ending) {
        try {
            ending.run();
        } catch (IllegalStateException refused) {
            // A crossing is refused; these tests check only that nothing changed.
        }
    }

    /** The stamp nids announced as committed for this transaction, over every announcement. */
    private List<Integer> announcedStampNids(Transaction transaction) {
        List<Integer> stampNids = new ArrayList<>();
        for (CommitBroadcaster.CommitNotification notification : notifications) {
            if (notification.transactionUuid().equals(transaction.transactionUuid())) {
                for (int stampNid : notification.stampNids()) {
                    stampNids.add(stampNid);
                }
            }
        }
        return stampNids;
    }
}
