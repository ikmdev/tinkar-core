package org.hl7.tinkar.entity.transaction;

import org.hl7.tinkar.common.alert.AlertObject;
import org.hl7.tinkar.common.alert.AlertStreams;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.service.TrackingCallable;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.terms.State;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class CancelVersionTask extends TrackingCallable<Void> {
    private static final Logger LOG = LoggerFactory.getLogger(CancelTransactionTask.class);
    final EntityVersion version;

    public CancelVersionTask(EntityVersion version) {
        updateTitle(getTitleString() + version.getClass().getSimpleName());
        this.version = version;
        addToTotalWork(1);
    }

    protected String getTitleString() {
        return "Canceling transaction: ";
    }

    @Override
    public Void compute() throws Exception {
        try {
            Long commitTime = System.currentTimeMillis();
            Transaction.forVersion(version).ifPresentOrElse(transaction -> {
                if (transaction.componentsInTransaction.size() == 1) {
                    transaction.forEachStampInTransaction(stampUuid -> {
                        processTransaction(stampUuid);
                    });
                } else {
                    // remove from transaction, add to new transaction.
                    Transaction transactionForVersion = Transaction.make();
                    StampEntity oldStamp = version.stamp();
                    StampEntity newStamp = transactionForVersion.getStamp(oldStamp.state(), oldStamp.time(), oldStamp.authorNid(), oldStamp.moduleNid(), oldStamp.pathNid());

                    transaction.removeComponent(version.publicId());
                    transactionForVersion.addComponent(version.publicId());

                    switch (version) {
                        case ConceptVersionRecord conceptVersionRecord -> {
                            conceptVersionRecord.chronology().versionRecords().remove(version);
                            ConceptVersionRecord newVersionRecord = conceptVersionRecord.withStampNid(newStamp.nid());
                            conceptVersionRecord.chronology().versionRecords().add(newVersionRecord);
                            Entity.provider().putEntity(conceptVersionRecord.chronology());
                        }
                        case SemanticVersionRecord semanticVersionRecord -> {
                            semanticVersionRecord.chronology().versionRecords().remove(version);
                            SemanticVersionRecord newVersionRecord = semanticVersionRecord.withStampNid(newStamp.nid());
                            semanticVersionRecord.chronology().versionRecords().add(newVersionRecord);
                            Entity.provider().putEntity(semanticVersionRecord.chronology());
                        }
                        case PatternVersionRecord patternVersionRecord -> {
                            patternVersionRecord.chronology().versionRecords().remove(version);
                            PatternVersionRecord newVersionRecord = patternVersionRecord.withStampNid(newStamp.nid());
                            patternVersionRecord.chronology().versionRecords().add(newVersionRecord);
                            Entity.provider().putEntity(patternVersionRecord.chronology());
                        }
                        default -> throw new IllegalStateException("Unexpected value: " + version);
                    }
                    processTransaction(newStamp.asUuidArray()[0]);
                }
            }, () -> {
                AlertStreams.getRoot().dispatch(AlertObject.makeError(new IllegalStateException("No transaction for version: " + version)));
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
