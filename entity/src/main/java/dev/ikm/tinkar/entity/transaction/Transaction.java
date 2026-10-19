/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.entity.transaction;

import org.eclipse.collections.api.list.primitive.MutableLongList;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.sets.ConcurrentHashSet;
import dev.ikm.tinkar.common.util.broadcast.CommitBroadcaster;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.StampAnalogueBuilder;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampEntityVersion;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.entity.StampVersionRecord;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityBinding;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.State;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.primitive.LongLists;
import org.eclipse.collections.api.list.ImmutableList;

import static dev.ikm.tinkar.common.service.PrimitiveData.SCOPED_PATTERN_PUBLICID_FOR_NID;

/**
 * Represents a transaction managing various components and stamps.
 * A Transaction is responsible for keeping track of its associated
 * unique identifier (UUID), name, active transactions, related stamps,
 * and components, as well as handling transactional operations.
 * <p>
 * A transaction that writes uncommitted versions persists as a record in the store, a semantic of
 * the change transaction pattern (IKE-Network/ike-issues#1308, {@code TransactionRecords}); that
 * record is its only serialized form.
 */
public class Transaction implements Comparable<Transaction> {

    private static final org.slf4j.Logger LOG = org.slf4j.LoggerFactory.getLogger(Transaction.class);

    private static ConcurrentHashSet<Transaction> activeTransactions = new ConcurrentHashSet<>();
    private final UUID transactionUuid;
    /** Named at birth, or renamed while open by {@link #setTransactionName(String)}. */
    private volatile String transactionName;
    ConcurrentHashSet<UUID> stampsInTransaction = new ConcurrentHashSet<>();
    ConcurrentHashSet<Long> componentsInTransaction = new ConcurrentHashSet<>();
    private long commitTime = Long.MAX_VALUE;

    /*
     * The transaction's record (IKE-Network/ike-issues#1308, TransactionRecords): written once the
     * transaction mints its first uncommitted stamp outside a load phase, then on each lifecycle
     * event. A transaction that only writes under committed stamps, or only in a load phase, has
     * none and lives in memory, as before.
     */
    private volatile long basisTime = System.currentTimeMillis();
    private volatile String externalBasis = "";
    private volatile long basisPathNid;
    private volatile long recordAuthorNid;
    private volatile boolean recorded;
    /** Rebuilt from its record at startup: its versions are matched by stamp alone. */
    private volatile boolean restored;
    /** How many components the record's latest version lists; -1 before the first. */
    private volatile int recordedComponents = -1;

    /**
     * How far this transaction is toward its one ending (IKE-Network/ike-issues#1243). It ends
     * once, committed or canceled. Repeating the ending it had is a silent no-op; the other
     * ending is refused, since the stored stamps can honor only one. An ending that fails part
     * way returns the transaction to {@code OPEN}, so a caller can cancel what it left behind.
     */
    private enum Ending { OPEN, COMMITTING, COMMITTED, CANCELING, CANCELED }

    private final AtomicReference<Ending> ending = new AtomicReference<>(Ending.OPEN);

    /**
     * Constructs a new Transaction instance with the specified UUID and name.
     * The created transaction is added to the list of active transactions.
     *
     * @param transactionUuid the unique identifier for this transaction
     * @param transactionName the name associated with this transaction
     */
    private Transaction(UUID transactionUuid, String transactionName) {
        this.transactionName = transactionName;
        this.transactionUuid = transactionUuid;
        activeTransactions.add(this);
    }

    /**
     * Constructs a new Transaction instance with the specified transaction name.
     * A unique identifier is automatically generated for the transaction.
     *
     * @param transactionName the name of the transaction
     */
    public Transaction(String transactionName) {
        this(UUID.randomUUID(), transactionName);
    }

    /**
     * Default constructor for the Transaction class.
     * Creates a new Transaction instance with a randomly generated unique identifier (UUID)
     * and an empty description.
     */
    public Transaction() {
        this(UUID.randomUUID(), "");
    }

    /**
     * Retrieves a transaction associated with the given stamp identifier. A stamp
     * identity may carry multiple UUIDs — an adopted, store-established identity does —
     * so every UUID is checked; transactions mint single-UUID stamps, so at most one
     * can match.
     *
     * @param stampId the public identifier for the stamp
     * @return an Optional containing the corresponding Transaction if found; an empty Optional otherwise
     */
    public static Optional<Transaction> forStamp(PublicId stampId) {
        for (UUID stampUuid : stampId.asUuidArray()) {
            Optional<Transaction> transaction = forStamp(stampUuid);
            if (transaction.isPresent()) {
                return transaction;
            }
        }
        return Optional.empty();
    }

    /**
     * Finds an active transaction that contains the specified stamp UUID.
     *
     * @param stampUuid the UUID of the stamp to search for in active transactions
     * @return an Optional containing the transaction if found, or an empty Optional if no transaction contains the given stamp UUID
     */
    public static Optional<Transaction> forStamp(UUID stampUuid) {
        for (Transaction transaction : activeTransactions) {
            if (transaction.stampsInTransaction.contains(stampUuid)) {
                return Optional.of(transaction);
            }
        }
        return Optional.empty();
    }

    /**
     * Retrieves the transaction associated with the given entity version, if any.
     *
     * @param version the entity version for which the associated transaction is to be determined
     * @return an Optional containing the associated transaction if found, otherwise an empty Optional
     */
    public static Optional<Transaction> forVersion(EntityVersion version) {
        StampEntity stamp = version.stamp();
        UUID[] stampUuids = stamp.asUuidArray();
        for (Transaction transaction : activeTransactions) {
            if (transaction.restored && transaction.ownsStamp(stampUuids)) {
                // Rebuilt from its record: its stamps are uncommitted and derive from its UUID,
                // so the stamp alone names it; a component the record did not list joins it.
                transaction.componentsInTransaction.add(version.nid());
                return Optional.of(transaction);
            }
            if (transaction.componentsInTransaction.contains(version.nid())) {
                // A stamp has one UUID (getStamp mints it); the loop only avoids reading it by position.
                for (UUID stampUuid : stampUuids) {
                    if (transaction.stampsInTransaction.contains(stampUuid)) {
                        return Optional.of(transaction);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Creates and returns a new instance of the Transaction class.
     *
     * @return a new Transaction object
     */
    public static Transaction make() {
        return new Transaction();
    }

    /**
     * Creates and returns a new Transaction object with the specified name.
     *
     * @param transactionName the name of the transaction to be created
     * @return a new Transaction object with the provided name
     */
    public static Transaction make(String transactionName) {
        return new Transaction(transactionName);
    }

    /**
     * The transactions that are open: made, or restored, and neither committed nor canceled.
     * A snapshot: transactions keep being made and ended after it is taken.
     *
     * @return the open transactions, in no particular order
     */
    public static ImmutableList<Transaction> activeTransactions() {
        return Lists.immutable.withAll(activeTransactions);
    }

    /**
     * The name this transaction was made with, or was last given.
     *
     * @return the transaction name; empty when it was made without one
     */
    public String getTransactionName() {
        return transactionName;
    }

    /**
     * Names this transaction while it is open. A transaction made without a name is named at
     * publish time by the user: the name goes out with the commit
     * ({@link CommitBroadcaster.CommitNotification#transactionName()}).
     *
     * @param transactionName the new name
     * @throws IllegalStateException if the transaction has ended
     */
    public void setTransactionName(String transactionName) {
        if (!isOpen()) {
            throw new IllegalStateException("Transaction " + transactionUuid + " has ended; it cannot be renamed");
        }
        this.transactionName = transactionName;
    }

    /**
     * Compares this transaction with the specified transaction for order.
     * Returns a negative integer, zero, or a positive integer as this transaction's UUID
     * is less than, equal to, or greater than the specified transaction's UUID.
     *
     * @param o the transaction to be compared.
     * @return a negative integer, zero, or a positive integer as this transaction's UUID
     *         is less than, equal to, or greater than the specified transaction's UUID.
     */
    @Override
    public int compareTo(Transaction o) {
        return this.transactionUuid.compareTo(o.transactionUuid);
    }

    /**
     * Removes a component associated with the specified entity
     * from the components in transaction.
     *
     * @param entity the entity whose associated component is to be removed
     */
    public void removeComponent(Entity entity) {
        componentsInTransaction.remove(entity.nid());
    }

    /**
     * Retrieves the unique identifier for the transaction.
     *
     * @return the UUID representing the transaction's unique identifier
     */
    public UUID transactionUuid() {
        return transactionUuid;
    }

    /**
     * Calculates and returns the total number of stamps in the current transaction.
     *
     * @return the count of stamps in the transaction.
     */
    public int stampsInTransactionCount() {
        return stampsInTransaction.size();
    }

    /**
     * Returns the count of components involved in the transaction.
     *
     * @return the number of components in the transaction
     */
    public int componentsInTransactionCount() {
        return componentsInTransaction.size();
    }

    /**
     * Retrieves a StampEntity using the specified state, time, author, module, and path.
     *
     * @param state the state for which the stamp is to be retrieved
     * @param time the time for which the stamp is to be retrieved
     * @param author the author associated with the stamp
     * @param module the module associated with the stamp
     * @param path the path associated with the stamp
     * @return The retrieved or newly created StampEntity.
     */
    public StampEntity getStamp(State state, long time, ConceptFacade author, ConceptFacade module, ConceptFacade path) {
        checkState(state, time, author == null, module == null, path == null);
        return getStamp(state, time, author.publicId(), module.publicId(), path.publicId());
    }

    /**
     * Validates the given state and its associated parameters.
     *
     * @param state       the state to validate; must not be null
     * @param time        a timestamp representing the time, or Long.MAX_VALUE for uncommitted;
     *                    must not be Long.MIN_VALUE, the time of a canceled stamp
     * @param authorNull  flag indicating if the author is null; must be false
     * @param moduleNull  flag indicating if the module is null; must be false
     * @param pathNull    flag indicating if the path is null; must be false
     * @throws IllegalStateException if any of the conditions are not met
     */
    private void checkState(State state, long time, boolean authorNull, boolean moduleNull, boolean pathNull) {
        if (state == null) throw new IllegalStateException("State cannot be null...");
        if (time == Long.MIN_VALUE) throw new IllegalStateException("Time cannot be Long.MIN_VALUE...");
        if (authorNull) throw new IllegalStateException("Author cannot be null...");
        if (moduleNull) throw new IllegalStateException("Module cannot be null...");
        if (pathNull) throw new IllegalStateException("Path cannot be null...");
    }

    /**
     * Retrieves or creates a StampEntity using the provided state, timestamp, author, module, and path identifiers.
     * If a StampEntity corresponding to the given parameters does not exist, it creates and stores one.
     *
     * <p>A stamp whose time is given up front is a complete tuple, and a complete tuple is a value:
     * its identity is derived from the tuple alone, through the same canonical form the entity
     * builder uses ({@link Stamp#stampUuidOf}), so two transactions that declare the same tuple
     * share one stamp entity and versions written twice under it merge to one. Such a stamp is
     * committed at birth, with its single version, and {@link #cancel()} leaves it alone; writes
     * under it are final at the put, which is import semantics. A stamp whose time is
     * {@link Long#MAX_VALUE} belongs to this transaction alone until commit assigns its time,
     * so its identity includes the transaction's.
     *
     * @param state The state associated with the stamp.
     * @param time The timestamp for the stamp in milliseconds since epoch, or Long.MAX_VALUE for the commit time.
     * @param authorId The public identifier of the author for the stamp.
     * @param moduleId The public identifier of the module for the stamp.
     * @param pathId The public identifier of the path for the stamp.
     * @return The retrieved or newly created StampEntity.
     */
    public StampEntity getStamp(State state, long time, PublicId authorId, PublicId moduleId, PublicId pathId) {
        checkState(state, time, authorId == null, moduleId == null, pathId == null);
        UUID stampUuid = time == Long.MAX_VALUE
                ? UuidT5Generator.forTransaction(transactionUuid, state.publicId(), time, authorId, moduleId, pathId)
                : Stamp.stampUuidOf(state, time, authorId, moduleId, pathId);
        boolean newStamp = stampsInTransaction.add(stampUuid);
        StampEntity stamp = findOrMakeStamp(stampUuid, state, time, authorId, moduleId, pathId);
        if (newStamp && time == Long.MAX_VALUE) {
            uncommittedStampAdded(authorId, pathId);
        }
        return stamp;
    }

    private StampEntity findOrMakeStamp(UUID stampUuid, State state, long time, PublicId authorId, PublicId moduleId, PublicId pathId) {
        if (PrimitiveData.get().hasUuid(stampUuid)) {
            StampEntity existing = Entity.getStamp(PrimitiveData.nid(stampUuid));
            if (existing != null) {
                return existing;
            }
        }
        // Making a stamp assigns its nid before the stamp is stored, so a thread that finds the
        // UUID known can still find no stamp: another thread is between the two. Creation is
        // serialized, and the check repeated inside, so that thread waits for the stored stamp
        // instead of returning none (parallel OWL-to-logic batches met it; IKE-Network/ike-issues#1293).
        synchronized (STAMP_CREATION) {
            if (PrimitiveData.get().hasUuid(stampUuid)) {
                StampEntity existing = Entity.getStamp(PrimitiveData.nid(stampUuid));
                if (existing != null) {
                    return existing;
                }
            }
            StampEntity stamp = StampRecord.make(stampUuid, state, time, authorId, moduleId, pathId);
            Entity.provider().putEntity(stamp);
            return stamp;
        }
    }

    /** Serializes stamp creation across transactions and threads; see {@link #getStamp(State, long, PublicId, PublicId, PublicId)}. */
    private static final Object STAMP_CREATION = new Object();

    /**
     * Retrieves a StampEntity based on the provided state, author, module, and path.
     *
     * @param state The state of the stamp to retrieve.
     * @param author The concept representing the author of the stamp.
     * @param module The concept representing the module of the stamp.
     * @param path The concept representing the path of the stamp.
     * @return The retrieved or newly created StampEntity.
     */
    public StampEntity getStamp(State state, ConceptFacade author, ConceptFacade module, ConceptFacade path) {
        return getStamp(state, Long.MAX_VALUE, author.publicId(), module.publicId(), path.publicId());
    }

    /**
     * Retrieves a StampEntity based on the provided state, author, module, and path identifiers.
     *
     * @param state The state of the stamp being retrieved.
     * @param authorNid The identifier of the author associated with the stamp.
     * @param moduleNid The identifier of the module associated with the stamp.
     * @param pathNid The identifier of the path associated with the stamp.
     * @return The retrieved or newly created StampEntity.
     */
    public StampEntity getStamp(State state, long authorNid, long moduleNid, long pathNid) {
        return getStamp(state, Long.MAX_VALUE, authorNid, moduleNid, pathNid);
    }

    /**
     * Retrieves or creates a {@code StampEntity} based on the given parameters.
     * Validates input arguments to ensure they are within defined constraints.
     * <p>     * Time can be Long.MAX_VALUE, the uncommitted time, and will be set at commit time, or Time can be a
     * time in the past, and on commit, time is preserved. This strategy allows transactions to
     * work on import of historic content.
     * <p>     * @param state the state of the stamp to retrieve; must not be null
     * @param time the timestamp associated with the stamp; must not be {@code Long.MIN_VALUE},
     *             the time of a canceled stamp
     * @param authorNid the identifier of the author; must not be zero
     * @param moduleNid the identifier of the module; must not be zero
     * @param pathNid the identifier of the path; must not be zero
     * @return a {@code StampEntity} that matches the provided parameters
     * @throws IllegalStateException if any of the provided parameters are invalid
     */
    public StampEntity getStamp(State state, long time, long authorNid, long moduleNid, long pathNid) {
        if (state == null) throw new IllegalStateException("State cannot be null...");
        if (time == Long.MIN_VALUE) throw new IllegalStateException("Time cannot be Long.MIN_VALUE...");
        if (authorNid == 0) throw new IllegalStateException("Author cannot be zero...");
        if (moduleNid == 0) throw new IllegalStateException("Module cannot be zero...");
        if (pathNid == 0) throw new IllegalStateException("Path cannot be zero...");
        return getStamp(state, time, PrimitiveData.publicId(authorNid), PrimitiveData.publicId(moduleNid), PrimitiveData.publicId(pathNid));
    }

    /**
     * Retrieves or creates a StampEntity for the specified state, author, module, and path,
     * while associating it with components provided through the given entities.
     *
     * @param state        the state for the stamp
     * @param authorNid    the author identifier
     * @param moduleNid    the module identifier
     * @param pathNid      the path identifier
     * @param firstEntity  the primary entity to associate with the stamp
     * @param extraEntities additional entities to associate with the stamp
     * @return a StampEntity representing the combination of the specified parameters
     */
    public StampEntity getStampForEntities(State state, long authorNid, long moduleNid, long pathNid, EntityFacade firstEntity, EntityFacade... extraEntities) {
        StampEntity stampEntity = getStamp(state, Long.MAX_VALUE, authorNid, moduleNid, pathNid);
        addComponent(firstEntity);
        for (EntityFacade entityFacade : extraEntities) {
            addComponent(entityFacade);
        }
        return stampEntity;
    }

    /**
     * Adds a component to the transaction by its entity's nid (numeric identifier).
     * Throws an IllegalStateException if the entity's nid is 0.
     *
     * @param entity the EntityFacade object whose nid is to be added to the transaction
     * @throws IllegalStateException if the nid of the provided entity is 0
     */
    public void addComponent(EntityFacade entity) {
        if (entity == null) {
            return; // Allow null for new entities that don't exist yet
        }
        if (entity.nid() == 0) {
            throw new IllegalStateException("Entity nid cannot = 0: " + entity);
        }
        componentsInTransaction.add(entity.nid());
    }

    /**
     * Adds a component identified by its unique integer identifier (nid) to the current transaction.
     * Throws an {@code IllegalStateException} if the provided nid is zero, as zero is considered invalid.
     *
     * @param entityNid the unique nid of the component to add to the transaction
     *                  (must not be zero).
     * @throws IllegalStateException if the entity nid is zero.
     */
    public void addComponent(long entityNid) {
        if (entityNid == 0) {
            throw new IllegalStateException("Entity nid cannot = 0. ");
        }
        componentsInTransaction.add(entityNid);
    }

    public long commitTime() {
        return this.commitTime;
    }

    /**
     * Commits the current transaction by finalizing all stamps included in it, recording the
     * commit timestamp, and notifying the system of the changes.
     * <p>     * This method processes each stamp in the transaction by invoking the `commitStamp` method
     * with the appropriate commit time. It also updates the list of active transactions by
     * removing the committed transaction and triggers a notification to indicate that a refresh
     * is required.
     * <p>A transaction ends once (IKE-Network/ike-issues#1243). Committing a transaction that was
     * already committed does nothing and announces nothing. Committing one that was canceled is
     * refused. A commit that fails part way leaves the transaction open, so the caller can cancel
     * it; the stamps it finalized before failing are canceled with the rest.
     *
     * @return the total number of stamps that were finalized and committed; 0 if the transaction
     *         was already committed
     * @throws IllegalStateException if the transaction was canceled
     */
    public int commit() {
        if (!begin(Ending.COMMITTING, Ending.COMMITTED, "committed")) {
            return 0;
        }
        try {
            AtomicInteger stampCount = new AtomicInteger();
            this.commitTime = System.currentTimeMillis();
            activeTransactions.remove(this);
            MutableLongList finalizedStampNids = LongLists.mutable.empty();
            forEachStampInTransaction(stampUuid -> {
                finalizedStampNids.add(commitStamp(stampUuid, this.commitTime));
                stampCount.incrementAndGet();
            });
            Entity.provider().notifyRefreshRequired(this);
            MutableLongList changedComponentNids = LongLists.mutable.empty();
            forEachComponentInTransaction(componentNid -> changedComponentNids.add(componentNid));
            ending.set(Ending.COMMITTED);
            record(KernelConcept.TRANSACTION_COMMITTED.nid());
            CommitBroadcaster.publish(new CommitBroadcaster.CommitNotification(
                    this.transactionUuid, this.transactionName, this.commitTime,
                    finalizedStampNids.toArray(), changedComponentNids.toArray(), stampCount.get()));
            return stampCount.get();
        } catch (RuntimeException | Error failure) {
            reopen();
            this.commitTime = Long.MAX_VALUE;
            throw failure;
        }
    }

    /**
     * Whether this transaction is still open: neither committed nor canceled, nor being either.
     * A caller that may reach its error handling after a successful commit cancels only while
     * the transaction is open.
     *
     * @return {@code true} until the transaction is committed or canceled
     */
    public boolean isOpen() {
        return ending.get() == Ending.OPEN;
    }

    /**
     * Starts this transaction's ending.
     *
     * @param inProgress the state while ending: {@code COMMITTING} or {@code CANCELING}
     * @param done       the state once ended: {@code COMMITTED} or {@code CANCELED}
     * @param verb       {@code "committed"} or {@code "canceled"}, for the refusal
     * @return {@code true} to proceed; {@code false} if the transaction already has, or is
     *         reaching, this ending, which makes the call a no-op
     * @throws IllegalStateException if the transaction has, or is reaching, the other ending
     */
    private boolean begin(Ending inProgress, Ending done, String verb) {
        while (true) {
            Ending current = ending.get();
            if (current == Ending.OPEN) {
                if (ending.compareAndSet(Ending.OPEN, inProgress)) {
                    return true;
                }
            } else if (current == inProgress || current == done) {
                return false;
            } else {
                boolean wasCommitted = current == Ending.COMMITTING || current == Ending.COMMITTED;
                throw new IllegalStateException("Transaction " + transactionName + " (" + transactionUuid
                        + ") was " + (wasCommitted ? "committed" : "canceled")
                        + "; it cannot also be " + verb + " (IKE-Network/ike-issues#1243)");
            }
        }
    }

    /** Returns a transaction whose ending failed to open, and to the active transactions. */
    private void reopen() {
        ending.set(Ending.OPEN);
        activeTransactions.add(this);
    }

    public void forEachStampInTransaction(Consumer<? super UUID> action) {
        stampsInTransaction.forEach(action);
    }

    /**
     * Finalizes a stamp by committing it with the provided commit time. If the stamp's
     * current time is not set (the uncommitted time, Long.MAX_VALUE), a new version of the stamp
     * is created with the commit time. This method ensures that the stamp's state and its
     * associated metadata are appropriately updated and stored.
     *
     * @param stampUuid   The unique identifier of the stamp being committed.
     * @param commitTime  The timestamp to be associated with the stamp upon commitment.
     */
    private long commitStamp(UUID stampUuid, long commitTime) {
        long stampNid = ScopedValue
                .where(SCOPED_PATTERN_PUBLICID_FOR_NID, EntityBinding.Stamp.pattern().publicId())
                .call(() -> PrimitiveData.nid(stampUuid));
        StampRecord stampEntity = Entity.getStamp(stampNid);
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
        return stampNid;
    }

    /**
     * Applies the provided action to each component in the transaction. This method
     * processes all components that are part of the current transaction by passing
     * them to the given consumer action.
     *
     * @param action the {@code Consumer} to be applied to each component in the transaction.
     *               The action consumes a component nid.
     */
    public void forEachComponentInTransaction(Consumer<? super Long> action) {
        componentsInTransaction.forEach(action);
    }

    /**
     * Cancels all stamps associated with the transaction by setting their state
     * to "CANCELED." This operation updates each stamp to an analogue version
     * with the canceled state and notifies the entity provider that a refresh
     * is required. The transaction is also removed from the list of active transactions.
     * <p>A transaction ends once (IKE-Network/ike-issues#1243). Canceling a transaction that was
     * already canceled does nothing. Canceling one that was committed is refused, so committed
     * stamps are never canceled. A cancel that fails part way leaves the transaction open.
     *
     * @return the number of stamps that were processed during the cancellation; 0 if the
     *         transaction was already canceled
     * @throws IllegalStateException if the transaction was committed
     */
    public int cancel() {
        if (!begin(Ending.CANCELING, Ending.CANCELED, "canceled")) {
            return 0;
        }
        try {
            return cancelStamps();
        } catch (RuntimeException | Error failure) {
            reopen();
            throw failure;
        }
    }

    private int cancelStamps() {
        AtomicInteger stampCount = new AtomicInteger();
        forEachStampInTransaction(stampUuid -> {
            long stampNid = ScopedValue
                    .where(SCOPED_PATTERN_PUBLICID_FOR_NID, EntityBinding.Stamp.pattern().publicId())
                    .call(() -> PrimitiveData.nid(stampUuid));
            StampRecord stampEntity = Entity.getStamp(stampNid);
            StampEntityVersion stampVersion = stampEntity.lastVersion();
            if (stampVersion.time() == Long.MIN_VALUE) {
                // Already canceled: Long.MIN_VALUE is the time of a canceled stamp.
            } else if (stampVersion.time() != Long.MAX_VALUE) {
                // Committed: a stamp has at most two versions, uncommitted then committed or
                // canceled, and a committed time is final. A stamp minted with its time is committed
                // at birth and may be shared with other transactions, so cancel must not touch it.
                return;
            } else {
                StampAnalogueBuilder newStampBuilder = stampEntity.analogueBuilder();
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
        ending.set(Ending.CANCELED);
        record(KernelConcept.TRANSACTION_CANCELLED.nid());
        Entity.provider().notifyRefreshRequired(this);
        return stampCount.get();
    }

    // ── The record (IKE-Network/ike-issues#1308) ─────────────────────────────

    /**
     * When the transaction began: its record's first version, or when it was made for a
     * transaction without one.
     *
     * @return the transaction's initiation, in epoch milliseconds
     */
    public long initiated() {
        long fromRecord = recorded ? TransactionRecords.initiated(transactionUuid) : Long.MAX_VALUE;
        return fromRecord == Long.MAX_VALUE ? Math.min(basisTime, System.currentTimeMillis()) : fromRecord;
    }

    /**
     * The time of the point in the store this transaction's changes were made against: what its
     * author could see. Two versions of one component are concurrent when neither change's basis
     * includes the other.
     *
     * @return the basis time, in epoch milliseconds
     */
    public long basisTime() {
        return basisTime;
    }

    /**
     * @return what the changes were made against outside the store, such as the git commit a
     *         document edit started from; empty when there is none
     */
    public String externalBasis() {
        return externalBasis;
    }

    /**
     * Moves the basis forward: the transaction's author has taken in what changed up to
     * {@code time}, as a rebase onto a newer commit does. A basis never moves back.
     *
     * @param time          the new basis time, in epoch milliseconds
     * @param externalBasis what the changes are now made against outside the store, or empty
     */
    public void moveBasis(long time, String externalBasis) {
        this.basisTime = Math.max(this.basisTime, time);
        this.externalBasis = externalBasis == null ? "" : externalBasis;
        if (isOpen()) {
            record(KernelConcept.TRANSACTION_OPEN.nid());
        }
    }

    /**
     * Sets what the changes are made against outside the store, keeping the basis time.
     *
     * @param externalBasis such as the git commit a document edit starts from
     */
    public void externalBasis(String externalBasis) {
        moveBasis(this.basisTime, externalBasis);
    }

    /**
     * @return whether the transaction keeps a record in the store, and so survives a restart
     */
    public boolean recorded() {
        return recorded;
    }

    /**
     * Whether this transaction was rebuilt from its record when the store opened: work a restart
     * interrupted, which an editor taking up one of its components continues in this transaction
     * rather than opening a second one beside it.
     *
     * @return {@code true} for a transaction rebuilt at startup
     */
    public boolean rebuiltFromRecord() {
        return restored;
    }

    /**
     * Rebuilds the transactions the store's records say are open, so their uncommitted versions
     * have an owner again after a restart. Called once the entity service answers reads, before
     * anything else reads from the store and before uncommitted stamps outside a transaction are
     * cancelled.
     *
     * @param entities the entity service of the store just opened
     * @return the number of transactions rebuilt
     */
    public static int restoreOpen(EntityService entities) {
        TransactionRecords.storeStarted();
        int restored = 0;
        java.util.List<Transaction> rebuilt = new java.util.ArrayList<>();
        for (TransactionRecords.Snapshot snapshot : TransactionRecords.open(entities)) {
            if (forUuid(snapshot.transactionUuid()).isPresent()) {
                continue;
            }
            Transaction transaction = new Transaction(snapshot.transactionUuid(), snapshot.name());
            transaction.basisTime = snapshot.basisTime();
            transaction.externalBasis = snapshot.externalBasis();
            transaction.basisPathNid = snapshot.basisPathNid();
            transaction.recordAuthorNid = snapshot.authorNid();
            transaction.recorded = true;
            transaction.restored = true;
            for (long componentNid : snapshot.componentNids()) {
                transaction.componentsInTransaction.add(componentNid);
            }
            transaction.recordedComponents = snapshot.componentNids().length;
            rebuilt.add(transaction);
            restored++;
        }
        if (!rebuilt.isEmpty()) {
            reclaimStamps(entities, rebuilt);
        }
        return restored;
    }

    /**
     * Gives rebuilt transactions back their uncommitted stamps. A record does not list them: an
     * uncommitted stamp's UUID derives from its transaction's UUID and the stamp's own state,
     * author, module and path, so each uncommitted stamp in the store is matched by deriving its
     * UUID for each rebuilt transaction. Uncommitted stamps are few, and only they are tried.
     */
    private static void reclaimStamps(EntityService entities, java.util.List<Transaction> rebuilt) {
        dev.ikm.tinkar.common.service.internal.EntityStore.current().forEachStampNid(stampNid -> {
            StampEntity<?> stamp = entities.getStampFast(stampNid);
            if (stamp == null || stamp.lastVersion() == null || stamp.time() != Long.MAX_VALUE) {
                return;
            }
            PublicId state = stamp.state().publicId();
            PublicId author = PrimitiveData.publicId(stamp.authorNid());
            PublicId module = PrimitiveData.publicId(stamp.moduleNid());
            PublicId path = PrimitiveData.publicId(stamp.pathNid());
            UUID[] stampUuids = stamp.asUuidArray();
            for (Transaction transaction : rebuilt) {
                UUID derived = UuidT5Generator.forTransaction(transaction.transactionUuid, state, Long.MAX_VALUE, author, module, path);
                for (UUID stampUuid : stampUuids) {
                    if (stampUuid.equals(derived)) {
                        transaction.stampsInTransaction.add(stampUuid);
                        return;
                    }
                }
            }
        });
    }

    /**
     * Writes a version of each open, recorded transaction's record whose components have grown
     * since its last version, so a restart rebuilds it whole. Run when the store is saved, while
     * its services are still running: as it stops, they no longer answer.
     *
     * @param entities the entity service to write through
     * @return the number of records written
     */
    public static int checkpointOpen(EntityService entities) {
        int written = 0;
        for (Transaction transaction : activeTransactions) {
            if (transaction.recorded && transaction.isOpen()
                    && transaction.componentsInTransaction.size() != transaction.recordedComponents
                    && transaction.record(entities, KernelConcept.TRANSACTION_OPEN.nid())) {
                written++;
            }
        }
        return written;
    }

    /**
     * Drops every transaction held in memory, as when the store they belong to has stopped.
     * Their records remain; {@link #restoreOpen(EntityService)} rebuilds the open ones from the next start.
     */
    public static void forgetAll() {
        activeTransactions.clear();
    }

    /**
     * The transactions still open: made in this session, or rebuilt from their records when the
     * store opened. A surface that lets a person review pending work lists these, and commits or
     * cancels them only when asked.
     *
     * @return the open transactions, in no particular order
     */
    public static java.util.List<Transaction> openTransactions() {
        return activeTransactions.stream().filter(Transaction::isOpen).toList();
    }

    /**
     * The transaction rebuilt from its record that owns an entity's pending version under the given
     * stamp tuple: work a restart interrupted. A writer about to change the entity under that tuple
     * continues this transaction rather than opening a second one beside it, since two uncommitted
     * versions of one component do not supersede each other, and its change lands on the pending
     * version's own stamp. For a writer that leaves settling to the person; one that commits at once must
     * not take over pending work, which ends only when someone asks.
     *
     * @param entityNid the entity about to be changed
     * @param state     the state of the stamp about to be made
     * @param authorNid its author
     * @param moduleNid its module
     * @param pathNid   its path
     * @return the rebuilt transaction owning the entity's pending version, if there is one
     */
    public static Optional<Transaction> rebuiltOwnerOfPendingVersion(long entityNid, State state, long authorNid,
                                                                     long moduleNid, long pathNid) {
        if (!(dev.ikm.tinkar.entity.EntityHandle.get(entityNid).orNull() instanceof Entity<?> entity)) {
            return Optional.empty();
        }
        for (Object object : entity.versions()) {
            EntityVersion version = (EntityVersion) object;
            if (!version.uncommitted()) {
                continue;
            }
            StampEntity<?> stamp = version.stamp();
            if (stamp.authorNid() != authorNid || stamp.moduleNid() != moduleNid
                    || stamp.pathNid() != pathNid || stamp.state() != state) {
                continue;
            }
            Optional<Transaction> owner = forVersion(version).filter(Transaction::rebuiltFromRecord);
            if (owner.isPresent()) {
                return owner;
            }
        }
        return Optional.empty();
    }

    /**
     * @param transactionUuid a transaction's UUID
     * @return the active transaction with that UUID, if any
     */
    public static Optional<Transaction> forUuid(UUID transactionUuid) {
        for (Transaction transaction : activeTransactions) {
            if (transaction.transactionUuid.equals(transactionUuid)) {
                return Optional.of(transaction);
            }
        }
        return Optional.empty();
    }

    private boolean ownsStamp(UUID[] stampUuids) {
        for (UUID stampUuid : stampUuids) {
            if (stampsInTransaction.contains(stampUuid)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The first uncommitted stamp records the transaction. Later ones need no version: a record
     * does not list its stamps, which a restart finds by their derived UUIDs.
     */
    private void uncommittedStampAdded(PublicId authorId, PublicId pathId) {
        if (recorded || EntityService.get().isLoadPhase() || !TransactionRecords.available()) {
            return;
        }
        recordAuthorNid = PrimitiveData.nid(authorId);
        basisPathNid = PrimitiveData.nid(pathId);
        recorded = true;
        record(KernelConcept.TRANSACTION_OPEN.nid());
    }

    /** Writes a version of the record, when the transaction has one; a failure is reported, not thrown. */
    private void record(long stateNid) {
        if (recorded) {
            record(EntityService.get(), stateNid);
        }
    }

    /** @return whether the version was written */
    private boolean record(EntityService entities, long stateNid) {
        if (!recorded) {
            return false;
        }
        try {
            TransactionRecords.write(entities, this, stateNid);
            return true;
        } catch (RuntimeException e) {
            LOG.error("Could not record transaction {} ({})", transactionName, transactionUuid, e);
            return false;
        }
    }

    void recordedComponents(int count) {
        this.recordedComponents = count;
    }

    long recordAuthorNid() {
        return recordAuthorNid;
    }

    long basisPathNid() {
        return basisPathNid;
    }

    /**
     * @return the components this transaction has written
     */
    public long[] componentNids() {
        MutableLongList nids = LongLists.mutable.empty();
        componentsInTransaction.forEach(nids::add);
        return nids.toArray();
    }

}
