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
import dev.ikm.tinkar.entity.boundary.DataKind;
import dev.ikm.tinkar.entity.boundary.EntityBoundaries;
import dev.ikm.tinkar.entity.boundary.ModuleKinds;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import org.eclipse.collections.api.factory.primitive.LongLists;
import org.eclipse.collections.api.factory.primitive.LongSets;
import org.eclipse.collections.api.list.primitive.MutableLongList;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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

/**
 * Export, import, delete and archive of a {@link Scope} (IKE-Network/ike-issues#1313; design
 * {@code design-2026-10-20-instance-data}, "Scope operations"). Export writes the scope's STAMPs,
 * concepts, patterns and semantics as a change set, with a manifest naming the knowledge it
 * references, its kind and the modules it is written under. Import checks all three against the
 * target store before loading anything, and that the store does not hold the scope already; the
 * store allocates fresh nids, since public ids carry identity and nids are local; delete
 * erases a closed scope and collects the STAMPs nothing else is written under; archive is export
 * then delete. A move between stores is an archive here and an import there.
 *
 * <p>Export refuses a scope with references leading out of it, and delete one with references
 * leading into it; a delete asks the store's data-use index ({@link DataUse}) for the second kind,
 * which the referencing index holds only for referenced components, and reads every other
 * component once instead where the store keeps no index. Delete is not journaled: the knowledge change sets never carry shared or local data,
 * and the shared and local change-set files, which no store replays on its own, still hold
 * the scope's records until they are rewritten.
 */
public final class ScopeOperations {

    private static final Logger LOG = LoggerFactory.getLogger(ScopeOperations.class);

    /** The zip entry of an exported scope's manifest; under META-INF/, so a change-set loader skips it. */
    public static final String SCOPE_MANIFEST = "META-INF/scope.txt";

    /** The zip folder of what an export adds beside the components, such as a conversation's own file; a loader skips it too. */
    private static final String ENTRIES = "META-INF/scope";

    private static final String KIND = "Scope-Kind: ";
    private static final String ROOT = "Root: ";
    private static final String MODULE = "Module: ";
    private static final String OWN_MODULE = "Own-Module: ";
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
        return export(scope, file, Map.of());
    }

    /**
     * Exports a scope as a change set with its manifest, and with entries a reader of it may use,
     * such as a conversation's own file, which a change-set loader does not read.
     *
     * @param scope   the scope
     * @param file    the change set to write
     * @param entries the entries' contents, by simple file name
     * @return what was written
     */
    public static Exported export(Scope scope, File file, Map<String, byte[]> entries) {
        if (!scope.exportable()) {
            List<String> crossing = new ArrayList<>(scope.outgoing());
            crossing.addAll(scope.dangling());
            throw new IllegalStateException("The scope cannot be exported whole: " + crossing.size()
                    + " reference(s) lead out of it, first: " + crossing.getFirst());
        }
        entries.keySet().forEach(ScopeOperations::requireSimpleName);
        EntityCountSummary counts = ScopeChangeSets.write(file, scope);
        StringBuilder manifest = new StringBuilder(KIND).append(scope.kind().word()).append('\n');
        for (long root : scope.roots().toArray()) {
            manifest.append(ROOT).append(uuids(PrimitiveData.publicId(root))).append('\n');
        }
        for (long module : modulesOf(scope)) {
            manifest.append(scope.members().contains(module) ? OWN_MODULE : MODULE).append(uuids(PrimitiveData.publicId(module))).append('\n');
        }
        for (long reference : scope.knowledge().toArray()) {
            manifest.append(REFERENCE).append(uuids(PrimitiveData.publicId(reference))).append('\n');
        }
        try (FileSystem zip = FileSystems.newFileSystem(URI.create("jar:" + file.toURI()), Map.of())) {
            Path entry = zip.getPath(SCOPE_MANIFEST);
            Files.createDirectories(entry.getParent());
            Files.writeString(entry, manifest, StandardCharsets.UTF_8);
            for (Map.Entry<String, byte[]> added : entries.entrySet()) {
                Path path = zip.getPath(ENTRIES, added.getKey());
                Files.createDirectories(path.getParent());
                Files.write(path, added.getValue());
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Writing the scope manifest into " + file + " failed", e);
        }
        return new Exported(file, counts, scope.knowledge().size());
    }

    /** The modules a scope is written under that are of its kind, whether or not they are part of it. */
    private static List<Long> modulesOf(Scope scope) {
        List<Long> modules = new ArrayList<>();
        for (long stamp : scope.stamps().toArray()) {
            long module = EntityBoundaries.moduleOf(stamp);
            if (EntityBoundaries.stampKind(stamp).kind() != DataKind.KNOWLEDGE && !modules.contains(module)) {
                modules.add(module);
            }
        }
        return modules;
    }

    private static void requireSimpleName(String name) {
        if (name.isBlank() || name.startsWith(".") || name.contains("/") || name.contains("\\")) {
            throw new IllegalArgumentException("Not a simple file name: " + name);
        }
    }

    // ── import ───────────────────────────────────────────────────────────────

    /**
     * What an import loaded.
     *
     * @param kind       the scope's kind, as the manifest names it
     * @param roots      the scope's roots
     * @param counts     the components loaded
     * @param replaced   whether a copy this store had was removed first
     * @param changeSets the change sets written to the store's own folders, so they hold the scope too
     */
    public record Imported(DataKind kind, List<PublicId> roots, EntityCountSummary counts, boolean replaced, List<File> changeSets) { }

    /** Why a scope was not imported, in words for the person who asked. Nothing was changed. */
    public static class ImportRefused extends IllegalStateException {
        ImportRefused(String reason) {
            super(reason);
        }
    }

    /** An import refused because the target store lacks knowledge the scope references. */
    public static final class MissingKnowledgeException extends ImportRefused {
        private final List<PublicId> missing;

        MissingKnowledgeException(List<PublicId> missing) {
            super("It refers to " + (missing.size() == 1 ? "1 component" : missing.size() + " components")
                    + " this knowledge base does not have. The first is " + missing.getFirst().asUuidList().makeString(" ") + ".");
            this.missing = List.copyOf(missing);
        }

        /** @return the public ids the store lacks */
        public List<PublicId> missing() {
            return missing;
        }
    }

    /** An import refused because the target store has the scope already. Replacing it removes the store's copy first. */
    public static final class AlreadyHereException extends ImportRefused {
        AlreadyHereException() {
            super("This knowledge base has it already.");
        }
    }

    /**
     * An exported scope's manifest, with what was exported beside the components.
     *
     * @param kind       the scope's kind, or {@code null} when the file does not name one
     * @param roots      its roots
     * @param modules    the modules it is written under that the target store must hold
     * @param ownModules the modules it is written under that are part of it
     * @param references the knowledge it references
     * @param entries    what was exported beside the components, by simple file name
     */
    public record Manifest(DataKind kind, List<PublicId> roots, List<PublicId> modules, List<PublicId> ownModules,
                           List<PublicId> references, Map<String, byte[]> entries) { }

    /**
     * @param file a zip file
     * @return whether it is an exported scope, rather than any other change set
     */
    public static boolean exportedScope(File file) {
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file)) {
            return zip.getEntry(SCOPE_MANIFEST) != null;
        } catch (IOException notAZip) {
            return false;
        }
    }

    /**
     * Reads an exported scope's manifest.
     *
     * @param file the exported change set
     * @return its manifest
     * @throws IllegalArgumentException when the file has none
     */
    public static Manifest manifest(File file) {
        DataKind kind = null;
        List<PublicId> roots = new ArrayList<>();
        List<PublicId> modules = new ArrayList<>();
        List<PublicId> ownModules = new ArrayList<>();
        List<PublicId> references = new ArrayList<>();
        Map<String, byte[]> entries = new java.util.LinkedHashMap<>();
        try (FileSystem zip = FileSystems.newFileSystem(file.toPath(), Map.of())) {
            Path entry = zip.getPath(SCOPE_MANIFEST);
            if (!Files.exists(entry)) {
                throw new IllegalArgumentException(file.getName() + " is not an exported scope: it has no " + SCOPE_MANIFEST);
            }
            for (String line : Files.readAllLines(entry, StandardCharsets.UTF_8)) {
                if (line.startsWith(KIND)) {
                    // A scope exported before 2026-11-22 names its kind instance or ephemeral.
                    kind = DataKind.named(line.substring(KIND.length())).orElse(null);
                } else if (line.startsWith(ROOT)) {
                    roots.add(publicId(line.substring(ROOT.length())));
                } else if (line.startsWith(MODULE)) {
                    modules.add(publicId(line.substring(MODULE.length())));
                } else if (line.startsWith(OWN_MODULE)) {
                    ownModules.add(publicId(line.substring(OWN_MODULE.length())));
                } else if (line.startsWith(REFERENCE)) {
                    references.add(publicId(line.substring(REFERENCE.length())));
                }
            }
            Path added = zip.getPath(ENTRIES);
            if (Files.isDirectory(added)) {
                try (java.util.stream.Stream<Path> files = Files.list(added)) {
                    for (Path path : files.filter(Files::isRegularFile).toList()) {
                        entries.put(path.getFileName().toString(), Files.readAllBytes(path));
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Reading the scope manifest of " + file + " failed", e);
        }
        return new Manifest(kind, List.copyOf(roots), List.copyOf(modules), List.copyOf(ownModules), List.copyOf(references),
                java.util.Collections.unmodifiableMap(entries));
    }

    /**
     * Checks that an exported scope can be imported into the open store as it stands, and reads
     * nothing in. Every component it references must be here. Its kind must be the kind of its
     * modules here, so that a store whose starter set does not have the parent modules, or whose
     * modules have other parents, never takes it for knowledge, which could not be removed as a
     * group again. It must not be here already.
     *
     * @param manifest the file's manifest
     * @throws AlreadyHereException      when everything else allows the import and the store has the scope
     * @throws MissingKnowledgeException when the store lacks knowledge the scope references
     * @throws ImportRefused             any other reason it cannot be imported
     */
    public static void check(Manifest manifest) {
        if (manifest.kind() == null || manifest.kind() == DataKind.KNOWLEDGE || manifest.roots().isEmpty()) {
            throw new ImportRefused("The file does not say what kind of data it has.");
        }
        if (manifest.modules().isEmpty() && manifest.ownModules().isEmpty()) {
            throw new ImportRefused("An earlier version of Komet exported it, without the modules it is written under."
                    + " Export it again with this one.");
        }
        if (!ModuleKinds.parentsPresent()) {
            throw new ImportRefused("This knowledge base's starter set does not have the kinds of data, and it would be taken"
                    + " for knowledge. Load the current starter set first.");
        }
        List<PublicId> missing = new ArrayList<>();
        for (PublicId id : manifest.references()) {
            if (!present(id)) {
                missing.add(id);
            }
        }
        for (PublicId id : manifest.modules()) {
            if (!present(id) && !missing.contains(id)) {
                missing.add(id);
            }
        }
        if (!missing.isEmpty()) {
            throw new MissingKnowledgeException(missing);
        }
        for (PublicId id : manifest.modules()) {
            requireKind(id, manifest.kind());
        }
        for (PublicId id : manifest.ownModules()) {
            if (present(id)) {
                requireKind(id, manifest.kind());
            }
        }
        if (manifest.roots().stream().anyMatch(ScopeOperations::present)) {
            throw new AlreadyHereException();
        }
    }

    private static void requireKind(PublicId module, DataKind kind) {
        DataKind here = ModuleKinds.resolve(PrimitiveData.nid(module)).kind();
        if (here != kind) {
            throw new ImportRefused("It was exported as " + kind.word() + " data, and here its module, "
                    + PrimitiveData.textOptional(PrimitiveData.nid(module)).orElseGet(() -> module.asUuidList().makeString(" ")) + ", is a module of " + here.word() + " data.");
        }
    }

    /** Whether the store holds a component, rather than an identity alone. */
    private static boolean present(PublicId id) {
        return PrimitiveData.get().hasPublicId(id) && read(PrimitiveData.nid(id)).isPresent();
    }

    /**
     * Imports an exported scope the store does not have.
     *
     * @param file the exported change set
     * @return what was loaded
     * @throws ImportRefused why nothing was loaded
     */
    public static Imported importScope(File file) {
        return importScope(file, false);
    }

    /**
     * Imports an exported scope. Nothing is loaded unless {@link #check} allows it; a copy this
     * store has is removed first when the caller asks for that, and put back when the load then
     * fails. The scope is also written to the store's own change sets, since a loaded change set is
     * never written again and a store rebuilt from its folders would otherwise not have it.
     *
     * @param file    the exported change set
     * @param replace whether to remove the copy this store has, if it has one
     * @return what was loaded
     * @throws ImportRefused why nothing was changed
     */
    public static Imported importScope(File file, boolean replace) {
        if (PrimitiveData.get() instanceof ScopeErasure erasure) {
            return importScope(file, replace, erasure::removeFromChangeSets, erasure::newChangeSet);
        }
        return importScope(file, replace, _ -> ScopeErasure.ChangeSetRemoval.NONE, _ -> Optional.empty());
    }

    /**
     * Imports an exported scope as {@link #importScope(File, boolean)} does, through the given
     * change sets rather than the store's own change-set writers.
     *
     * @param file         the exported change set
     * @param replace      whether to remove the copy this store has, if it has one
     * @param remover      removes components' records, by their UUIDs, from the change sets
     * @param newChangeSet a new change-set file for a module's data, in the folder of its kind
     * @return what was loaded
     * @throws ImportRefused why nothing was changed
     */
    public static Imported importScope(File file, boolean replace, java.util.function.Function<Set<UUID>, ScopeErasure.ChangeSetRemoval> remover,
                                       java.util.function.LongFunction<Optional<File>> newChangeSet) {
        Manifest manifest = manifest(file);
        boolean here = false;
        try {
            check(manifest);
        } catch (AlreadyHereException already) {
            if (!replace) {
                throw already;
            }
            here = true;
        }
        Path backup = null;
        try {
            if (here) {
                Scope held = Scope.of(manifest.roots().stream().filter(ScopeOperations::present).mapToLong(PrimitiveData::nid).toArray());
                backup = Files.createTempFile("replaced-scope", ".zip");
                ScopeChangeSets.write(backup.toFile(), held);
                try {
                    delete(held, remover);
                } catch (IllegalStateException | UnsupportedOperationException cannot) {
                    throw new ImportRefused("This knowledge base has it already, and its copy cannot be removed. " + cannot.getMessage());
                }
            }
            EntityCountSummary counts;
            List<File> written;
            try {
                counts = load(file);
                written = ScopeChangeSets.write(newChangeSet, roots(manifest), modules(manifest), manifest.kind());
            } catch (RuntimeException failed) {
                erasePartOf(manifest, remover);
                if (backup != null) {
                    load(backup.toFile());
                    ScopeChangeSets.write(newChangeSet, roots(manifest), modules(manifest), manifest.kind());
                }
                throw failed;
            }
            return new Imported(manifest.kind(), manifest.roots(), counts, here, written);
        } catch (IOException e) {
            throw new UncheckedIOException("Keeping a copy of what " + file.getName() + " replaces failed", e);
        } finally {
            if (backup != null) {
                try {
                    Files.deleteIfExists(backup);
                } catch (IOException left) {
                    backup.toFile().deleteOnExit();
                }
            }
        }
    }

    private static EntityCountSummary load(File file) {
        try {
            LoadEntitiesFromProtobufFile loader = new LoadEntitiesFromProtobufFile(file);
            EntityCountSummary counts = loader.compute();
            if (!loader.complete()) {
                throw new IllegalStateException("not every record could be read");
            }
            return counts;
        } catch (Exception e) {
            throw new IllegalStateException("Importing the scope from " + file.getName() + " failed: " + e.getMessage(), e);
        } finally {
            EntityBoundaries.clear();
        }
    }

    /** Erases what a load that failed had read in, so that nothing is read in. */
    private static void erasePartOf(Manifest manifest, java.util.function.Function<Set<UUID>, ScopeErasure.ChangeSetRemoval> remover) {
        for (PublicId root : manifest.roots()) {
            if (present(root)) {
                try {
                    delete(Scope.of(PrimitiveData.nid(root)), remover);
                } catch (RuntimeException left) {
                    LOG.warn("Part of an import that failed could not be taken out: {}", root.idString(), left);
                }
            }
        }
    }

    private static long[] roots(Manifest manifest) {
        return manifest.roots().stream().filter(ScopeOperations::present).mapToLong(PrimitiveData::nid).toArray();
    }

    private static List<Long> modules(Manifest manifest) {
        List<Long> modules = new ArrayList<>();
        java.util.stream.Stream.concat(manifest.modules().stream(), manifest.ownModules().stream())
                .filter(ScopeOperations::present).forEach(id -> modules.add(PrimitiveData.nid(id)));
        return modules;
    }

    // ── delete ───────────────────────────────────────────────────────────────

    /**
     * What a delete erased.
     *
     * @param components the concepts, patterns and semantics erased
     * @param stamps     the STAMPs collected: written under by the scope alone
     * @param stampsKept the scope's shared or local STAMPs kept, since something else is written under them
     * @param outgoing   the references the scope made to other scopes, gone with it
     * @param changeSets what removing the erased components from the shared and local change sets did
     * @param indexed    whether the store's data-use index answered what else uses the scope, rather than a read of every component
     */
    public record Deleted(int components, int stamps, int stampsKept, int outgoing, ScopeErasure.ChangeSetRemoval changeSets,
                          boolean indexed) { }

    /**
     * Deletes a scope nothing outside it references: erases its members, then collects its
     * shared and local STAMPs that no remaining component is written under. A knowledge
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
        MutableLongSet ended = LongSets.mutable.empty().asSynchronized();
        if (indexed) {
            into = askTheIndex(index.get(), members, candidates, ended);
        } else if (!"false".equalsIgnoreCase(fullPass)) {
            into = readEveryComponent(scope, members, candidates, ended);
        } else {
            throw new IllegalStateException("The " + PrimitiveData.get().getClass().getSimpleName()
                    + " store keeps no current data-use index, and " + FULL_PASS_PROPERTY + "=false refuses reading every component instead");
        }
        if (into.count() > 0) {
            throw new IllegalStateException("The scope cannot be deleted: " + into.count()
                    + " reference(s) from outside it lead into it, first: " + into.first());
        }
        // A document's own module goes with it only when nothing left behind names it; otherwise it
        // stays, with its name and parent and the STAMPs they are written under.
        MutableLongSet kept = keptModules(scope, members, candidates, indexed ? index.get() : null);
        kept.forEach(nid -> read(nid).ifPresent(entity -> {
            for (EntityVersion version : entity.versions()) {
                candidates.remove(version.stampNid());
            }
        }));
        MutableLongList erased = LongLists.mutable.empty();
        for (long member : scope.members().toArray()) {
            if (!kept.contains(member)) {
                erased.add(member);
            }
        }
        int erasedMembers = erased.size();
        candidates.forEach(erased::add);
        long[] nids = erased.toArray();
        Set<UUID> uuids = new java.util.HashSet<>();
        for (long nid : nids) {
            uuids.addAll(Arrays.asList(PrimitiveData.publicId(nid).asUuidArray()));
        }
        closeChangeSetJournals();
        // An ended transaction's record listed what it wrote while it was open: it lets them go. Its
        // entries in the change sets hold it as it was written, naming what is about to be erased, so
        // they go first; the record written again without those components is journalled anew.
        ScopeErasure.ChangeSetRemoval recordsRemoved = ScopeErasure.ChangeSetRemoval.NONE;
        if (!ended.isEmpty()) {
            Set<UUID> records = new java.util.HashSet<>();
            ended.forEach(record -> records.addAll(Arrays.asList(PrimitiveData.publicId(record).asUuidArray())));
            recordsRemoved = remover.apply(records);
            dev.ikm.tinkar.entity.transaction.Transaction.forgetInRecords(ended.toArray(), nids);
        }
        erasure.eraseComponents(nids);
        EntityService.get().invalidateCaches(nids);
        dev.ikm.tinkar.common.service.ServiceLifecycleManager.get().getRunningService(dev.ikm.tinkar.entity.StampService.class).ifPresent(stamps -> stamps.forget(nids));
        EntityBoundaries.clear();
        ScopeErasure.ChangeSetRemoval changeSets = remover.apply(uuids).plus(recordsRemoved);
        return new Deleted(erasedMembers, candidates.size(), dataStamps - candidates.size(), scope.outgoing().size(), changeSets,
                indexed);
    }

    /**
     * What erasing knowledge did.
     *
     * @param components the components erased
     * @param changeSets what was removed from the knowledge change sets
     */
    public record ErasedKnowledge(int components, ScopeErasure.ChangeSetRemoval changeSets) { }

    /**
     * Erases knowledge components from the store and from the knowledge change sets: the one
     * exception to knowledge never being erased (IKE-Network/ike-issues#1313; the fourth question
     * before step 2 of {@code design-2026-11-22-knowledge-shared-local}, settled by KEC on
     * 2026-11-22). It is for knowledge written before a rule it broke existed, such as the
     * narrations of documents saved in files written before 2026-11-08, once what it records is
     * kept elsewhere: without it, a knowledge base rebuilt from its change sets would hold
     * knowledge about data it does not have.
     *
     * <p>Refused before anything is erased: a component that is not knowledge, and a semantic
     * outside the components that refers to one of them.
     *
     * @param nids the components
     * @return what was erased
     * @throws IllegalArgumentException      when a component is not knowledge, or something else refers to one
     * @throws UnsupportedOperationException when the store cannot erase
     */
    public static ErasedKnowledge eraseKnowledge(long[] nids) {
        if (!(PrimitiveData.get() instanceof ScopeErasure erasure)) {
            throw new UnsupportedOperationException("The " + PrimitiveData.get().getClass().getSimpleName()
                    + " store cannot erase knowledge");
        }
        if (nids.length == 0) {
            return new ErasedKnowledge(0, ScopeErasure.ChangeSetRemoval.NONE);
        }
        MutableLongSet erasing = LongSets.mutable.of(nids);
        Set<UUID> uuids = new java.util.LinkedHashSet<>();
        for (long nid : nids) {
            Entity<? extends EntityVersion> entity = EntityHandle.get(nid).expectEntity();
            if (EntityBoundaries.kindOf(nid) != DataKind.KNOWLEDGE) {
                throw new IllegalArgumentException(entity.publicId().idString() + " is not knowledge");
            }
            uuids.addAll(Arrays.asList(entity.publicId().asUuidArray()));
            EntityStore.current().forEachSemanticNidForComponent(nid, referrer -> {
                if (!erasing.contains(referrer)) {
                    throw new IllegalArgumentException(EntityHandle.get(referrer).expectEntity().publicId().idString()
                            + " refers to " + entity.publicId().idString() + ", so nothing was erased");
                }
            });
        }
        closeChangeSetJournals();
        erasure.eraseComponents(nids);
        EntityService.get().invalidateCaches(nids);
        dev.ikm.tinkar.common.service.ServiceLifecycleManager.get().getRunningService(dev.ikm.tinkar.entity.StampService.class).ifPresent(stamps -> stamps.forget(nids));
        EntityBoundaries.clear();
        return new ErasedKnowledge(nids.length, erasure.removeFromKnowledgeChangeSets(uuids));
    }

    /**
     * Closes the open change-set journals before anything is erased. A journal can still hold what
     * was written moments ago, as when a document is rebuilt just after it was read in, and writing
     * it out reads the public ids of what it references. A journal that cannot close stops the
     * delete here, with nothing erased.
     */
    private static void closeChangeSetJournals() {
        for (dev.ikm.tinkar.common.service.SaveState state : PrimitiveData.getStatesToSave()) {
            if (state instanceof dev.ikm.tinkar.entity.ChangeSetWriterService) {
                state.save().join();
            }
        }
    }

    /** The references into a scope from outside it: how many, and the first in words. */
    private record Into(long count, String first) { }

    /**
     * The scope's own modules that something left behind still names, each with its name, its
     * stated parent and whatever else of the scope refers to it alone: a STAMP of the scope kept
     * because something outside it is written under it, or a STAMP outside the scope, which the
     * store's data-use index lists by module, or a read of every STAMP finds when the store keeps no
     * index. Two copies of one file, as a superseded prose document and its vertex document are,
     * share the document's module, and deleting one leaves it to the other (IKE-Network/ike-issues#1313).
     */
    private static MutableLongSet keptModules(Scope scope, MutableLongSet members, Set<Long> candidates, DataUse index) {
        MutableLongSet scopeStamps = LongSets.mutable.of(scope.stamps().toArray());
        MutableLongSet modules = LongSets.mutable.empty();
        scopeStamps.forEach(stamp -> {
            long module = EntityBoundaries.moduleOf(stamp);
            if (members.contains(module)) {
                modules.add(module);
            }
        });
        MutableLongSet kept = LongSets.mutable.empty();
        if (modules.isEmpty()) {
            return kept;
        }
        MutableLongSet named = LongSets.mutable.empty();
        scopeStamps.forEach(stamp -> {
            long module = EntityBoundaries.moduleOf(stamp);
            if (modules.contains(module) && !candidates.contains(stamp)) {
                named.add(module);
            }
        });
        if (index != null) {
            modules.forEach(module -> index.forEachStampOfModule(module, stamp -> {
                // An entry may outlive what it recorded: confirm it by reading that one STAMP.
                if (!scopeStamps.contains(stamp)
                        && read(stamp).filter(e -> e instanceof StampEntity<?> s && s.moduleNid() == module).isPresent()) {
                    named.add(module);
                }
            }));
        } else {
            EntityStore.current().forEachParallel((bytes, nid) -> {
                if (bytes == null || scopeStamps.contains(nid)) {
                    return;
                }
                if (EntityRecordFactory.make(bytes) instanceof StampEntity<?> stamp && modules.contains(stamp.moduleNid())) {
                    synchronized (named) {
                        named.add(stamp.moduleNid());
                    }
                }
            });
        }
        EntityStore store = EntityStore.current();
        named.forEach(module -> {
            java.util.ArrayDeque<Long> queue = new java.util.ArrayDeque<>();
            if (kept.add(module)) {
                queue.add(module);
            }
            while (!queue.isEmpty()) {
                long at = queue.poll();
                store.forEachSemanticNidForComponent(at, citing -> {
                    if (members.contains(citing) && kept.add(citing)) {
                        queue.add(citing);
                    }
                });
            }
        });
        return kept;
    }

    /**
     * Asks the store's data-use index who else is written under each candidate STAMP and who
     * references each member, confirming each hit outside the scope by reading that one component:
     * an entry may outlive what it recorded.
     */
    private static Into askTheIndex(DataUse index, MutableLongSet members, Set<Long> candidates, MutableLongSet ended) {
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
            if (dev.ikm.tinkar.entity.transaction.Transaction.endedRecord(referrer)) {
                ended.add(referrer);                             // bookkeeping: it lets go of the scope's components
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
    private static Into readEveryComponent(Scope scope, MutableLongSet members, Set<Long> candidates, MutableLongSet ended) {
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
                    if (members.contains(target) && dev.ikm.tinkar.entity.transaction.Transaction.endedRecord(nid)) {
                        ended.add(nid);                          // bookkeeping: it lets go of the scope's components
                    } else if (members.contains(target)) {
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
