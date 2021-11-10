package org.hl7.tinkar.entity.transaction;

import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.service.TrackingCallable;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.StampEntityVersion;
import org.hl7.tinkar.entity.StampRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class CommitTransactionTask extends TrackingCallable<Void> {
    private static final Logger LOG = LoggerFactory.getLogger(CancelTransactionTask.class);
    final Transaction transaction;

    public CommitTransactionTask(Transaction transaction) {
        updateTitle(getTitleString() + transaction.transactionUuid());
        this.transaction = transaction;
        addToTotalWork(transaction.stampsInTransactionCount());
    }

    protected String getTitleString() {
        return "Committing transaction: ";
    }

    @Override
    public Void compute() throws Exception {
        try {
            Long commitTime = System.currentTimeMillis();
            transaction.forEachStampInTransaction(stampUuid -> {
                processTransaction(stampUuid, commitTime);
            });

            return null;
        } catch (Throwable t) {
            LOG.error(t.getLocalizedMessage(), t);
            throw t;
        }
    }

    private void processTransaction(UUID stampUuid, Long commitTime) {
        StampRecord stampEntity = Entity.getStamp(PrimitiveData.nid(stampUuid));
        StampEntityVersion stampVersion = stampEntity.lastVersion();
        if (stampVersion.time() == Long.MAX_VALUE) {
            StampEntityVersion committedVersion = stampEntity.addVersion(stampVersion.state(),
                    commitTime, stampVersion.authorNid(), stampVersion.moduleNid(), stampVersion.pathNid());
            Entity.provider().putStamp(stampEntity);
        }
        completedUnitOfWork();
        //TODO support nested transactions
//        for (TransactionImpl childTransaction : transaction.getChildren()) {
//            processTransaction(uncommittedStamp, stampSequence, childTransaction);
//        }
    }
}
