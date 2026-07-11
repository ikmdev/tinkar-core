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
package dev.ikm.tinkar.entity.builder;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.common.service.EntityCountSummary;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ServiceKeys;
import dev.ikm.tinkar.common.service.ServiceProperties;
import dev.ikm.tinkar.entity.aggregator.AllowlistEntityAggregator;
import dev.ikm.tinkar.entity.aggregator.EntityAggregator;
import dev.ikm.tinkar.entity.aggregator.TemporalEntityAggregator;
import dev.ikm.tinkar.entity.export.ExportEntitiesToProtobufFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.UUID;

/**
 * The change-set export entry point build tooling invokes: discovers the project's
 * {@link KnowledgeSetSource}, composes it, replays the session into a fresh ephemeral
 * store, and exports the store as a protobuf change set — the released form of a
 * knowledge set. A starter set is exactly this artifact applied to an empty base.
 * <p>
 * This is the stable reflective seam for the {@code ike:knowledge-export} Maven goal.
 * Unlike bindings generation, export replays into a store, so the classpath must carry
 * the ephemeral, entity, and executor providers — and, because the goal runs on the
 * classpath, legacy {@code META-INF/services} registrations for their controllers
 * (tinkar providers declare services in {@code module-info} only; the exporting module
 * supplies the registrations in its own resources).
 * <p>
 * The store is fresh and seeded only by the replay, so the export contains exactly the
 * set: its concepts, semantics, and declared stamps (aggregated by real time — epoch
 * zero onward — which excludes the store's startup sentinel stamp). Referenced
 * identities outside the set (for example {@code TinkarTerm} concepts) mint nids during
 * replay but are never written as entities, so they do not leak into the artifact.
 * Declared stamp times before 1970 are therefore not exportable — not a real
 * constraint for authored content.
 * <p>
 * Arguments: {@code outputFile [sourceClassName]}.
 *
 * @deprecated Replaced by the {@code ike-knowledge-spi} service contract
 * (IKE-Network/ike-issues#850): goals resolve a {@code KnowledgeBaseAssembler} /
 * {@code KnowledgeExporter} / {@code BindingsGenerator} implementation via
 * {@link java.util.ServiceLoader} instead of invoking this main by name. Retained only
 * while the {@code ike:} goals migrate; removal is tracked on the same issue. There are
 * no external users — this deprecation marks the iterative migration, not a
 * compatibility promise.
 */
@Deprecated
public final class ChangeSetMain {

    private ChangeSetMain() {
    }

    /**
     * Exports the composed knowledge set as a protobuf change-set file.
     *
     * @param args {@code outputFile [sourceClassName]}
     * @throws Exception if discovery, composition, replay, or export fails
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1 || args.length > 3) {
            throw new IllegalArgumentException(
                    "Usage: ChangeSetMain <outputFile> [konceptsYmlFile] [sourceClassName]");
        }
        Path outputFile = Path.of(args[0]);
        Files.createDirectories(outputFile.toAbsolutePath().getParent());

        // Optional second arg is the koncepts YAML output; an optional trailing arg names
        // the source class. Both are position-flexible: a value ending in .yml is the
        // koncepts file, anything else is the source class.
        Path konceptsYml = null;
        String sourceClassName = null;
        for (int i = 1; i < args.length; i++) {
            if (args[i].endsWith(".yml") || args[i].endsWith(".yaml")) {
                konceptsYml = Path.of(args[i]);
            } else {
                sourceClassName = args[i];
            }
        }

        KnowledgeSetSource source;
        if (sourceClassName != null) {
            source = (KnowledgeSetSource) Class.forName(sourceClassName, true, ChangeSetMain.class.getClassLoader())
                    .getDeclaredConstructor().newInstance();
        } else {
            List<KnowledgeSetSource> found = new ArrayList<>();
            ServiceLoader.load(KnowledgeSetSource.class, ChangeSetMain.class.getClassLoader())
                    .forEach(found::add);
            if (found.size() != 1) {
                throw new IllegalStateException("Expected exactly one KnowledgeSetSource on the classpath, found "
                        + found.size() + (found.isEmpty() ? "" : ": "
                        + found.stream().map(s -> s.getClass().getName()).toList())
                        + " — pass the implementation class name as an argument to select one");
            }
            source = found.getFirst();
        }

        Path storeRoot = Files.createTempDirectory("knowledge-export-store");
        CachingService.clearAll();
        ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, storeRoot.toFile());
        PrimitiveData.selectControllerByName("Load Ephemeral Store");
        PrimitiveData.start();
        try {
            KnowledgeSet knowledgeSet = source.compose();
            knowledgeSet.write();
            EntityCountSummary summary = new ExportEntitiesToProtobufFile(outputFile.toFile(),
                    selectAggregator()).compute();
            System.out.println("Change set written: " + outputFile + " — "
                    + summary.getTotalCount() + " entities ("
                    + summary.conceptCount() + " concepts, "
                    + summary.semanticCount() + " semantics, "
                    + summary.patternCount() + " patterns, "
                    + summary.stampCount() + " stamps) from " + source.getClass().getName());

            // The glossary is extracted from this same loaded store — one materialization,
            // both artifacts — rather than a parallel read of the ledger.
            if (konceptsYml != null) {
                Files.createDirectories(konceptsYml.toAbsolutePath().getParent());
                Files.writeString(konceptsYml, KonceptExtractor.extractYaml());
                System.out.println("Koncepts extracted: " + konceptsYml);
            }
        } finally {
            PrimitiveData.stop();
        }
    }

    /**
     * The export aggregator. Default (no system property): aggregate by real time (epoch &ge; 0) —
     * every declared stamp and its content, excluding the premundane {@code NONEXISTENT_STAMP} sentinel
     * the store mints at startup (whose module/author are absent in a store seeded only by this replay).
     * That is the behavior-preserving default for every existing export.
     *
     * <p>When {@code changeset.export.moduleAllowlist} is set (comma-separated module UUIDs), export
     * becomes a <b>filtered projection</b>: only content in the allowlisted modules crosses, optionally
     * narrowed to the paths in {@code changeset.export.pathAllowlist}, and refined by pattern via
     * {@code changeset.export.includePatterns} / {@code changeset.export.excludePatterns} (comma-separated
     * pattern UUIDs — e.g. exclude the layout patterns from a knowledge distribution). This is
     * default-deny — a module not named is not exported. See {@link AllowlistEntityAggregator}.
     */
    private static EntityAggregator selectAggregator() {
        Set<PublicId> modules = publicIdsFromProperty("changeset.export.moduleAllowlist");
        if (modules.isEmpty()) {
            return new TemporalEntityAggregator(0L, Long.MAX_VALUE);
        }
        Set<PublicId> paths = publicIdsFromProperty("changeset.export.pathAllowlist");
        Set<PublicId> includePatterns = publicIdsFromProperty("changeset.export.includePatterns");
        Set<PublicId> excludePatterns = publicIdsFromProperty("changeset.export.excludePatterns");
        System.out.println("Change set filtered by module allowlist: " + modules.size() + " module(s)"
                + (paths.isEmpty() ? "" : ", " + paths.size() + " path(s)")
                + (includePatterns.isEmpty() ? "" : ", include " + includePatterns.size() + " pattern(s)")
                + (excludePatterns.isEmpty() ? "" : ", exclude " + excludePatterns.size() + " pattern(s)"));
        return new AllowlistEntityAggregator(modules, paths, includePatterns, excludePatterns, null);
    }

    /** Parses a comma-separated list of UUIDs from a system property into a set of {@link PublicId}s. */
    private static Set<PublicId> publicIdsFromProperty(String propertyName) {
        String value = System.getProperty(propertyName);
        if (value == null || value.isBlank()) {
            return Set.of();
        }
        Set<PublicId> publicIds = new LinkedHashSet<>();
        for (String token : value.split(",")) {
            String trimmed = token.trim();
            if (!trimmed.isEmpty()) {
                publicIds.add(PublicIds.of(UUID.fromString(trimmed)));
            }
        }
        return publicIds;
    }
}
