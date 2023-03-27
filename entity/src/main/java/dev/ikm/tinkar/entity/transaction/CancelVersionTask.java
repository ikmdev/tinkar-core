package dev.ikm.tinkar.entity.transaction;

import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.terms.State;

/**
 * For canceling a single version. If the version is part of a shared transaction, then
 * it will be removed from that transaction, and added to a new single version transaction.
 */
public class CancelVersionTask extends TransactionVersionTask {
    public CancelVersionTask(ConceptVersionRecord version) {
        super(version);
    }

    public CancelVersionTask(PatternVersionRecord version) {
        super(version);
    }

    public CancelVersionTask(SemanticVersionRecord version) {
        super(version);
    }

    public CancelVersionTask(StampVersionRecord version) {
        super(version);
    }

    protected String getTitleString() {
        return "Canceling transaction: ";
    }

    protected void performTransactionAction(Transaction transactionForAction) {
        transactionForAction.cancel();
    }

    @Override
    protected State getStateForVersion(EntityVersion version) {
        return State.CANCELED;
    }

    protected long getTime() {
        return Long.MIN_VALUE;
    }
}
