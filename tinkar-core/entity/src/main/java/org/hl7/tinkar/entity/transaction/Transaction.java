package org.hl7.tinkar.entity.transaction;

import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.sets.ConcurrentHashSet;
import org.hl7.tinkar.common.util.uuid.UuidT5Generator;
import org.hl7.tinkar.component.Version;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.StampEntity;
import org.hl7.tinkar.entity.StampRecord;
import org.hl7.tinkar.terms.ConceptFacade;
import org.hl7.tinkar.terms.State;

import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;

/**
 *
 */
public class Transaction implements Comparable<Transaction> {
    private static ConcurrentHashSet<Transaction> activeTransactions = new ConcurrentHashSet<>();
    private final UUID transactionUuid = UUID.randomUUID();
    private final String transactionName;
    ConcurrentHashSet<UUID> stampsInTransaction = new ConcurrentHashSet<>();
    ConcurrentHashSet<PublicId> componentsInTransaction = new ConcurrentHashSet<>();

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

    public static Optional<Transaction> forVersion(Version version) {
        if (version.publicId().asUuidArray().length > 1) {
            throw new IllegalStateException("Can only handle one UUID for stamp. Found: " + version);
        }
        return forStamp(version.publicId().asUuidArray()[0]);
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

    public void addComponent(PublicId componentId) {
        componentsInTransaction.add(componentId);
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

    public void forEachStampInTransaction(Consumer<? super UUID> action) {
        stampsInTransaction.forEach(action);
    }

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

    public StampEntity getStamp(State state, long time, PublicId authorId, PublicId moduleId, PublicId pathId) {
        checkState(state, time, authorId == null, moduleId == null, pathId == null);
        UUID stampUuid = UuidT5Generator.forTransaction(transactionUuid, state.publicId(), time, authorId, moduleId, pathId);
        stampsInTransaction.add(stampUuid);
        Optional<StampEntity> optionalStamp = Entity.get(PrimitiveData.nid(stampUuid));
        if (optionalStamp.isEmpty()) {
            StampEntity stamp = StampRecord.make(stampUuid, state, time, authorId, moduleId, pathId);
            Entity.provider().putStamp(stamp);
            return stamp;
        }
        return optionalStamp.get();
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
     * @return
     */
    public StampEntity getStamp(State state, long time, int authorNid, int moduleNid, int pathNid) {
        if (state == null) throw new IllegalStateException("State cannot be null...");
        if (time == Long.MIN_VALUE) throw new IllegalStateException("Time cannot be Long.MIN_VALUE...");
        if (authorNid == 0) throw new IllegalStateException("Author cannot be zero...");
        if (moduleNid == 0) throw new IllegalStateException("Module cannot be zero...");
        if (pathNid == 0) throw new IllegalStateException("Path cannot be zero...");
        return getStamp(state, time, PrimitiveData.publicId(authorNid), PrimitiveData.publicId(moduleNid), PrimitiveData.publicId(pathNid));
    }
}
