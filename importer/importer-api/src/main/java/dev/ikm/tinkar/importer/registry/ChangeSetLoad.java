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
package dev.ikm.tinkar.importer.registry;

import dev.ikm.tinkar.common.service.EntityCountSummary;
import dev.ikm.tinkar.entity.changeset.ChangeSetUpgrade;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * Loads change-set files into the open store and registers each load, skipping the files the
 * store has already registered ({@code dev-ike-dashboard}, decision 20; the registry is
 * IKE-Network/ike-issues#1304).
 *
 * <p>Every load registers through {@link Registry#registerLoad(Path, String, Instant)}, and the
 * registrations are read back through {@link Registry#loadedChecksums()}: a file whose checksum
 * the store has registered is {@linkplain Outcome#REGISTERED_BEFORE skipped}. So the same files
 * load once, whether they came by a manual import, a git pull, or an upgrade that carried them
 * into a new build of the store. A store whose starter set predates the registry
 * ({@link Registry#declared()} false) has no registrations to read and loads every file, as it
 * always has.
 *
 * <p>The files load in the order given: for a store, its knowledge folder
 * ({@code changeSets/src/main/resources}) then its instance folder ({@code instanceChangeSets}),
 * each in file-name order, which the writer makes chronological. Not {@code ephemeralChangeSets},
 * which a session regenerates. A file refused in that pass, for a component no file before it
 * carried, is tried again after the rest; one still refused is reported as such and left
 * unregistered, so the next open tries it again.
 */
public final class ChangeSetLoad {

    private static final Logger LOG = LoggerFactory.getLogger(ChangeSetLoad.class);

    /** The folder under a store's root its knowledge change sets are written to, and a release packages. */
    public static final Path KNOWLEDGE_FOLDER = Path.of("changeSets", "src", "main", "resources");
    /** The folder under a store's root its instance change sets are written to. */
    public static final Path INSTANCE_FOLDER = Path.of("instanceChangeSets");

    private ChangeSetLoad() {
    }

    /** Told of each file as it is loaded. */
    @FunctionalInterface
    public interface Progress {
        /**
         * A file is about to load.
         *
         * @param index  its place among the files of this pass, counting from 1
         * @param count  how many files this pass loads
         * @param file   the file
         * @param retry  whether this is the second pass, over the files the first refused
         */
        void loading(int index, int count, Path file, boolean retry);

        /** Tells no one. */
        Progress NONE = (index, count, file, retry) -> { };
    }

    /** What became of one file. */
    public enum Outcome {
        /** Its checksum was registered before this load began: not loaded again. */
        REGISTERED_BEFORE("registered before"),
        /** Loaded in the first pass and registered. */
        LOADED("loaded"),
        /** Refused in the first pass, loaded after the rest and registered. */
        LOADED_AFTER_RETRY("loaded after a retry"),
        /** Refused in both passes: not loaded, not registered. */
        REFUSED("refused");

        private final String label;

        Outcome(String label) {
            this.label = label;
        }

        /** The outcome as the report words it. */
        public String label() {
            return label;
        }
    }

    /**
     * One file's result.
     *
     * @param file         the file
     * @param checksum     its checksum, {@code sha256:<hex>}
     * @param outcome      what became of it
     * @param counts       what the load wrote, when it loaded
     * @param reason       why it was refused: the first refusal for one loaded after a retry, the
     *                     last for one refused
     * @param registration the Registry stamp its load was registered under, by nid, when the store
     *                     declares the registry and the file loaded
     */
    public record Result(Path file, String checksum, Outcome outcome, Optional<EntityCountSummary> counts,
                         Optional<String> reason, Optional<Long> registration) {

        /** Validates. */
        public Result {
            Objects.requireNonNull(file, "file");
            Objects.requireNonNull(checksum, "checksum");
            Objects.requireNonNull(outcome, "outcome");
            Objects.requireNonNull(counts, "counts");
            Objects.requireNonNull(reason, "reason");
            Objects.requireNonNull(registration, "registration");
        }

        /** Whether the file is in the store now, by this load or an earlier one. */
        public boolean loaded() {
            return outcome != Outcome.REFUSED;
        }
    }

    /**
     * What a load did, in file order.
     *
     * @param base             the folder the report names files relative to
     * @param folders          the folders read, in the order read; empty when files were named
     * @param registryDeclared whether the store declares the registry, so loads were registered
     *                         and earlier registrations skipped
     * @param results          each file's result, in load order
     * @param when             when the load ran
     */
    public record Report(Path base, List<Path> folders, boolean registryDeclared, List<Result> results, Instant when) {

        /** Validates and copies. */
        public Report {
            Objects.requireNonNull(base, "base");
            folders = List.copyOf(folders);
            results = List.copyOf(results);
            Objects.requireNonNull(when, "when");
        }

        /** Whether nothing was refused. */
        public boolean clean() {
            return count(Outcome.REFUSED) == 0;
        }

        /**
         * How many files had an outcome.
         *
         * @param outcome the outcome
         * @return the count
         */
        public long count(Outcome outcome) {
            return results.stream().filter(result -> result.outcome() == outcome).count();
        }

        /** The files loaded by this run, in either pass. */
        public List<Result> loaded() {
            return results.stream().filter(result -> result.outcome() == Outcome.LOADED
                    || result.outcome() == Outcome.LOADED_AFTER_RETRY).toList();
        }

        /** The files refused in both passes. */
        public List<Result> refused() {
            return results.stream().filter(result -> result.outcome() == Outcome.REFUSED).toList();
        }

        /** One line: how many files, and how many had each outcome. */
        public String summary() {
            StringBuilder line = new StringBuilder(String.format("%,d change-set file(s): %,d loaded", results.size(),
                    loaded().size()));
            long retried = count(Outcome.LOADED_AFTER_RETRY);
            if (retried > 0) {
                line.append(String.format(" (%,d after a retry)", retried));
            }
            line.append(String.format(", %,d registered before, %,d refused", count(Outcome.REGISTERED_BEFORE),
                    count(Outcome.REFUSED)));
            if (!registryDeclared) {
                line.append("; the store does not declare the registry, so every file loads and none is registered");
            }
            return line.toString();
        }

        /** The report as Markdown: what was read, each file's outcome, and the summary. */
        public String markdown() {
            StringBuilder out = new StringBuilder();
            out.append("# Change-set load: ").append(base).append("\n\n");
            out.append("Registry: ").append(registryDeclared ? "declared; each load is registered and earlier registrations are skipped"
                    : "not declared by this store; every file loads and nothing is registered").append("\n\n");
            if (!folders.isEmpty()) {
                out.append("Folders, in load order:\n\n");
                for (Path folder : folders) {
                    out.append("- `").append(relative(folder)).append("`\n");
                }
                out.append('\n');
            }
            if (results.isEmpty()) {
                out.append("No change-set files.\n");
            } else {
                out.append("| File | Outcome | Records | Detail |\n|---|---|---:|---|\n");
                for (Result result : results) {
                    out.append("| `").append(relative(result.file())).append("` | ").append(result.outcome().label())
                            .append(" | ").append(result.counts().map(counts -> String.format("%,d", counts.getTotalCount())).orElse(""))
                            .append(" | ").append(result.reason().map(reason -> reason.replace("|", "\\|").replace("\n", " ")).orElse(""))
                            .append(" |\n");
                }
            }
            out.append('\n').append(summary()).append('\n');
            return out.toString();
        }

        private String relative(Path path) {
            Path absolute = path.toAbsolutePath().normalize();
            Path root = base.toAbsolutePath().normalize();
            return absolute.startsWith(root) ? root.relativize(absolute).toString() : absolute.toString();
        }
    }

    /**
     * A store's change-set folders that exist, in load order: the knowledge folder, then the
     * instance folder.
     *
     * @param storeRoot the store's root
     * @return the folders
     */
    public static List<Path> folders(Path storeRoot) {
        List<Path> folders = new ArrayList<>();
        for (Path folder : List.of(storeRoot.resolve(KNOWLEDGE_FOLDER), storeRoot.resolve(INSTANCE_FOLDER))) {
            if (Files.isDirectory(folder)) {
                folders.add(folder);
            }
        }
        return List.copyOf(folders);
    }

    /**
     * Loads the change sets of a store's folders that the store has not registered, and registers
     * each.
     *
     * @param storeRoot the store's root: the open store's, or another whose files are to be loaded
     * @param progress  told of each file as it loads
     * @return the report
     * @throws IOException if a folder cannot be listed
     */
    public static Report loadStore(Path storeRoot, Progress progress) throws IOException {
        List<Path> folders = folders(storeRoot);
        return load(storeRoot, folders, ChangeSetUpgrade.changeSets(folders), progress);
    }

    /**
     * Loads the files the store has not registered, in the order given, and registers each.
     *
     * @param base     the folder the report names the files relative to
     * @param files    the change-set files, in load order
     * @param progress told of each file as it loads
     * @return the report
     */
    public static Report load(Path base, List<Path> files, Progress progress) {
        return load(base, List.of(), files, progress);
    }

    private static Report load(Path base, List<Path> folders, List<Path> files, Progress progress) {
        Instant when = Instant.now();
        boolean declared = Registry.declared();
        Set<String> loadedBefore = declared ? Registry.loadedChecksums() : Set.of();
        Map<Path, Result> results = new LinkedHashMap<>();
        Map<Path, String> checksums = new LinkedHashMap<>();
        List<Path> pending = new ArrayList<>();
        for (Path file : files) {
            String checksum = Registry.checksum(file);
            checksums.put(file, checksum);
            if (loadedBefore.contains(checksum)) {
                LOG.info("Registered before, not loaded again: {}", file.getFileName());
                results.put(file, new Result(file, checksum, Outcome.REGISTERED_BEFORE, Optional.empty(), Optional.empty(),
                        Optional.empty()));
            } else {
                results.put(file, null);
                pending.add(file);
            }
        }
        Map<Path, String> refusals = new LinkedHashMap<>();
        int index = 0;
        for (Path file : pending) {
            progress.loading(++index, pending.size(), file, false);
            try {
                results.put(file, loaded(file, checksums.get(file), Outcome.LOADED, Optional.empty()));
            } catch (RuntimeException refused) {
                String reason = reason(refused);
                LOG.warn("Refused, to be tried again after the rest: {}: {}", file.getFileName(), reason);
                refusals.put(file, reason);
            }
        }
        index = 0;
        for (Map.Entry<Path, String> refusal : refusals.entrySet()) {
            Path file = refusal.getKey();
            progress.loading(++index, refusals.size(), file, true);
            try {
                results.put(file, loaded(file, checksums.get(file), Outcome.LOADED_AFTER_RETRY, Optional.of(refusal.getValue())));
            } catch (RuntimeException refused) {
                String reason = reason(refused);
                LOG.error("Refused again, not loaded: {}: {}", file.getFileName(), reason, refused);
                results.put(file, new Result(file, checksums.get(file), Outcome.REFUSED, Optional.empty(), Optional.of(reason),
                        Optional.empty()));
            }
        }
        return new Report(base, folders, declared, new ArrayList<>(results.values()), when);
    }

    /**
     * A load that returned with records left out: a format-1 or format-2 import goes on past a
     * record it cannot resolve. The file is not in the store whole, so it is refused like one that
     * threw.
     */
    public static final class IncompleteLoadException extends IllegalStateException {
        IncompleteLoadException(String message) {
            super(message);
        }
    }

    /** Loads one file whole and registers the load. */
    private static Result loaded(Path file, String checksum, Outcome outcome, Optional<String> firstRefusal) {
        LoadEntitiesFromProtobufFile loader = new LoadEntitiesFromProtobufFile(file.toFile());
        EntityCountSummary counts = loader.compute();
        if (!loader.complete()) {
            List<String> failures = loader.failures();
            throw new IncompleteLoadException(failures.isEmpty()
                    ? String.format("%,d of %,d record(s) imported", counts.getTotalCount(), loader.expectedCount())
                    : failures.getFirst() + (failures.size() > 1 ? String.format(" (and %,d more)", failures.size() - 1) : ""));
        }
        Optional<Long> registration = Registry.registerLoad(file, checksum, Instant.now());
        LOG.info("Loaded {} ({} record(s)){}", file.getFileName(), String.format("%,d", counts.getTotalCount()),
                registration.isPresent() ? ", registered" : "");
        return new Result(file, checksum, outcome, Optional.of(counts), firstRefusal, registration);
    }

    /** Why a load failed: the deepest cause's message, else the exception itself. */
    static String reason(Throwable failure) {
        Throwable cause = failure;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.toString() : message;
    }
}
