package dev.ikm.tinkar.entity.transaction;

import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.terms.State;

/**
 * For canceling a single version. If the version is part of a shared transaction, then
 * it will be removed from that transaction, and added to a new single version transaction.
 */
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
