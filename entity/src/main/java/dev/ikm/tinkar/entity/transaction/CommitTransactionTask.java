package dev.ikm.tinkar.entity.transaction;

import dev.ikm.tinkar.common.service.TrackingCallable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CommitTransactionTask extends TrackingCallable<Void> {
    private static final Logger LOG = LoggerFactory.getLogger(CancelTransactionTask.class);
    final Transaction transaction;

    public CommitTransactionTask(Transaction transaction) {
        updateTitle(getTitleString() + transaction.transactionUuid());
        this.transaction = transaction;
        addToTotalWork(1);
    }

    protected String getTitleString() {
        return "Committing transaction: ";
    }

    @Override
    public Void compute() throws Exception {
        try {
            int stampCount = transaction.commit();
            completedUnitOfWork();
            return null;
        } catch (Throwable t) {
            LOG.error(t.getLocalizedMessage(), t);
            throw t;
        }
    }
}
