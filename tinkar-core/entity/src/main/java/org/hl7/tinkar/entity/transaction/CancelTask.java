package org.hl7.tinkar.entity.transaction;

import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.service.TrackingCallable;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.StampEntityVersion;
import org.hl7.tinkar.entity.StampRecord;
import org.hl7.tinkar.terms.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public abstract class CancelTask extends TrackingCallable<Void> {
    private static final Logger LOG = LoggerFactory.getLogger(CancelTask.class);
    final Transaction transaction;

    public CancelTask(Transaction transaction) {
        updateTitle(getTitleString() + transaction.transactionUuid());
        this.transaction = transaction;
        addToTotalWork(transaction.stampsInTransactionCount());
    }

    protected String getTitleString() {
        return "Canceling transaction: ";
    }

    @Override
    public Void compute() throws Exception {
        try {
            transaction.forEachStampInTransaction(stampUuid -> {
                processTransaction(stampUuid);
            });

            return null;
        } catch (Throwable t) {
            LOG.error(t.getLocalizedMessage(), t);
            throw t;
        }
    }

    private void processTransaction(UUID stampUuid) {
        StampRecord stampRecord = Entity.getStamp(PrimitiveData.nid(stampUuid));
        StampEntityVersion stampVersion = stampRecord.lastVersion();
        if (stampVersion.time() == Long.MIN_VALUE) {
            // already canceled.
        } else {
            StampEntityVersion canceledVersion = stampRecord.addVersion(State.CANCELED,
                    Long.MIN_VALUE, stampVersion.authorNid(), stampVersion.moduleNid(), stampVersion.pathNid());

            Entity.provider().putStamp(stampRecord);
        }
        completedUnitOfWork();
        //TODO support nested transactions
//        for (TransactionImpl childTransaction : transaction.getChildren()) {
//            processTransaction(uncommittedStamp, stampSequence, childTransaction);
//        }
    }

    protected long getTime() {
        return Long.MIN_VALUE;
    }

    protected State getStatus(State uncommittedStatus) {
        return State.CANCELED;
    }
}
