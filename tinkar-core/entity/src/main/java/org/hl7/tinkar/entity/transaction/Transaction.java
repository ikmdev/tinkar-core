package org.hl7.tinkar.entity.transaction;

import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.sets.ConcurrentHashSet;
import org.hl7.tinkar.common.util.uuid.UuidT5Generator;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.terms.ConceptFacade;
import org.hl7.tinkar.terms.EntityFacade;
import org.hl7.tinkar.terms.State;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 *
 */
public class Transaction implements Comparable<Transaction> {
    private static ConcurrentHashSet<Transaction> activeTransactions = new ConcurrentHashSet<>();
    private final UUID transactionUuid = UUID.randomUUID();
    private final String transactionName;
    ConcurrentHashSet<UUID> stampsInTransaction = new ConcurrentHashSet<>();
    ConcurrentHashSet<Integer> componentsInTransaction = new ConcurrentHashSet<>();
    private long commitTime = Long.MAX_VALUE;

    public Transaction(String transactionName) {
        this.transactionName = transactionName;
        activeTransactions.add(this);
    }

    public Transaction() {
        this.transactionName = "";
        activeTransactions.add(this);
    }

    public static Optional<Transaction> forStamp(PublicId stampId) {
        if (stampId.asUuidArray().length > 1) {
            throw new IllegalStateException("Can only handle one UUID for stamp. Found: " + stampId);
        }
        return forStamp(stampId.asUuidArray()[0]);
    }

    public static Optional<Transaction> forStamp(UUID stampUuid) {
        for (Transaction transaction : activeTransactions) {
            if (transaction.stampsInTransaction.contains(stampUuid)) {
                return Optional.of(transaction);
            }
        }
        return Optional.empty();
    }

    public static Optional<Transaction> forVersion(EntityVersion version) {
        StampEntity stamp = version.stamp();
        UUID[] stampUuids = stamp.asUuidArray();
        if (stampUuids.length > 1) {
            throw new IllegalStateException("Can only handle one UUID for stamp. Found: " + version);
        }
        return forStamp(stampUuids[0]);
    }

    public static Transaction make() {
        return new Transaction();
    }

    public static Transaction make(String transactionName) {
        return new Transaction(transactionName);
    }

    @Override
    public int compareTo(Transaction o) {
        return this.transactionUuid.compareTo(o.transactionUuid);
    }

    public void removeComponent(Entity entity) {
        componentsInTransaction.remove(entity.nid());
    }

    public UUID transactionUuid() {
        return transactionUuid;
    }

    public int stampsInTransactionCount() {
        return stampsInTransaction.size();
    }

    public int componentsInTransactionCount() {
        return componentsInTransaction.size();
    }

    /**
     * @param state
     * @param time
     * @param author
     * @param module
     * @param path
     * @return StampEntity that is written to the entity store.
     */
    public StampEntity getStamp(State state, long time, ConceptFacade author, ConceptFacade module, ConceptFacade path) {
        checkState(state, time, author == null, module == null, path == null);
        return getStamp(state, time, author.publicId(), module.publicId(), path.publicId());
    }

    private void checkState(State state, long time, boolean authorNull, boolean moduleNull, boolean pathNull) {
        if (state == null) throw new IllegalStateException("State cannot be null...");
        if (time == Long.MIN_VALUE) throw new IllegalStateException("Time cannot be Long.MIN_VALUE...");
        if (authorNull) throw new IllegalStateException("Author cannot be null...");
        if (moduleNull) throw new IllegalStateException("Module cannot be null...");
        if (pathNull) throw new IllegalStateException("Path cannot be null...");
    }

    /**
     * @param state
     * @param time
     * @param authorId
     * @param moduleId
     * @param pathId
     * @return StampEntity that is already written to the entity store.
     */
    public StampEntity getStamp(State state, long time, PublicId authorId, PublicId moduleId, PublicId pathId) {
        checkState(state, time, authorId == null, moduleId == null, pathId == null);
        UUID stampUuid = UuidT5Generator.forTransaction(transactionUuid, state.publicId(), time, authorId, moduleId, pathId);
        stampsInTransaction.add(stampUuid);
        Optional<StampEntity> optionalStamp = Entity.get(PrimitiveData.nid(stampUuid));
        if (optionalStamp.isEmpty()) {
            StampEntity stamp = StampRecord.make(stampUuid, state, time, authorId, moduleId, pathId);
            Entity.provider().putEntity(stamp);
            return stamp;
        }
        return optionalStamp.get();
    }

    /**
     * @param state
     * @param author
     * @param module
     * @param path
     * @return StampEntity that is already written to the entity store.
     */
    public StampEntity getStamp(State state, ConceptFacade author, ConceptFacade module, ConceptFacade path) {
        return getStamp(state, Long.MAX_VALUE, author.publicId(), module.publicId(), path.publicId());
    }

    /**
     * @param state
     * @param authorNid
     * @param moduleNid
     * @param pathNid
     * @return StampEntity that is already written to the entity store.
     */
    public StampEntity getStamp(State state, int authorNid, int moduleNid, int pathNid) {
        return getStamp(state, Long.MAX_VALUE, authorNid, moduleNid, pathNid);
    }

    /**
     * Time can be Long.MAX_VALUE, and will be set at commit time, or Time can be a
     * time in the past, and on commit, time is preserved. This strategy allows transactions to
     * work on import of historic content.
     *
     * @param state
     * @param time
     * @param authorNid
     * @param moduleNid
     * @param pathNid
     * @return StampEntity that is already written to the entity store.
     */
    public StampEntity getStamp(State state, long time, int authorNid, int moduleNid, int pathNid) {
        if (state == null) throw new IllegalStateException("State cannot be null...");
        if (time == Long.MIN_VALUE) throw new IllegalStateException("Time cannot be Long.MIN_VALUE...");
        if (authorNid == 0) throw new IllegalStateException("Author cannot be zero...");
        if (moduleNid == 0) throw new IllegalStateException("Module cannot be zero...");
        if (pathNid == 0) throw new IllegalStateException("Path cannot be zero...");
        return getStamp(state, time, PrimitiveData.publicId(authorNid), PrimitiveData.publicId(moduleNid), PrimitiveData.publicId(pathNid));
    }

    public StampEntity getStampForEntities(State state, int authorNid, int moduleNid, int pathNid, EntityFacade firstEntity, EntityFacade... extraEntities) {
        StampEntity stampEntity = getStamp(state, Long.MAX_VALUE, authorNid, moduleNid, pathNid);
        addComponent(firstEntity);
        for (EntityFacade entityFacade : extraEntities) {
            addComponent(entityFacade);
        }
        return stampEntity;
    }

    public void addComponent(EntityFacade entity) {
        if (entity.nid() == 0) {
            throw new IllegalStateException("Entity nid cannot = 0: " + entity);
        }
        componentsInTransaction.add(entity.nid());
    }

    public long commitTime() {
        return this.commitTime;
    }

    /**
     * @return count of stamps committed.
     */
    public int commit() {
        AtomicInteger stampCount = new AtomicInteger();
        this.commitTime = System.currentTimeMillis();
        forEachStampInTransaction(stampUuid -> {
            commitStamp(stampUuid, this.commitTime);
            stampCount.incrementAndGet();
        });
        activeTransactions.remove(this);
        Entity.provider().notifyRefreshRequired(this);
        return stampCount.get();
    }

    public void forEachStampInTransaction(Consumer<? super UUID> action) {
        stampsInTransaction.forEach(action);
    }

    private void commitStamp(UUID stampUuid, long commitTime) {
        StampRecord stampEntity = Entity.getStamp(PrimitiveData.nid(stampUuid));
        StampEntityVersion stampVersion = stampEntity.lastVersion();
        if (stampVersion.time() == Long.MAX_VALUE) {
            StampAnalogueBuilder newStampBuilder = stampEntity.analogueBuilder();
            newStampBuilder.add(new StampVersionRecord(newStampBuilder.analogue(),
                    stampVersion.stateNid(),
                    commitTime, stampVersion.authorNid(), stampVersion.moduleNid(), stampVersion.pathNid()));
            StampRecord newStamp = newStampBuilder.build();
            Entity.provider().putEntity(newStamp);
        } else {
            // Transaction will retain current time of stamp. Used when importing with existing time.
        }
        //TODO support nested transactions
//        for (TransactionImpl childTransaction : transaction.getChildren()) {
//            processTransaction(uncommittedStamp, stampSequence, childTransaction);
//        }
    }

    public void forEachComponentInTransaction(Consumer<? super Integer> action) {
        componentsInTransaction.forEach(action);
    }

    public int cancel() {
        AtomicInteger stampCount = new AtomicInteger();
        forEachStampInTransaction(stampUuid -> {
            StampRecord stampRecord = Entity.getStamp(PrimitiveData.nid(stampUuid));
            StampEntityVersion stampVersion = stampRecord.lastVersion();
            if (stampVersion.time() == Long.MIN_VALUE) {
                // already canceled.
            } else {
                StampAnalogueBuilder newStampBuilder = stampRecord.analogueBuilder();
                newStampBuilder.add(new StampVersionRecord(newStampBuilder.analogue(),
                        State.CANCELED.nid(),
                        Long.MIN_VALUE, stampVersion.authorNid(), stampVersion.moduleNid(), stampVersion.pathNid()));
                StampRecord newStamp = newStampBuilder.build();
                Entity.provider().putEntity(newStamp);
            }
            stampCount.incrementAndGet();
        });
        //TODO support nested transactions
//        for (TransactionImpl childTransaction : transaction.getChildren()) {
//            processTransaction(uncommittedStamp, stampSequence, childTransaction);
//        }
        activeTransactions.remove(this);
        Entity.provider().notifyRefreshRequired(this);
        return stampCount.get();
    }
}
