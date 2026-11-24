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
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.KernelConcept;
import org.eclipse.collections.api.factory.primitive.LongSets;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The folder of a knowledge base's shared data: its change sets, one or more files per module, named
 * with the module's public id, and the markers that say a document was removed from it
 * (IKE-Network/ike-issues#1313; {@code design-2026-11-22-knowledge-shared-local}, decisions 7 and 8 and
 * the questions before step 4, settled by KEC on 2026-11-22 and 2026-11-23).
 *
 * <p>Only a marker removes a document elsewhere. A folder that is missing, or only partly copied,
 * looks like a deletion of everything, so missing files remove nothing. A marker is a small file that
 * names the module, the act, who did it and when; it has a name of its own, so two people removing one
 * document write two files and no conflict. Sharing the document again removes its markers.
 */
public final class SharedFolder {

    private static final Logger LOG = LoggerFactory.getLogger(SharedFolder.class);

    /** The folder under a store's root. */
    public static final String NAME = "sharedChangeSets";
    /** The folder of local data, beside it. */
    private static final String LOCAL_NAME = "localChangeSets";
    /** How a change set's file name ends. */
    public static final String CHANGE_SET_SUFFIX = "ike-cs.zip";
    /** How a marker's file name ends. */
    public static final String MARKER_SUFFIX = ".removed.properties";

    private static final Pattern MODULE_ID = Pattern.compile("\\[([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})]");

    private SharedFolder() {
    }

    /** What removed a document from the shared folder. */
    public enum Act {
        /** It was deleted for everyone who syncs. */
        DELETED,
        /** It was made local: whoever did it kept it, and everyone else loses it. */
        MADE_LOCAL
    }

    /**
     * A marker: a document was removed from the shared folder.
     *
     * @param module the document's module
     * @param name   the document's name, for people
     * @param act    what was done
     * @param by     who did it, by name
     * @param when   when
     * @param file   the marker's file
     */
    public record Removal(UUID module, String name, Act act, String by, Instant when, Path file) {

        /** @return the removal in words: {@code notes/a.adoc was deleted by Ada on 2026-11-23} */
        public String words() {
            return name + (act == Act.DELETED ? " was deleted by " : " was made local by ") + by + " on " + when.toString().substring(0, 10);
        }
    }

    /** @return whether the open knowledge base knows the Shared module, without which shared data would be taken for knowledge */
    public static boolean declared() {
        return PrimitiveData.get().hasPublicId(KernelConcept.SHARED_MODULE.publicId())
                && EntityHandle.get(KernelConcept.SHARED_MODULE.nid()).entity().isPresent();
    }

    /**
     * The folder of a store.
     *
     * @param storeRoot the store's root
     * @return the folder, which may not exist
     */
    public static Path of(Path storeRoot) {
        return storeRoot.resolve(NAME);
    }

    /**
     * The module a file of the folder is named for.
     *
     * @param file a change set or a marker
     * @return its module's public id, when the name holds one
     */
    public static Optional<UUID> moduleOf(Path file) {
        Matcher id = MODULE_ID.matcher(file.getFileName().toString());
        UUID last = null;
        while (id.find()) {
            last = UUID.fromString(id.group(1));             // a document's own name may hold brackets; the id comes last
        }
        return Optional.ofNullable(last);
    }

    /**
     * The change sets of the folder.
     *
     * @param folder the folder
     * @return its change sets by module, in file-name order
     */
    public static Map<UUID, List<Path>> changeSets(Path folder) {
        Map<UUID, List<Path>> byModule = new LinkedHashMap<>();
        for (Path file : files(folder, CHANGE_SET_SUFFIX)) {
            moduleOf(file).ifPresent(module -> byModule.computeIfAbsent(module, _ -> new ArrayList<>()).add(file));
        }
        return byModule;
    }

    // ── markers ──────────────────────────────────────────────────────────────

    /**
     * Leaves a marker that a document was removed.
     *
     * @param folder the folder
     * @param module the document's module
     * @param name   the document's name
     * @param act    what was done
     * @param by     who did it
     * @param when   when
     * @return the marker
     */
    public static Removal mark(Path folder, UUID module, String name, Act act, String by, Instant when) {
        Properties properties = new Properties();
        properties.setProperty("module", module.toString());
        properties.setProperty("name", name);
        properties.setProperty("act", act.name());
        properties.setProperty("by", by);
        properties.setProperty("when", when.toString());
        String safe = name.replaceAll("[/\\\\:*?\"<>|\\p{Cntrl}]", "-").strip();
        Path file = folder.resolve((safe.length() > 80 ? safe.substring(0, 80).strip() : safe) + " [" + module + "] "
                + UUID.randomUUID().toString().substring(0, 8) + MARKER_SUFFIX);
        try {
            Files.createDirectories(folder);
            try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                properties.store(out, "A shared document removed from this knowledge base; Komet removes it wherever it syncs.");
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the marker " + file, e);
        }
        return new Removal(module, name, act, by, when, file);
    }

    /**
     * The markers of the folder.
     *
     * @param folder the folder
     * @return the markers, in the order the removals were done; one that cannot be read is left out, with a warning
     */
    public static List<Removal> removals(Path folder) {
        List<Removal> removals = new ArrayList<>();
        for (Path file : files(folder, MARKER_SUFFIX)) {
            Properties properties = new Properties();
            try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                properties.load(in);
                removals.add(new Removal(UUID.fromString(properties.getProperty("module")), properties.getProperty("name", ""),
                        Act.valueOf(properties.getProperty("act")), properties.getProperty("by", ""),
                        Instant.parse(properties.getProperty("when")), file));
            } catch (IOException | RuntimeException unreadable) {
                LOG.warn("Not a marker Komet can read, and nothing was removed for it: {}", file, unreadable);
            }
        }
        removals.sort(java.util.Comparator.comparing(Removal::when));
        return removals;
    }

    /**
     * Takes away a document's markers, as sharing it again does.
     *
     * @param folder the folder
     * @param module the document's module
     * @return how many there were
     */
    public static int unmark(Path folder, UUID module) {
        int gone = 0;
        for (Removal removal : removals(folder)) {
            if (removal.module().equals(module)) {
                try {
                    Files.deleteIfExists(removal.file());
                    gone++;
                } catch (IOException e) {
                    throw new UncheckedIOException("Could not delete the marker " + removal.file(), e);
                }
            }
        }
        return gone;
    }

    // ── what the store holds ─────────────────────────────────────────────────

    /**
     * The modules of shared data the open knowledge base holds. Every STAMP of it is read.
     *
     * @return their nids
     */
    public static Set<Long> sharedModules() {
        if (!declared()) {
            return Set.of();
        }
        // Not the STAMP service's modules in use, which are those written to since the knowledge base was opened.
        Set<Long> modules = java.util.concurrent.ConcurrentHashMap.newKeySet();
        EntityService.get().forEachStampEntity(stamp -> modules.add(stamp.moduleNid()));
        // A cancelled edit's STAMP outlives its document, and names a module that is gone.
        modules.removeIf(module -> EntityHandle.get(module).isAbsent() || ModuleKinds.resolve(module).kind() != DataKind.SHARED);
        return new java.util.TreeSet<>(modules);
    }

    /**
     * Everything written under a module of its own: a document with its annotations.
     *
     * @param module the module's nid
     * @return the scope
     */
    public static Scope scopeOf(long module) {
        MutableLongSet members = LongSets.mutable.empty();
        Optional<DataUse> index = PrimitiveData.get() instanceof ScopeErasure erasure
                ? erasure.dataUse().filter(DataUse::current) : Optional.empty();
        if (index.isPresent()) {
            index.get().forEachStampOfModule(module, stamp -> index.get().forEachWrittenUnder(stamp, member -> {
                if (writtenUnder(member, module)) {
                    members.add(member);
                }
            }));
        } else {
            EntityStore.current().forEachParallel((bytes, nid) -> {
                if (bytes != null && !(EntityRecordFactory.make(bytes) instanceof StampEntity<?>) && writtenUnder(nid, module)) {
                    synchronized (members) {
                        members.add(nid);
                    }
                }
            });
        }
        members.add(module);
        return Scope.of(members.toSortedArray());
    }

    private static boolean writtenUnder(long nid, long module) {
        return EntityHandle.get(nid).entity().stream().flatMap(entity -> entity.versions().stream())
                .anyMatch(version -> EntityBoundaries.moduleOf(((EntityVersion) version).stampNid()) == module);
    }

    /**
     * What the markers did to the open knowledge base.
     *
     * @param removed   the documents removed, in words
     * @param keptLocal the documents kept here as local data, since something of this person's was not synced, in words
     * @param left      the documents a marker could not remove, each with why
     * @param missing   the documents whose change sets are gone from the folder with no marker, which stay
     */
    public record Applied(List<String> removed, List<String> keptLocal, List<String> left, List<String> missing) {

        /** Nothing done. */
        public static final Applied NONE = new Applied(List.of(), List.of(), List.of(), List.of());

        /** @return whether there is nothing to say */
        public boolean quiet() {
            return removed.isEmpty() && keptLocal.isEmpty() && left.isEmpty() && missing.isEmpty();
        }

        /** @return what was done, a sentence each */
        public List<String> sentences() {
            List<String> said = new ArrayList<>();
            removed.forEach(words -> said.add(words + ", so it was removed here."));
            keptLocal.forEach(words -> said.add(words + ". It has changes of yours that were never synced, so it was kept here as local data."));
            left.forEach(said::add);
            missing.forEach(name -> said.add("The change sets of " + name + " are missing from the folder of shared data, with no sign that it"
                    + " was deleted. It stays in this knowledge base."));
            return said;
        }
    }

    /**
     * Applies the folder's markers to the open knowledge base: a shared document with a marker is
     * removed, or kept here as local data when the folder still holds change sets of it, which only
     * this knowledge base can have written since. Missing files without a marker remove nothing.
     *
     * @param folder the folder
     * @param author who the knowledge base was opened as, for what is kept as local data
     * @return what was done
     */
    public static Applied apply(Path folder, ConceptFacade author) {
        if (!declared()) {
            return Applied.NONE;
        }
        List<String> removed = new ArrayList<>();
        List<String> keptLocal = new ArrayList<>();
        List<String> left = new ArrayList<>();
        Map<UUID, Removal> latest = new LinkedHashMap<>();
        removals(folder).forEach(removal -> latest.put(removal.module(), removal));
        Map<UUID, List<Path>> changeSets = changeSets(folder);
        Map<UUID, List<Path>> local = changeSets(folder.resolveSibling(LOCAL_NAME));
        for (Removal removal : latest.values()) {
            PublicId id = PublicIds.of(removal.module());
            long module = PrimitiveData.get().hasPublicId(id) ? PrimitiveData.nid(id) : 0;
            if (module == 0 || EntityHandle.get(module).entity().isEmpty() || ModuleKinds.resolve(module).kind() != DataKind.SHARED) {
                // Removed before, or local here: what reached the folder since goes too, or a third knowledge
                // base would take it for changes of its own.
                deleteChangeSets(folder, removal.module());
                continue;
            }
            try {
                Scope scope = scopeOf(module);
                // What is in the folder was not synced, since the sync took the rest away; what is in the folder of
                // local data was said of a copy kept before, and the document was shared again since.
                if (changeSets.containsKey(removal.module()) || local.containsKey(removal.module())) {
                    KindMove.move(scope, KernelConcept.LOCAL_MODULE, author);
                    deleteChangeSets(folder, removal.module());
                    keptLocal.add(removal.words());
                } else {
                    ScopeOperations.delete(scope);
                    removed.add(removal.words());
                }
            } catch (RuntimeException refused) {
                LOG.warn("{}, and it stays here: {}", removal.words(), refused.getMessage(), refused);
                left.add(removal.words() + ". It stays in this knowledge base: " + refused.getMessage());
            }
        }
        List<String> missing = new ArrayList<>();
        for (long module : sharedModules()) {
            Entity<?> concept = EntityHandle.get(module).orNull();
            if (concept == null) {
                continue;
            }
            boolean here = false;
            for (UUID uuid : concept.publicId().asUuidArray()) {
                here |= changeSets.containsKey(uuid) || latest.containsKey(uuid);
            }
            if (!here) {
                missing.add(PrimitiveData.text(module));
            }
        }
        return new Applied(List.copyOf(removed), List.copyOf(keptLocal), List.copyOf(left), List.copyOf(missing));
    }

    /** Deletes a module's change sets from the folder, once what they held is kept or removed elsewhere. */
    public static void deleteChangeSets(Path folder, UUID module) {
        for (Path file : changeSets(folder).getOrDefault(module, List.of())) {
            try {
                Files.deleteIfExists(file);
            } catch (IOException e) {
                throw new UncheckedIOException("Could not delete " + file, e);
            }
        }
    }

    private static List<Path> files(Path folder, String suffix) {
        if (!Files.isDirectory(folder)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(folder)) {
            return files.filter(file -> file.getFileName().toString().endsWith(suffix)).sorted().toList();
        } catch (IOException e) {
            throw new UncheckedIOException("Could not list " + folder, e);
        }
    }
}
