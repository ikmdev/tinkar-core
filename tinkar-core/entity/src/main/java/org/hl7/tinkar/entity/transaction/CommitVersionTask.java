package org.hl7.tinkar.entity.transaction;

import org.hl7.tinkar.common.alert.AlertObject;
import org.hl7.tinkar.common.alert.AlertStreams;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.service.TrackingCallable;
import org.hl7.tinkar.entity.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;

public class CommitVersionTask extends TrackingCallable<Void> {
    private static final Logger LOG = LoggerFactory.getLogger(CommitVersionTask.class);
    final EntityVersion version;

    public CommitVersionTask(EntityVersion version) {
        this.version = version;
        updateTitle(getTitleString() + version.getClass().getSimpleName());
        addToTotalWork(1);
    }

    protected String getTitleString() {
        return "Committing version for: ";
    }

    @Override
    public Void compute() throws Exception {
        try {
            Long commitTime = System.currentTimeMillis();
            Transaction.forVersion(version).ifPresentOrElse(transaction -> {
                if (transaction.componentsInTransaction.size() == 1) {
                    transaction.forEachStampInTransaction(stampUuid -> {
                        processTransaction(stampUuid, commitTime);
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
                    processTransaction(newStamp.asUuidArray()[0], commitTime);
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
