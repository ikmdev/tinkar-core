package org.hl7.tinkar.entity.transaction;

import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.terms.State;

public class CommitVersionTask extends TransactionVersionTask {
    public CommitVersionTask(ConceptVersionRecord version) {
        super(version);
    }

    public CommitVersionTask(PatternVersionRecord version) {
        super(version);
    }

    public CommitVersionTask(SemanticVersionRecord version) {
        super(version);
    }

    public CommitVersionTask(StampVersionRecord version) {
        super(version);
    }

    protected String getTitleString() {
        return "Committing version for: ";
    }

    @Override
    protected void performTransactionAction(Transaction transactionForAction) {
        transactionForAction.commit();
    }

    @Override
    protected State getStateForVersion(EntityVersion version) {
        return version.state();
    }
}
