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
package dev.ikm.tinkar.entity.changeset;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipException;
import java.util.zip.ZipFile;

/**
 * Change sets upgraded in place against a knowledge base (IKE-Network/ike-issues#1313): every
 * format-1 or format-2 change set, and every format-3 one whose reference table names no pattern
 * for a component it refers to, is rewritten in format 3 by {@link ChangeSetCompaction}, which
 * asks the knowledge base for each referenced component's pattern. A store that mints nids by
 * pattern, such as the 64-bit spined-array store, then loads them with no base.
 *
 * <p>Three steps, so a person can look before anything changes:
 * <ol>
 *   <li>{@link #plan}: each file is assessed and, when it needs it, converted into a work
 *       folder and verified. Nothing chosen is touched.
 *   <li>{@link Plan#archive}: the originals that will be replaced go into one dated
 *       {@code .tar.gz} outside the store, under their absolute paths, with a SHA-256 manifest
 *       and an AsciiDoc README, never as loose {@code .zip} files a scan for change sets would
 *       pick up.
 *   <li>{@link Plan#replace}: each original whose bytes still match the archive's hash is
 *       replaced by its upgrade.
 * </ol>
 *
 * <p>A change set whose upgrade still refers to a component the knowledge base does not hold is
 * left as it was, and the components are named: a pattern is never guessed, and the same file
 * may upgrade against another base.
 */
public final class ChangeSetUpgrade {

    /** The name of the archive's folder and file, after the date. */
    public static final String ARCHIVE_SUFFIX = "-change-set-originals";

    /** The first of the names a component with no pattern is reported by, per change set. */
    private static final int NAMED_PER_CHANGE_SET = 5;

    private ChangeSetUpgrade() {
    }

    /** What a file needs. */
    public enum Need {
        /** A zero-byte file, such as a session that wrote nothing: not a change set. */
        EMPTY("empty"),
        /** Not a zip with a change-set manifest. */
        NOT_A_CHANGE_SET("not a change set"),
        /** Format 1: no format version in the manifest. */
        FORMAT_1("format 1"),
        /** Format 2. */
        FORMAT_2("format 2"),
        /** Format 3 whose reference table names no pattern for at least one component. */
        FORMAT_3_WITHOUT_PATTERNS("format 3 without patterns"),
        /** Format 3 with a pattern for every component: nothing to do. */
        CURRENT("current");

        private final String label;

        Need(String label) {
            this.label = label;
        }

        /** @return how a person reads it, such as {@code format 1} */
        public String label() {
            return label;
        }

        /** @return true when an upgrade rewrites the file */
        public boolean upgrades() {
            return this == FORMAT_1 || this == FORMAT_2 || this == FORMAT_3_WITHOUT_PATTERNS;
        }
    }

    /**
     * A file and what it needs.
     *
     * @param file   the file
     * @param need   what it needs
     * @param detail why, for a file that is not a change set; empty otherwise
     */
    public record Assessment(Path file, Need need, String detail) {
    }

    /**
     * A change set converted into the work folder.
     *
     * @param original the change set as chosen
     * @param upgraded its format-3 upgrade, in the work folder
     * @param was      what it needed
     * @param result   what the compaction reported
     */
    public record Converted(Path original, Path upgraded, Need was, ChangeSetCompaction.Result result) {
        /** @return true when every reference names a pattern, so the upgrade may replace the original */
        public boolean complete() {
            return result.withoutPattern().isEmpty();
        }
    }

    /**
     * A change set that could not be converted, and why.
     *
     * @param file   the change set
     * @param reason what went wrong
     */
    public record Failure(Path file, String reason) {
    }

    /** Told of each change set as it is converted, counting from 1. */
    @FunctionalInterface
    public interface Progress {
        void converting(int index, int count, Path changeSet);
    }

    /**
     * The change sets among what was chosen: a folder's own {@code .zip} files, not those of its
     * subfolders, and every file chosen by itself, in name order within each folder.
     *
     * @param chosen folders and files
     * @return the files, each once
     * @throws IOException if a folder cannot be listed
     */
    public static List<Path> changeSets(List<Path> chosen) throws IOException {
        Map<Path, Path> files = new LinkedHashMap<>();
        for (Path path : chosen) {
            if (Files.isDirectory(path)) {
                try (Stream<Path> listed = Files.list(path)) {
                    listed.filter(Files::isRegularFile)
                            .filter(file -> file.getFileName().toString().endsWith(".zip"))
                            .filter(file -> !file.getFileName().toString().startsWith("."))
                            .sorted(Comparator.comparing(file -> file.getFileName().toString()))
                            .forEach(file -> files.putIfAbsent(file.toAbsolutePath().normalize(), file));
                }
            } else {
                files.putIfAbsent(path.toAbsolutePath().normalize(), path);
            }
        }
        return List.copyOf(files.keySet());
    }

    /**
     * What a file needs. Reads the manifest and, for format 3, the reference table; never the
     * records.
     *
     * @param file the file
     * @return its assessment
     */
    public static Assessment assess(Path file) {
        try {
            if (Files.size(file) == 0) {
                return new Assessment(file, Need.EMPTY, "");
            }
        } catch (IOException e) {
            return new Assessment(file, Need.NOT_A_CHANGE_SET, "cannot be read: " + e.getMessage());
        }
        try (ZipFile zip = new ZipFile(file.toFile())) {
            Optional<Manifest> manifest = ChangeSetFormat.manifest(zip);
            if (manifest.isEmpty()) {
                return new Assessment(file, Need.NOT_A_CHANGE_SET, "a zip with no " + ChangeSetFormat.MANIFEST);
            }
            int version = ChangeSetFormat.version(manifest.get().getMainAttributes(), file.getFileName().toString());
            return switch (version) {
                case 1 -> new Assessment(file, Need.FORMAT_1, "");
                case 2 -> new Assessment(file, Need.FORMAT_2, "");
                default -> new Assessment(file, namesEveryPattern(zip) ? Need.CURRENT : Need.FORMAT_3_WITHOUT_PATTERNS, "");
            };
        } catch (ZipException e) {
            return new Assessment(file, Need.NOT_A_CHANGE_SET, "not a zip: " + e.getMessage());
        } catch (IOException | RuntimeException e) {
            return new Assessment(file, Need.NOT_A_CHANGE_SET, e.getMessage());
        }
    }

    /** Whether every component of a format-3 table names its pattern: sequence 0 names none. */
    private static boolean namesEveryPattern(ZipFile zip) throws IOException {
        boolean[] all = {true};
        ComponentTable.forEach(zip, component -> {
            if (component.patternSequence() == 0) {
                all[0] = false;
            }
        });
        return all[0];
    }

    /**
     * Assesses each file and converts the change sets that need it into {@code workFolder},
     * verifying each upgrade. Nothing chosen is changed.
     *
     * @param files      the files, as {@link #changeSets} lists them
     * @param patternOf  the pattern of a component by its UUIDs, from the knowledge base;
     *                   {@link ChangeSetCompaction#patternsFromStore()} for the running store
     * @param base       the knowledge base, as the report names it
     * @param workFolder an empty folder the upgrades are written to
     * @param progress   told of each conversion
     * @return the plan
     * @throws IOException if the work folder cannot be written
     */
    public static Plan plan(List<Path> files, Function<UUID[], Optional<UUID[]>> patternOf, String base,
                            Path workFolder, Progress progress) throws IOException {
        List<Assessment> assessments = files.stream().map(ChangeSetUpgrade::assess).toList();
        List<Assessment> toUpgrade = assessments.stream().filter(assessment -> assessment.need().upgrades()).toList();
        List<Converted> converted = new ArrayList<>();
        List<Failure> failed = new ArrayList<>();
        Files.createDirectories(workFolder);
        for (int i = 0; i < toUpgrade.size(); i++) {
            Assessment assessment = toUpgrade.get(i);
            progress.converting(i + 1, toUpgrade.size(), assessment.file());
            // One folder per change set: two chosen from different folders may share a name.
            Path target = Files.createDirectories(workFolder.resolve(Integer.toString(i + 1)))
                    .resolve(assessment.file().getFileName().toString());
            try {
                ChangeSetCompaction.Result result = new ChangeSetCompaction(assessment.file().toFile(), target.toFile(),
                        patternOf).call();
                ChangeSetVerification.Verification verification = new ChangeSetVerification(target.toFile()).call();
                if (!verification.ok()) {
                    failed.add(new Failure(assessment.file(), "the upgrade does not verify: " + verification.errors()));
                    Files.deleteIfExists(target);
                } else {
                    converted.add(new Converted(assessment.file(), target, assessment.need(), result));
                }
            } catch (Exception e) {
                failed.add(new Failure(assessment.file(), e.getMessage() == null ? e.toString() : e.getMessage()));
                Files.deleteIfExists(target);
            }
        }
        return new Plan(base, workFolder, assessments, converted, failed);
    }

    /**
     * What an upgrade found and made, before anything chosen changes.
     *
     * @param base        the knowledge base the patterns came from
     * @param workFolder  where the upgrades are
     * @param assessments every file chosen, assessed
     * @param converted   the change sets converted, complete or not
     * @param failed      the change sets that could not be converted
     */
    public record Plan(String base, Path workFolder, List<Assessment> assessments, List<Converted> converted,
                       List<Failure> failed) {

        public Plan {
            assessments = List.copyOf(assessments);
            converted = List.copyOf(converted);
            failed = List.copyOf(failed);
        }

        /** @return the upgrades that may replace their originals: every reference names a pattern */
        public List<Converted> replaceable() {
            return converted.stream().filter(Converted::complete).toList();
        }

        /** @return the change sets left as they were, naming components the base does not hold */
        public List<Converted> incomplete() {
            return converted.stream().filter(upgrade -> !upgrade.complete()).toList();
        }

        /** @return true when nothing is left without a pattern and nothing failed */
        public boolean clean() {
            return incomplete().isEmpty() && failed.isEmpty();
        }

        /** @return how many files were assessed as needing {@code need} */
        public long count(Need need) {
            return assessments.stream().filter(assessment -> assessment.need() == need).count();
        }

        /**
         * The report, in Markdown: the counts, then each change set converted, left as it was,
         * failed, or not a change set. Empty files are counted, not listed: a store can hold a
         * hundred thousand of them.
         *
         * @return the report
         */
        public String markdown() {
            StringBuilder text = new StringBuilder("# Change-set upgrade\n\n");
            text.append("Knowledge base: ").append(base).append("\n\n");
            text.append("- ").append(assessments.size()).append(" file(s) chosen\n");
            text.append("- ").append(replaceable().size()).append(" change set(s) upgraded to format 3")
                    .append(byNeed(replaceable())).append("\n");
            text.append("- ").append(count(Need.CURRENT)).append(" already current\n");
            text.append("- ").append(count(Need.EMPTY)).append(" empty file(s) skipped (sessions that wrote nothing)\n");
            text.append("- ").append(count(Need.NOT_A_CHANGE_SET)).append(" other file(s) skipped, not change sets\n");
            text.append("- ").append(incomplete().size())
                    .append(" left as they were, naming components the knowledge base does not hold\n");
            if (!failed.isEmpty()) {
                text.append("- ").append(failed.size()).append(" failed\n");
            }
            if (!incomplete().isEmpty()) {
                text.append("\n## Left as they were\n\n");
                text.append("No pattern is guessed. These may upgrade against a knowledge base that holds the components.\n\n");
                for (Converted upgrade : incomplete()) {
                    List<UUID> names = upgrade.result().withoutPattern();
                    text.append("- `").append(upgrade.original().getFileName()).append("` (").append(upgrade.was().label())
                            .append("): ").append(String.format("%,d", names.size())).append(" component(s) with no pattern: ")
                            .append(names.subList(0, Math.min(NAMED_PER_CHANGE_SET, names.size())).stream()
                                    .map(uuid -> "`" + uuid + "`").reduce((a, b) -> a + ", " + b).orElse(""))
                            .append(names.size() > NAMED_PER_CHANGE_SET ? " …" : "").append("\n");
                }
            }
            if (!failed.isEmpty()) {
                text.append("\n## Failed\n\n");
                failed.forEach(failure -> text.append("- `").append(failure.file().getFileName()).append("`: ")
                        .append(failure.reason()).append("\n"));
            }
            if (!replaceable().isEmpty()) {
                text.append("\n## Upgraded\n\n");
                for (Converted upgrade : replaceable()) {
                    text.append("- `").append(upgrade.original().getFileName()).append("` (").append(upgrade.was().label())
                            .append("): ").append(String.format("%,d", upgrade.result().records())).append(" record(s), ")
                            .append(String.format("%,d", upgrade.result().referencedOnly())).append(" referenced only\n");
                }
            }
            List<Assessment> others = assessments.stream()
                    .filter(assessment -> assessment.need() == Need.NOT_A_CHANGE_SET).toList();
            if (!others.isEmpty()) {
                text.append("\n## Not change sets\n\n");
                others.forEach(other -> text.append("- `").append(other.file().getFileName()).append("`: ")
                        .append(other.detail()).append("\n"));
            }
            return text.toString();
        }

        private static String byNeed(List<Converted> upgrades) {
            Map<Need, Long> counts = new LinkedHashMap<>();
            upgrades.forEach(upgrade -> counts.merge(upgrade.was(), 1L, Long::sum));
            if (counts.isEmpty()) {
                return "";
            }
            return counts.entrySet().stream().map(entry -> entry.getValue() + " from " + entry.getKey().label())
                    .reduce((a, b) -> a + ", " + b).map(text -> " (" + text + ")").orElse("");
        }

        /**
         * Archives the originals the upgrade will replace: one {@code <date>-change-set-originals.tar.gz}
         * in {@code archiveFolder} ({@code -2}, {@code -3} … when the name is taken), holding the
         * folder of that name with {@code README.adoc}, {@code manifest-sha256.txt} and every original
         * under {@code root/} at its absolute path.
         *
         * @param archiveFolder where the archive goes, outside every store
         * @param date          the date it is named for
         * @return the archive
         * @throws IOException if it cannot be written
         */
        public Archive archive(Path archiveFolder, LocalDate date) throws IOException {
            Files.createDirectories(archiveFolder);
            String name = date + ARCHIVE_SUFFIX;
            for (int n = 2; Files.exists(archiveFolder.resolve(name + ".tar.gz")); n++) {
                name = date + ARCHIVE_SUFFIX + "-" + n;
            }
            Path file = archiveFolder.resolve(name + ".tar.gz");
            Map<Path, String> hashes = new LinkedHashMap<>();
            for (Converted upgrade : replaceable()) {
                hashes.put(upgrade.original().toAbsolutePath().normalize(), sha256(upgrade.original()));
            }
            StringBuilder manifest = new StringBuilder();
            hashes.forEach((original, hash) -> manifest.append(hash).append("  ").append(original).append("\n"));
            Path partial = archiveFolder.resolve(name + ".tar.gz.partial");
            try (TarGzWriter tar = new TarGzWriter(partial, System.currentTimeMillis() / 1000)) {
                tar.folder(name);
                tar.text(name + "/README.adoc", readme(name, date, hashes.keySet()));
                tar.text(name + "/manifest-sha256.txt", manifest.toString());
                tar.folder(name + "/root");
                for (Path original : hashes.keySet()) {
                    tar.file(name + "/root/" + rootRelative(original), original);
                }
            } catch (IOException | RuntimeException e) {
                Files.deleteIfExists(partial);
                throw e;
            }
            Files.move(partial, file, StandardCopyOption.ATOMIC_MOVE);
            return new Archive(file, Map.copyOf(hashes));
        }

        private String readme(String name, LocalDate date, java.util.Collection<Path> originals) {
            Map<Path, List<Converted>> byFolder = new LinkedHashMap<>();
            replaceable().forEach(upgrade -> byFolder.computeIfAbsent(
                    upgrade.original().toAbsolutePath().normalize().getParent(), _ -> new ArrayList<>()).add(upgrade));
            StringBuilder text = new StringBuilder("= Change-set originals, ").append(date).append("\n:toc:\n\n");
            text.append("The change sets below were rewritten in format 3 on ").append(date)
                    .append(" against the knowledge base\n").append(base)
                    .append(",\nso they load in a store that mints nids by pattern with no base\n")
                    .append("(IKE-Network/ike-issues#1313). These are the files as they were before. They are kept\n")
                    .append("here, compressed and outside every store, so nothing that scans for change sets sees them.\n\n");
            text.append("== Restoring\n\nEverything under `root/` sits at its original absolute path:\n\n[source,bash]\n----\n")
                    .append("tar -xzf ").append(name).append(".tar.gz\ncd ").append(name).append("/root\n")
                    .append("# copy back what you need, for example one change set:\n")
                    .append(originals.stream().findFirst().map(original -> "cp -p \"" + rootRelative(original) + "\" \""
                            + original.getParent() + "/\"\n").orElse("")).append("----\n\n")
                    .append("`manifest-sha256.txt` lists the SHA-256 of every original.\n\n");
            text.append("== What is here\n\n[cols=\"3,1,3\"]\n|===\n|Original folder |Files |Formats\n");
            byFolder.forEach((folder, upgrades) -> {
                text.append("\n|`").append(folder).append("`\n|").append(upgrades.size()).append("\n|");
                text.append(byNeed(upgrades).replaceFirst("^ \\((.*)\\)$", "$1")).append("\n");
            });
            text.append("|===\n\nEach of the ").append(originals.size())
                    .append(" upgrade(s) was verified before it replaced its original.\n");
            return text.toString();
        }

        /**
         * Replaces each archived original by its upgrade, keeping the original's modification
         * time. An original whose bytes no longer match its archived hash is left alone and
         * reported, and so is every original once one replacement fails. The work folder is
         * removed when every upgrade has been placed.
         *
         * @param archive the archive {@link #archive} wrote
         * @return what was replaced
         * @throws IOException if the work folder cannot be read
         */
        public Replacement replace(Archive archive) throws IOException {
            List<Path> replaced = new ArrayList<>();
            List<Failure> skipped = new ArrayList<>();
            for (Converted upgrade : replaceable()) {
                Path original = upgrade.original().toAbsolutePath().normalize();
                String archived = archive.hashes().get(original);
                if (archived == null) {
                    skipped.add(new Failure(original, "not in the archive"));
                    continue;
                }
                if (!archived.equals(sha256(original))) {
                    skipped.add(new Failure(original, "changed since it was archived"));
                    continue;
                }
                FileTime modified = Files.getLastModifiedTime(original);
                Files.move(upgrade.upgraded(), original, StandardCopyOption.REPLACE_EXISTING);
                Files.setLastModifiedTime(original, modified);
                replaced.add(original);
            }
            if (skipped.isEmpty()) {
                deleteRecursively(workFolder);
            }
            return new Replacement(archive.file(), replaced, skipped);
        }

        /** Removes the work folder without replacing anything. */
        public void discard() throws IOException {
            deleteRecursively(workFolder);
        }
    }

    /**
     * An archive of originals.
     *
     * @param file   the {@code .tar.gz}
     * @param hashes each original's SHA-256, by absolute path
     */
    public record Archive(Path file, Map<Path, String> hashes) {
    }

    /**
     * What a replacement did.
     *
     * @param archive  where the originals are
     * @param replaced the change sets replaced by their upgrades
     * @param skipped  the ones left as they were, and why
     */
    public record Replacement(Path archive, List<Path> replaced, List<Failure> skipped) {

        public Replacement {
            replaced = List.copyOf(replaced);
            skipped = List.copyOf(skipped);
        }

        /** @return the report, in Markdown */
        public String markdown() {
            StringBuilder text = new StringBuilder("## Replaced\n\n");
            text.append(replaced.size()).append(" change set(s) replaced by their upgrades; the originals are in\n`")
                    .append(archive).append("`.\n");
            if (!skipped.isEmpty()) {
                text.append("\nLeft as they were:\n\n");
                skipped.forEach(skip -> text.append("- `").append(skip.file().getFileName()).append("`: ")
                        .append(skip.reason()).append("\n"));
            }
            return text.toString();
        }
    }

    /** A path under the archive's {@code root/}: absolute, without its root, with {@code /}. */
    static String rootRelative(Path absolute) {
        Path root = absolute.getRoot();
        StringBuilder relative = new StringBuilder();
        if (root != null && !root.toString().equals("/")) {
            // A Windows drive, C:\ as C/.
            relative.append(root.toString().replace(":", "").replace("\\", "").replace("/", "")).append('/');
        }
        Path rest = root == null ? absolute : root.relativize(absolute);
        for (int i = 0; i < rest.getNameCount(); i++) {
            relative.append(i == 0 ? "" : "/").append(rest.getName(i));
        }
        return relative.toString();
    }

    static String sha256(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[1 << 16];
            int read;
            while ((read = in.read(buffer)) > 0) {
                digest.update(buffer, 0, read);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is missing from this JDK", e);
        }
    }

    private static void deleteRecursively(Path folder) throws IOException {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            for (Path path : walk.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }
}
