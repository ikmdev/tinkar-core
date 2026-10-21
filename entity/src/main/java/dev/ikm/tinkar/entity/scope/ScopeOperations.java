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
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.DataUse;
import dev.ikm.tinkar.common.service.EntityCountSummary;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ScopeErasure;
import dev.ikm.tinkar.common.service.internal.EntityStore;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityRecordFactory;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.aggregator.EntityAggregator;
import dev.ikm.tinkar.entity.boundary.DataKind;
import dev.ikm.tinkar.entity.boundary.EntityBoundaries;
import dev.ikm.tinkar.entity.export.ExportEntitiesToProtobufFile;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import org.eclipse.collections.api.factory.primitive.LongLists;
import org.eclipse.collections.api.factory.primitive.LongSets;
import org.eclipse.collections.api.list.primitive.MutableLongList;
import org.eclipse.collections.api.set.primitive.MutableLongSet;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongConsumer;

/**
 * Export, import, delete and archive of a {@link Scope} (IKE-Network/ike-issues#1313; design
 * {@code design-2026-10-20-instance-data}, "Scope operations"). Export writes the scope's STAMPs,
 * concepts, patterns and semantics as a change set, with a manifest naming the knowledge it
 * references; import checks every one resolves in the target store before loading anything, and
 * the store allocates fresh nids, since public ids carry identity and nids are local; delete
 * erases a closed scope and collects the STAMPs nothing else is written under; archive is export
 * then delete. A move between stores is an archive here and an import there.
 *
 * <p>Export refuses a scope with references leading out of it, and delete one with references
 * leading into it; a delete asks the store's data-use index ({@link DataUse}) for the second kind,
 * which the referencing index holds only for referenced components, and reads every other
 * component once instead where the store keeps no index. Delete is not journaled: the knowledge change sets never carry instance or ephemeral data,
 * and the instance and ephemeral change-set files, which no store replays on its own, still hold
 * the scope's records until they are rewritten.
 */
public final class ScopeOperations {

    /** The zip entry of an exported scope's manifest; under META-INF/, so a change-set loader skips it. */
    public static final String SCOPE_MANIFEST = "META-INF/scope.txt";

    private static final String KIND = "Scope-Kind: ";
    private static final String ROOT = "Root: ";
    private static final String REFERENCE = "Reference: ";

    /**
     * The system property that allows a delete, on a store with no current data-use index, to read
     * every other component instead: {@code true} by default, {@code false} refuses the delete, and
     * {@code always} reads every component even where an index answers, to measure one against the
     * other.
     */
    public static final String FULL_PASS_PROPERTY = "ike.scope.fullPass";

    private ScopeOperations() {
    }

    // ── export ───────────────────────────────────────────────────────────────

    /**
     * What an export wrote.
     *
     * @param file       the change set
     * @param counts     the components written
     * @param references the knowledge components the scope references, which the manifest names
     */
    public record Exported(File file, EntityCountSummary counts, int references) { }

    /**
     * Exports a scope as a change set with its manifest.
     *
     * @param scope the scope
     * @param file  the change set to write
     * @return what was written
     */
    public static Exported export(Scope scope, File file) {
        if (!scope.exportable()) {
            List<String> crossing = new ArrayList<>(scope.outgoing());
            crossing.addAll(scope.dangling());
            throw new IllegalStateException("The scope cannot be exported whole: " + crossing.size()
                    + " reference(s) lead out of it, first: " + crossing.getFirst());
        }
        EntityAggregator aggregator = new EntityAggregator() {
            @Override
            public EntityCountSummary aggregate(LongConsumer nidConsumer) {
                long concepts = 0;
                long patterns = 0;
                long semantics = 0;
                scope.stamps().forEach(nidConsumer::accept);
                for (long nid : scope.members().toArray()) {
                    nidConsumer.accept(nid);
                    switch (EntityHandle.get(nid).orNull()) {
                        case dev.ikm.tinkar.entity.ConceptEntity<?> _ -> concepts++;
                        case dev.ikm.tinkar.entity.PatternEntity<?> _ -> patterns++;
                        case dev.ikm.tinkar.entity.SemanticEntity<?> _ -> semantics++;
                        case null, default -> { }
                    }
                }
                return new EntityCountSummary(concepts, semantics, patterns, scope.stamps().size());
            }
        };
        EntityCountSummary counts;
        try {
            counts = new ExportEntitiesToProtobufFile(file, aggregator).compute();
        } catch (Exception e) {
            throw new IllegalStateException("Exporting the scope to " + file + " failed", e);
        }
        StringBuilder manifest = new StringBuilder(KIND).append(scope.kind().word()).append('\n');
        for (long root : scope.roots().toArray()) {
            manifest.append(ROOT).append(uuids(PrimitiveData.publicId(root))).append('\n');
        }
        for (long reference : scope.knowledge().toArray()) {
            manifest.append(REFERENCE).append(uuids(PrimitiveData.publicId(reference))).append('\n');
        }
        try (FileSystem zip = FileSystems.newFileSystem(URI.create("jar:" + file.toURI()), Map.of())) {
            Path entry = zip.getPath(SCOPE_MANIFEST);
            Files.createDirectories(entry.getParent());
            Files.writeString(entry, manifest, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Writing the scope manifest into " + file + " failed", e);
        }
        return new Exported(file, counts, scope.knowledge().size());
    }

    // ── import ───────────────────────────────────────────────────────────────

    /**
     * What an import loaded.
     *
     * @param kind   the scope's kind, as the manifest names it
     * @param roots  the scope's roots
     * @param counts the components loaded
     */
    public record Imported(DataKind kind, List<PublicId> roots, EntityCountSummary counts) { }

    /** An import refused because the target store lacks knowledge the scope references. */
    public static final class MissingKnowledgeException extends IllegalStateException {
        private final List<PublicId> missing;

        MissingKnowledgeException(File file, List<PublicId> missing) {
            super(file.getName() + " references " + missing.size() + " knowledge component(s) this store lacks, first "
                    + missing.getFirst().idString() + "; nothing was imported");
            this.missing = List.copyOf(missing);
        }

        /** @return the public ids the store lacks */
        public List<PublicId> missing() {
            return missing;
        }
    }

    /**
     * Imports an exported scope: every knowledge component its manifest names must resolve in this
     * store, or nothing is loaded.
     *
     * @param file the exported change set
     * @return what was loaded
     * @throws MissingKnowledgeException when the store lacks knowledge the scope references
     */
    public static Imported importScope(File file) {
        DataKind kind = null;
        List<PublicId> roots = new ArrayList<>();
        List<PublicId> missing = new ArrayList<>();
        try (FileSystem zip = FileSystems.newFileSystem(file.toPath(), Map.of())) {
            Path entry = zip.getPath(SCOPE_MANIFEST);
            if (!Files.exists(entry)) {
                throw new IllegalArgumentException(file.getName() + " is not an exported scope: it has no " + SCOPE_MANIFEST);
            }
            for (String line : Files.readAllLines(entry, StandardCharsets.UTF_8)) {
                if (line.startsWith(KIND)) {
                    String word = line.substring(KIND.length()).strip();
                    for (DataKind k : DataKind.values()) {
                        if (k.word().equals(word)) {
                            kind = k;
                        }
                    }
                } else if (line.startsWith(ROOT)) {
                    roots.add(publicId(line.substring(ROOT.length())));
                } else if (line.startsWith(REFERENCE)) {
                    PublicId id = publicId(line.substring(REFERENCE.length()));
                    if (!PrimitiveData.get().hasPublicId(id)) {
                        missing.add(id);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Reading the scope manifest of " + file + " failed", e);
        }
        if (!missing.isEmpty()) {
            throw new MissingKnowledgeException(file, missing);
        }
        EntityCountSummary counts;
        try {
            counts = new LoadEntitiesFromProtobufFile(file).compute();
        } catch (Exception e) {
            throw new IllegalStateException("Importing the scope from " + file + " failed", e);
        }
        EntityBoundaries.clear();
        return new Imported(kind, List.copyOf(roots), counts);
    }

    // ── delete ───────────────────────────────────────────────────────────────

    /**
     * What a delete erased.
     *
     * @param components the concepts, patterns and semantics erased
     * @param stamps     the STAMPs collected: written under by the scope alone
     * @param stampsKept the scope's instance or ephemeral STAMPs kept, since something else is written under them
     * @param outgoing   the references the scope made to other scopes, gone with it
     * @param changeSets what removing the erased components from the instance and ephemeral change sets did
     * @param indexed    whether the store's data-use index answered what else uses the scope, rather than a read of every component
     */
    public record Deleted(int components, int stamps, int stampsKept, int outgoing, ScopeErasure.ChangeSetRemoval changeSets,
                          boolean indexed) { }

    /**
     * Deletes a scope nothing outside it references: erases its members, then collects its
     * instance and ephemeral STAMPs that no remaining component is written under. A knowledge
     * STAMP is never collected. The store's data-use index says who else is written under each
     * of the scope's data STAMPs and who references each member through a field, which the
     * referencing index does not hold; a reference into the scope refuses the delete before
     * anything is erased. A store with no index has every other component read once instead,
     * unless {@value #FULL_PASS_PROPERTY} is {@code false}. A reference the scope makes to another
     * scope does not refuse it: it goes with the scope.
     *
     * @param scope the scope, resolved just before
     * @return what was erased
     * @throws IllegalStateException         when something outside the scope references into it
     * @throws UnsupportedOperationException when the store cannot erase
     */
    public static Deleted delete(Scope scope) {
        if (!(PrimitiveData.get() instanceof ScopeErasure erasure)) {
            throw new UnsupportedOperationException("The " + PrimitiveData.get().getClass().getSimpleName()
                    + " store cannot delete a scope");
        }
        return delete(scope, erasure::removeFromChangeSets);
    }

    /**
     * Deletes a scope as {@link #delete(Scope)} does, removing the erased components' records from
     * change sets through the given remover rather than the store's own change-set writers.
     *
     * @param scope   the scope, resolved just before
     * @param remover removes the erased components' records, by their UUIDs, from the change sets
     * @return what was erased
     */
    public static Deleted delete(Scope scope, java.util.function.Function<Set<UUID>, ScopeErasure.ChangeSetRemoval> remover) {
        if (!scope.deletable()) {
            throw new IllegalStateException("The scope cannot be deleted: " + scope.incoming().size()
                    + " component(s) outside it cite into it, first: " + scope.incoming().getFirst());
        }
        if (!(PrimitiveData.get() instanceof ScopeErasure erasure)) {
            throw new UnsupportedOperationException("The " + PrimitiveData.get().getClass().getSimpleName()
                    + " store cannot delete a scope");
        }
        Set<Long> candidates = ConcurrentHashMap.newKeySet();
        for (long stamp : scope.stamps().toArray()) {
            if (EntityBoundaries.stampKind(stamp).kind() != DataKind.KNOWLEDGE) {
                candidates.add(stamp);
            }
        }
        int dataStamps = candidates.size();
        MutableLongSet members = LongSets.mutable.of(scope.members().toArray());
        String fullPass = System.getProperty(FULL_PASS_PROPERTY, "true").strip();
        Optional<DataUse> index = erasure.dataUse();
        boolean indexed = !"always".equalsIgnoreCase(fullPass) && index.isPresent() && index.get().current();
        Into into;
        if (indexed) {
            into = askTheIndex(index.get(), members, candidates);
        } else if (!"false".equalsIgnoreCase(fullPass)) {
            into = readEveryComponent(scope, members, candidates);
        } else {
            throw new IllegalStateException("The " + PrimitiveData.get().getClass().getSimpleName()
                    + " store keeps no current data-use index, and " + FULL_PASS_PROPERTY + "=false refuses reading every component instead");
        }
        if (into.count() > 0) {
            throw new IllegalStateException("The scope cannot be deleted: " + into.count()
                    + " reference(s) from outside it lead into it, first: " + into.first());
        }
        MutableLongList erased = LongLists.mutable.withAll(scope.members());
        candidates.forEach(erased::add);
        long[] nids = erased.toArray();
        Set<UUID> uuids = new java.util.HashSet<>();
        for (long nid : nids) {
            uuids.addAll(Arrays.asList(PrimitiveData.publicId(nid).asUuidArray()));
        }
        erasure.eraseComponents(nids);
        EntityService.get().invalidateCaches(nids);
        EntityBoundaries.clear();
        ScopeErasure.ChangeSetRemoval changeSets = remover.apply(uuids);
        return new Deleted(scope.members().size(), candidates.size(), dataStamps - candidates.size(), scope.outgoing().size(), changeSets,
                indexed);
    }

    /** The references into a scope from outside it: how many, and the first in words. */
    private record Into(long count, String first) { }

    /**
     * Asks the store's data-use index who else is written under each candidate STAMP and who
     * references each member, confirming each hit outside the scope by reading that one component:
     * an entry may outlive what it recorded.
     */
    private static Into askTheIndex(DataUse index, MutableLongSet members, Set<Long> candidates) {
        for (Long stamp : List.copyOf(candidates)) {
            index.forEachWrittenUnder(stamp, user -> {
                if (!members.contains(user) && candidates.contains(stamp) && writtenUnder(user, stamp)) {
                    candidates.remove(stamp);
                }
            });
        }
        long[] count = {0};
        String[] first = {null};
        members.forEach(member -> index.forEachReferrer(member, referrer -> {
            if (members.contains(referrer)) {
                return;
            }
            String where = whereReferenced(referrer, member);
            if (where != null) {
                count[0]++;
                if (first[0] == null) {
                    first[0] = Scope.describe(referrer) + " references " + Scope.describe(member) + " by its " + where;
                }
            }
        }));
        return new Into(count[0], first[0]);
    }

    /**
     * Reads every other component once, for the STAMPs its versions are written under and every
     * reference it carries: the fallback for a store that keeps no data-use index.
     */
    private static Into readEveryComponent(Scope scope, MutableLongSet members, Set<Long> candidates) {
        java.util.concurrent.atomic.AtomicLong into = new java.util.concurrent.atomic.AtomicLong();
        java.util.concurrent.atomic.AtomicReference<String> firstInto = new java.util.concurrent.atomic.AtomicReference<>();
        MutableLongSet stamps = LongSets.mutable.of(scope.stamps().toArray());
        EntityStore.current().forEachParallel((bytes, nid) -> {
            if (bytes == null || members.contains(nid) || stamps.contains(nid)) {
                return;
            }
            Entity<?> entity = EntityRecordFactory.make(bytes);
            if (entity instanceof StampEntity<?>) {
                return;
            }
            for (Object o : entity.versions()) {
                EntityVersion version = (EntityVersion) o;
                candidates.remove(version.stampNid());
                dev.ikm.tinkar.entity.boundary.References.of(version, (where, target) -> {
                    if (members.contains(target)) {
                        into.incrementAndGet();
                        firstInto.compareAndSet(null, Scope.describe(nid) + " references " + Scope.describe(target) + " by its " + where);
                    }
                });
            }
        });
        return new Into(into.get(), firstInto.get());
    }

    private static boolean writtenUnder(long nid, long stamp) {
        Optional<? extends Entity<? extends EntityVersion>> held = read(nid);
        if (held.isPresent()) {
            for (EntityVersion version : held.get().versions()) {
                if (version.stampNid() == stamp) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Where a component's versions reference a target, or null when they no longer do. */
    private static String whereReferenced(long nid, long target) {
        Optional<? extends Entity<? extends EntityVersion>> held = read(nid);
        String[] where = {null};
        held.ifPresent(entity -> {
            for (EntityVersion version : entity.versions()) {
                dev.ikm.tinkar.entity.boundary.References.of(version, (w, t) -> {
                    if (t == target && where[0] == null) {
                        where[0] = w;
                    }
                });
            }
        });
        return where[0];
    }

    private static Optional<? extends Entity<? extends EntityVersion>> read(long nid) {
        try {
            return EntityHandle.get(nid).entity();
        } catch (RuntimeException absent) {
            return Optional.empty();
        }
    }

    /**
     * Archives a scope: exports it, then deletes it. The export is refused when references lead
     * out of the scope, and nothing is written; the delete is refused when references lead into
     * it, and the export is kept.
     *
     * @param scope the scope
     * @param file  the change set to write
     * @return what the delete erased
     */
    public static Deleted archive(Scope scope, File file) {
        export(scope, file);
        return delete(scope);
    }

    private static String uuids(PublicId id) {
        StringBuilder b = new StringBuilder();
        for (UUID uuid : id.asUuidArray()) {
            if (!b.isEmpty()) {
                b.append(' ');
            }
            b.append(uuid);
        }
        return b.toString();
    }

    private static PublicId publicId(String text) {
        return PublicIds.of(Arrays.stream(text.strip().split("\\s+")).map(UUID::fromString).toArray(UUID[]::new));
    }
}
