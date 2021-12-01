package org.hl7.tinkar.entity.transaction;

import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.terms.State;

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
