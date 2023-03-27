package dev.ikm.tinkar.entity.transaction;

import dev.ikm.tinkar.common.service.TrackingCallable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CancelTransactionTask extends TrackingCallable<Void> {
    private static final Logger LOG = LoggerFactory.getLogger(CancelTransactionTask.class);
    final Transaction transaction;

    public CancelTransactionTask(Transaction transaction) {
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
            int count = transaction.cancel();
            for (int i = 0; i < count; i++) {
                completedUnitOfWork();
            }
            return null;
        } catch (Throwable t) {
            LOG.error(t.getLocalizedMessage(), t);
            throw t;
        }
    }

    protected long getTime() {
        return Long.MIN_VALUE;
    }
}
