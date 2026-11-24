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
package dev.ikm.tinkar.entity.scope;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ScopeErasure;
import dev.ikm.tinkar.common.service.internal.EntityStore;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.boundary.DataKind;
import dev.ikm.tinkar.entity.boundary.EntityBoundaries;
import dev.ikm.tinkar.entity.boundary.ModuleKinds;
import dev.ikm.tinkar.entity.boundary.References;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiom;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpressionBuilder;
import dev.ikm.tinkar.entity.transaction.StampedWriter;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import dev.ikm.tinkar.terms.State;
import org.eclipse.collections.api.factory.primitive.LongSets;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;

/**
 * Moves documents from one kind of data to another, as sharing a document does, and as making a shared
 * one local does (IKE-Network/ike-issues#1313; decision 5 of
 * {@code design-2026-11-22-knowledge-shared-local}, settled by KEC on 2026-11-22). A document is
 * written under a module of its own, and its kind is that module's, so moving it states another
 * parent for the module. Everything written under the module moves with it, under the same identities
 * and STAMPs, and its change sets go to the folder of the new kind.
 *
 * <p>Refused, with nothing changed: a document written under a module that is not its own, an edit not
 * yet committed, a reference the new kind does not allow, from the documents or to them, and a
 * store that does not hold the new parent.
 */
public final class KindMove {

    private static final Logger LOG = LoggerFactory.getLogger(KindMove.class);

    private KindMove() {
    }

    /** Why documents were not moved, in words for the person who asked. Nothing was changed. */
    public static final class Refused extends IllegalStateException {
        Refused(String reason) {
            super(reason);
        }
    }

    /**
     * What a move did.
     *
     * @param from    the kind the documents were
     * @param to      the kind they are
     * @param modules their modules' nids
     * @param files   the change sets written for them, none where the new kind's record is elsewhere
     */
    public record Moved(DataKind from, DataKind to, List<Long> modules, List<File> files) { }

    /**
     * Moves the documents of a scope to the kind of a parent.
     *
     * @param scope  the documents, resolved just before
     * @param parent the new parent of their modules: the Shared module, the Local module or the File module
     * @param author who moves them
     * @return what was done
     * @throws Refused why nothing was changed
     */
    public static Moved move(Scope scope, ConceptFacade parent, ConceptFacade author) {
        if (!(PrimitiveData.get() instanceof ScopeErasure erasure)) {
            throw new Refused("This knowledge base cannot move data from one kind to another.");
        }
        return move(scope, parent, author, erasure::removeFromChangeSets, erasure::newChangeSet);
    }

    /**
     * Moves the documents of a scope as {@link #move(Scope, ConceptFacade, ConceptFacade)} does, through the
     * given change sets rather than the store's own change-set writers.
     *
     * @param scope        the documents, resolved just before
     * @param parent       the new parent of their modules
     * @param author       who moves them
     * @param remover      removes components' records, by their UUIDs, from the change sets of every kind but knowledge
     * @param newChangeSet a new change-set file for a module's data, in the folder of its kind
     * @return what was done
     * @throws Refused why nothing was changed
     */
    public static Moved move(Scope scope, ConceptFacade parent, ConceptFacade author,
                             Function<Set<UUID>, ScopeErasure.ChangeSetRemoval> remover, LongFunction<Optional<File>> newChangeSet) {
        if (!(PrimitiveData.get() instanceof ScopeErasure erasure)) {
            throw new Refused("This knowledge base cannot move data from one kind to another.");
        }
        if (!PrimitiveData.get().hasPublicId(parent.publicId()) || EntityHandle.get(parent.nid()).entity().isEmpty()) {
            throw new Refused("This knowledge base does not have the " + parent.description()
                    + "; load the current starter set first.");
        }
        DataKind to = ModuleKinds.resolve(parent.nid()).kind();
        DataKind from = scope.kind();
        MutableLongSet members = LongSets.mutable.of(scope.members().toArray());
        MutableLongSet stamps = LongSets.mutable.of(scope.stamps().toArray());
        List<Long> modules = modules(scope, members);
        requireCommitted(scope);
        requireOutgoing(scope, members, stamps, to);
        requireIncoming(members, stamps, to, erasure);
        if (from == to) {
            return new Moved(from, to, modules, List.of());
        }
        // Out of the old kind's change sets first: what they hold is written again for the new kind, and the
        // removal reads every folder.
        Set<UUID> uuids = new HashSet<>();
        for (long nid : members.toArray()) {
            uuids.addAll(Arrays.asList(PrimitiveData.publicId(nid).asUuidArray()));
        }
        for (long nid : stamps.toArray()) {
            uuids.addAll(Arrays.asList(PrimitiveData.publicId(nid).asUuidArray()));
        }
        List<Long> moved = new ArrayList<>();
        ConceptFacade old = from == DataKind.SHARED ? KernelConcept.SHARED_MODULE
                : from == DataKind.FILE ? KernelConcept.FILE_MODULE : KernelConcept.LOCAL_MODULE;
        remover.apply(uuids);
        try {
            for (long module : modules) {
                state(module, parent, author);
                moved.add(module);
            }
        } catch (RuntimeException failed) {
            for (long module : moved) {
                state(module, old, author);
            }
            ScopeChangeSets.write(newChangeSet, scope.roots().toArray(), modules, from);
            throw failed;
        }
        // The scope again: stating the parent added a version and its STAMP.
        List<File> files = ScopeChangeSets.write(newChangeSet, scope.roots().toArray(), modules, to);
        LOG.info("Moved {} module(s) from {} to {}: {} component(s), {} change set(s) written", modules.size(), from.word(),
                to.word(), members.size(), files.size());
        return new Moved(from, to, List.copyOf(modules), files);
    }

    /** The modules a scope is written under, each its own, written under itself. */
    private static List<Long> modules(Scope scope, MutableLongSet members) {
        List<Long> modules = new ArrayList<>();
        for (long stamp : scope.stamps().toArray()) {
            long module = EntityBoundaries.moduleOf(stamp);
            if (!members.contains(module)) {
                throw new Refused("Part of it is written under the module " + PrimitiveData.text(module)
                        + ", which is not its own. Remove it from the knowledge base and open its file again.");
            }
            if (!modules.contains(module)) {
                modules.add(module);
            }
        }
        if (modules.isEmpty()) {
            throw new Refused("It has no module of its own.");
        }
        return modules;
    }

    private static void requireCommitted(Scope scope) {
        for (long member : scope.members().toArray()) {
            Entity<? extends EntityVersion> entity = EntityHandle.get(member).expectEntity();
            for (EntityVersion version : entity.versions()) {
                if (version.time() == Long.MAX_VALUE) {
                    throw new Refused("An edit of it is not yet committed. Commit the edit or discard it first.");
                }
            }
        }
    }

    /** Every reference from the documents to data outside them must be one the new kind may make. */
    private static void requireOutgoing(Scope scope, MutableLongSet members, MutableLongSet stamps, DataKind to) {
        for (long member : scope.members().toArray()) {
            Entity<? extends EntityVersion> entity = EntityHandle.get(member).expectEntity();
            for (EntityVersion version : entity.versions()) {
                References.of(version, (where, target) -> {
                    if (target == 0 || members.contains(target) || stamps.contains(target)) {
                        return;
                    }
                    DataKind kind = EntityBoundaries.kindOf(target);
                    if (!to.mayReferTo(kind)) {
                        throw new Refused("It refers to " + kind.word() + " data outside it, " + words(target) + ", which "
                                + to.word() + " data may not do.");
                    }
                });
            }
            if (entity instanceof SemanticEntity<?> semantic && !members.contains(semantic.patternNid())) {
                DataKind kind = EntityBoundaries.kindOf(semantic.patternNid());
                if (kind != DataKind.KNOWLEDGE && kind != to) {
                    throw new Refused("Part of it is an element of " + words(semantic.patternNid()) + ", a pattern of " + kind.word() + " data.");
                }
            }
        }
    }

    /** Everything outside the documents that refers to them must be allowed to, once they are of the new kind. */
    private static void requireIncoming(MutableLongSet members, MutableLongSet stamps, DataKind to, ScopeErasure erasure) {
        LongConsumer check = referrer -> {
            if (members.contains(referrer) || stamps.contains(referrer) || Transaction.endedRecord(referrer)) {
                return;
            }
            DataKind kind = EntityBoundaries.kindOf(referrer);
            if (!kind.mayReferTo(to)) {
                throw new Refused(words(referrer) + ", which is " + kind.word() + " data, refers to it, and " + kind.word()
                        + " data may not refer to " + to.word() + " data.");
            }
        };
        EntityStore store = EntityStore.current();
        members.forEach(member -> store.forEachSemanticNidForComponent(member, check::accept));
        stamps.forEach(stamp -> store.forEachSemanticNidForComponent(stamp, check::accept));
        erasure.dataUse().filter(dev.ikm.tinkar.common.service.DataUse::current)
                .ifPresent(index -> members.forEach(member -> index.forEachReferrer(member, referrer -> {
                    if (EntityHandle.get(referrer).entity().isPresent()) {      // an entry may outlive what it recorded
                        check.accept(referrer);
                    }
                })));
    }

    /** States a parent for a module, in a new version of its stated axioms, written under the module itself. */
    private static void state(long module, ConceptFacade parent, ConceptFacade author) {
        PublicId[] axioms = new PublicId[1];
        EntityService.get().semanticsForComponentOfPattern(module, KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid())
                .forEach(semantic -> axioms[0] = ((SemanticEntity<? extends SemanticEntityVersion>) semantic).publicId());
        if (axioms[0] == null) {
            throw new Refused("The module " + PrimitiveData.text(module) + " states no parent.");
        }
        ConceptFacade own = EntityProxy.Concept.make(PrimitiveData.text(module), PrimitiveData.publicId(module));
        ModuleKinds.writeModule(own, parent, () -> {
            try (StampedWriter writer = StampedWriter.open("move to " + parent.description(), State.ACTIVE, author, own,
                    KernelConcept.DEVELOPMENT_PATH)) {
                LogicalExpressionBuilder leb = new LogicalExpressionBuilder();
                leb.NecessarySet(leb.And(new LogicalAxiom.Atom[] {leb.ConceptAxiom(parent)}));
                writer.semantic(axioms[0], KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.publicId(), own.publicId(),
                        (DiTreeEntity) leb.build().sourceGraph());
                writer.commit();
            }
        });
    }

    private static String words(long nid) {
        return Scope.describe(nid);
    }
}
