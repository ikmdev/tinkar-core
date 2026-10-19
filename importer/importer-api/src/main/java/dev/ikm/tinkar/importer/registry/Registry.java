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

import dev.ikm.tinkar.common.id.LongIdList;
import dev.ikm.tinkar.common.id.LongIdSet;
import dev.ikm.tinkar.common.id.LongIds;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.importer.ImportReport;
import dev.ikm.tinkar.importer.KnowledgeImporter;
import dev.ikm.tinkar.importer.StoreWriter;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import org.eclipse.collections.api.factory.Lists;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Registers what a knowledge base is made of, inside the knowledge base
 * (IKE-Network/ike-issues#1304): every release imported into it, every assembly that composed it,
 * and every export taken from it. One implementation for every tool that imports, assembles or
 * exports: the knowledge provider behind the Maven goals, and Komet.
 *
 * <p>Every registration is written by {@link KernelConcept#REGISTRAR the IKE Registrar}, in
 * {@link KernelConcept#REGISTRY_MODULE the Registry module}, on {@link KernelConcept#REGISTRY_PATH
 * the Registry path}, whatever path the content it describes is on, and refers to its own pattern.
 *
 * <ul>
 * <li><b>Release registration</b> ({@link KernelPattern#RELEASE_REGISTRATION_PATTERN}): identified
 * by the importer's name, the options that change what it writes, and the release's SHA-256, so the
 * same release read the same way is one registration. Stamped at the publisher's release date, so
 * importing it again writes nothing new.</li>
 * <li><b>Assembly registration</b> ({@link KernelPattern#ASSEMBLY_REGISTRATION_PATTERN}):
 * identified by the ordered checksums of the assembly's inputs, stamped when it ran. Rebuilding from
 * the same inputs is a new version; different inputs, a new registration.</li>
 * <li><b>Export registration</b> ({@link KernelPattern#EXPORT_REGISTRATION_PATTERN}): identified by
 * the checksum of what was written, stamped when it ran.</li>
 * </ul>
 *
 * <p>A store whose starter set predates the registry gets no registrations: every method answers
 * empty, and {@link #declared()} says why.
 */
public final class Registry {

    /** The namespace registration identities derive from: the IKE starter set's. */
    private static final UUID NAMESPACE = UUID.fromString("d890e06f-ec35-429a-b541-d0ead19695e2");

    private Registry() {
    }

    // ── What the store declares ──────────────────────────────────────────────

    /**
     * Whether the store declares the registry: the Registrar, the Registry module and path, and the
     * three registration patterns.
     *
     * @return true when registrations can be written
     */
    public static boolean declared() {
        return present(KernelConcept.REGISTRAR.publicId()) && present(KernelConcept.REGISTRY_MODULE.publicId())
                && present(KernelConcept.REGISTRY_PATH.publicId())
                && present(KernelPattern.RELEASE_REGISTRATION_PATTERN.publicId())
                && present(KernelPattern.ASSEMBLY_REGISTRATION_PATTERN.publicId())
                && present(KernelPattern.EXPORT_REGISTRATION_PATTERN.publicId());
    }

    private static boolean present(PublicId publicId) {
        return PrimitiveData.get().hasPublicId(publicId)
                && EntityHandle.get(PrimitiveData.nid(publicId)).entity().isPresent();
    }

    /**
     * The release registrations the store holds, by nid, in nid order.
     *
     * @return their nids; empty when the store does not declare the registry
     */
    public static Set<Long> releaseRegistrations() {
        if (!present(KernelPattern.RELEASE_REGISTRATION_PATTERN.publicId())) {
            return Set.of();
        }
        Set<Long> nids = ConcurrentHashMap.newKeySet();
        EntityService.get().forEachSemanticOfPattern(KernelPattern.RELEASE_REGISTRATION_PATTERN.nid(),
                semantic -> nids.add(semantic.nid()));
        return new java.util.TreeSet<>(nids);
    }

    // ── Identities ───────────────────────────────────────────────────────────

    /**
     * A release registration's identity: the importer, the options that change what it writes, and
     * the release's checksum.
     *
     * @param importer      the importer's name
     * @param outputOptions the options that change what it writes
     * @param checksum      the release's checksum, {@code sha256:<hex>}
     * @return the identity
     */
    public static UUID releaseRegistrationUuid(String importer, SortedMap<String, String> outputOptions,
                                               String checksum) {
        return UuidT5Generator.get(NAMESPACE, "Release registration: " + importer + "\n" + options(outputOptions)
                + "\n" + checksum);
    }

    /**
     * An assembly registration's identity: its inputs' checksums, in load order.
     *
     * @param inputs the inputs, in load order
     * @return the identity
     */
    public static UUID assemblyRegistrationUuid(List<AssemblyInput> inputs) {
        return UuidT5Generator.get(NAMESPACE, "Assembly registration: "
                + inputs.stream().map(AssemblyInput::checksum).collect(Collectors.joining("\n")));
    }

    /**
     * An export registration's identity: the checksum of what was written.
     *
     * @param outputChecksum the checksum, {@code sha256:<hex>}
     * @return the identity
     */
    public static UUID exportRegistrationUuid(String outputChecksum) {
        return UuidT5Generator.get(NAMESPACE, "Export registration: " + outputChecksum);
    }

    // ── Release registration ─────────────────────────────────────────────────

    /**
     * What a release registration records about the release itself, beyond what the import reports.
     *
     * @param artifact        the release as it was named: a Maven coordinate as written, or a file name
     * @param resolvedVersion the version the coordinate resolved to, for a snapshot its timestamped
     *                        version; empty for a release named by file
     * @param checksum        the release's checksum, {@code sha256:<hex>}
     */
    public record Release(String artifact, Optional<String> resolvedVersion, String checksum) {

        /**
         * Validates.
         *
         * @param artifact        the release as named
         * @param resolvedVersion the resolved version, if any
         * @param checksum        the checksum
         */
        public Release {
            Objects.requireNonNull(artifact, "artifact");
            Objects.requireNonNull(resolvedVersion, "resolvedVersion");
            Objects.requireNonNull(checksum, "checksum");
        }
    }

    /**
     * Registers a release import. Written after the import, in the store it wrote to, under a
     * Registry stamp at the publisher's release date; when the importer reports no release date, at
     * the latest time among the stamps it wrote under.
     *
     * <p>Registering the same release, read the same way, by the same importer version writes
     * nothing: the registration already says so. A different importer version re-reading it is a
     * new version of the registration, stamped when that import ran, so the stamp history shows
     * which importers have read the release and when.
     *
     * @param release       the release
     * @param importer      the importer that read it
     * @param options       the options the import ran with
     * @param report        what the import reported
     * @param stampsWritten the stamps the import wrote under, by nid
     * @param base          the release registrations the store held before the import, by nid
     * @return the Registry stamp the registration was written under, by nid; empty when the store does
     *         not declare the registry, or the import wrote under no stamp
     */
    public static Optional<Long> registerRelease(Release release, KnowledgeImporter importer,
                                                 Map<String, String> options, ImportReport report,
                                                 Set<Long> stampsWritten, Set<Long> base) {
        if (!declared() || stampsWritten.isEmpty()) {
            return Optional.empty();
        }
        SortedMap<String, String> outputOptions = new TreeMap<>();
        options.forEach((name, value) -> {
            if (importer.changesOutput(name)) {
                outputOptions.put(name, value);
            }
        });
        Instant date = report.releaseDate().orElseGet(() -> Instant.ofEpochMilli(stampsWritten.stream()
                .mapToLong(nid -> ((StampEntity<?>) EntityService.get().getStampFast(nid)).time())
                .filter(time -> time != Long.MAX_VALUE)
                .max()
                .orElseThrow()));
        PublicId identity = PublicIds.of(releaseRegistrationUuid(importer.name(), outputOptions, release.checksum()));
        String importerVersion = importerVersion(importer);
        long time = date.toEpochMilli();
        Optional<SemanticEntityVersion> latest = latest(identity);
        if (latest.isPresent()) {
            if (importerVersion.equals(latest.get().fieldValues().get(IMPORTER_FIELD))) {
                return Optional.of(latest.get().stampNid());
            }
            time = System.currentTimeMillis();
        }
        Stamp stamp = registryStamp(time);
        StoreWriter writer = writer();
        writer.semantic(stamp, identity,
                KernelPattern.RELEASE_REGISTRATION_PATTERN, KernelPattern.RELEASE_REGISTRATION_PATTERN.nid(),
                Lists.immutable.of(report.sourceVersion(), date, release.artifact(),
                        release.resolvedVersion().orElse(""), release.checksum(), importerVersion,
                        options(outputOptions), set(stampsWritten), report.written(), report.versioned(),
                        report.unchanged(), report.retired(), set(base)));
        return Optional.of(writer.stamp(stamp));
    }

    // ── Assembly registration ────────────────────────────────────────────────

    /**
     * One input of an assembly.
     *
     * @param role            its role ({@code PB}, {@code CHANGESET}, {@code SOURCE}, ...)
     * @param artifact        the input as it was named: a Maven coordinate as written, or a file name
     * @param resolvedVersion the version the coordinate resolved to; empty for an input named by file
     * @param checksum        its checksum, {@code sha256:<hex>}
     */
    public record AssemblyInput(String role, String artifact, Optional<String> resolvedVersion, String checksum) {

        /**
         * Validates.
         *
         * @param role            the role
         * @param artifact        the input as named
         * @param resolvedVersion the resolved version, if any
         * @param checksum        the checksum
         */
        public AssemblyInput {
            Objects.requireNonNull(role, "role");
            Objects.requireNonNull(artifact, "artifact");
            Objects.requireNonNull(resolvedVersion, "resolvedVersion");
            Objects.requireNonNull(checksum, "checksum");
        }

        /** One line of the registration's inputs field. */
        String line() {
            return role + " " + artifact + resolvedVersion.map(version -> " " + version).orElse("") + " " + checksum;
        }
    }

    /**
     * Registers an assembly: a new version each time, stamped when it ran.
     *
     * @param inputs     the inputs, in load order
     * @param releases   the release registrations the store holds, in the order they arrived, by nid
     * @param classifier the classifier that ran, if any
     * @param when       when the assembly ran
     * @return the Registry stamp the registration was written under, by nid; empty when the store does
     *         not declare the registry
     */
    public static Optional<Long> registerAssembly(List<AssemblyInput> inputs, List<Long> releases,
                                                  Optional<String> classifier, Instant when) {
        if (!declared()) {
            return Optional.empty();
        }
        Stamp stamp = registryStamp(when.toEpochMilli());
        StoreWriter writer = writer();
        writer.recordSemantic(stamp, PublicIds.of(assemblyRegistrationUuid(inputs)),
                KernelPattern.ASSEMBLY_REGISTRATION_PATTERN, KernelPattern.ASSEMBLY_REGISTRATION_PATTERN.nid(),
                Lists.immutable.of(inputs.stream().map(AssemblyInput::line).collect(Collectors.joining("\n")),
                        list(releases), classifier.orElse("")));
        return Optional.of(writer.stamp(stamp));
    }

    /**
     * Registers a load of protobuf files into an open store, outside an assembly (Komet's import):
     * an assembly registration of those files, the release registrations the store then holds, in
     * nid order, and no classifier.
     *
     * @param files the files loaded, in load order
     * @param when  when the load ran
     * @return the Registry stamp the registration was written under, by nid; empty when the store does
     *         not declare the registry
     */
    public static Optional<Long> registerLoad(List<Path> files, Instant when) {
        if (!declared()) {
            return Optional.empty();
        }
        return registerAssembly(files.stream()
                        .map(file -> new AssemblyInput("PB", String.valueOf(file.getFileName()), Optional.empty(),
                                checksum(file)))
                        .toList(),
                List.copyOf(releaseRegistrations()), Optional.empty(), when);
    }

    // ── Export registration ──────────────────────────────────────────────────

    /**
     * Registers an export, after it is written: a new version each time, stamped when it ran.
     *
     * @param file       the file the export wrote
     * @param selection  what it selected, as text: its time window, modules, or other filter
     * @param components how many components it wrote
     * @param when       when the export ran
     * @return the Registry stamp the registration was written under, by nid; empty when the store does
     *         not declare the registry
     */
    public static Optional<Long> registerExport(Path file, String selection, long components, Instant when) {
        if (!declared()) {
            return Optional.empty();
        }
        String checksum = checksum(file);
        Stamp stamp = registryStamp(when.toEpochMilli());
        StoreWriter writer = writer();
        writer.recordSemantic(stamp, PublicIds.of(exportRegistrationUuid(checksum)),
                KernelPattern.EXPORT_REGISTRATION_PATTERN, KernelPattern.EXPORT_REGISTRATION_PATTERN.nid(),
                Lists.immutable.of(String.valueOf(file.getFileName()), selection, checksum, components));
        return Optional.of(writer.stamp(stamp));
    }

    // ── Shared ───────────────────────────────────────────────────────────────

    /**
     * The SHA-256 of a file, or of a directory's files in path order (each relative path, then its
     * content), as {@code sha256:<hex>}.
     *
     * @param path the file or directory
     * @return its checksum
     */
    public static String checksum(Path path) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            if (Files.isDirectory(path)) {
                List<Path> files;
                try (Stream<Path> walk = Files.walk(path)) {
                    files = walk.filter(Files::isRegularFile).sorted(Comparator.comparing(Path::toString)).toList();
                }
                for (Path file : files) {
                    digest.update(path.relativize(file).toString().replace('\\', '/').getBytes(StandardCharsets.UTF_8));
                    digestFile(digest, file);
                }
            } else {
                digestFile(digest, path);
            }
            return "sha256:" + HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable", e);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot checksum " + path, e);
        }
    }

    private static void digestFile(MessageDigest digest, Path file) throws IOException {
        try (InputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
            in.transferTo(java.io.OutputStream.nullOutputStream());
        }
    }

    /** The importer's name and version: its jar's implementation version, else the jar's file name. */
    static String importerVersion(KnowledgeImporter importer) {
        String version = importer.getClass().getPackage().getImplementationVersion();
        if (version != null) {
            return importer.name() + " " + version;
        }
        try {
            Path location = Path.of(importer.getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            return importer.name() + " (" + location.getFileName() + ")";
        } catch (Exception e) {
            return importer.name();
        }
    }

    /** The release registration's Importer field, by position. */
    private static final int IMPORTER_FIELD = 5;

    /** A semantic's latest committed version, by stamp time; empty when the store has none. */
    private static Optional<SemanticEntityVersion> latest(PublicId identity) {
        if (!PrimitiveData.get().hasPublicId(identity)) {
            return Optional.empty();
        }
        return EntityHandle.get(PrimitiveData.nid(identity)).entity()
                .filter(SemanticEntity.class::isInstance)
                .flatMap(entity -> ((SemanticEntity<?>) entity).versions().stream()
                        .map(version -> (SemanticEntityVersion) version)
                        .filter(version -> version.stamp().time() != Long.MAX_VALUE)
                        .max(Comparator.comparingLong(version -> version.stamp().time())));
    }

    private static Stamp registryStamp(long time) {
        return Stamp.active(time, KernelConcept.REGISTRAR, KernelConcept.REGISTRY_MODULE, KernelConcept.REGISTRY_PATH);
    }

    private static StoreWriter writer() {
        return new StoreWriter(Calculators.Stamp.DevelopmentLatest());
    }

    private static String options(SortedMap<String, String> options) {
        List<String> lines = new ArrayList<>();
        options.forEach((name, value) -> lines.add(name + "=" + value));
        return String.join("\n", lines);
    }

    private static LongIdSet set(Set<Long> nids) {
        return LongIds.set.of(nids.stream().mapToLong(Long::longValue).toArray());
    }

    private static LongIdList list(List<Long> nids) {
        return LongIds.list.of(nids.stream().mapToLong(Long::longValue).toArray());
    }
}
