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

import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.common.service.EntityCountSummary;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ServiceKeys;
import dev.ikm.tinkar.common.service.ServiceProperties;
import dev.ikm.tinkar.entity.aggregator.TemporalEntityAggregator;
import dev.ikm.tinkar.entity.export.ExportEntitiesToProtobufFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

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
 */
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
        if (args.length < 1 || args.length > 2) {
            throw new IllegalArgumentException("Usage: ChangeSetMain <outputFile> [sourceClassName]");
        }
        Path outputFile = Path.of(args[0]);
        Files.createDirectories(outputFile.toAbsolutePath().getParent());

        KnowledgeSetSource source;
        if (args.length == 2) {
            source = (KnowledgeSetSource) Class.forName(args[1], true, ChangeSetMain.class.getClassLoader())
                    .getDeclaredConstructor().newInstance();
        } else {
            List<KnowledgeSetSource> found = new ArrayList<>();
            ServiceLoader.load(KnowledgeSetSource.class, ChangeSetMain.class.getClassLoader())
                    .forEach(found::add);
            if (found.size() != 1) {
                throw new IllegalStateException("Expected exactly one KnowledgeSetSource on the classpath, found "
                        + found.size() + (found.isEmpty() ? "" : ": "
                        + found.stream().map(s -> s.getClass().getName()).toList())
                        + " — pass the implementation class name as the second argument to select one");
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
            // Aggregate by real time (epoch >= 0): includes every declared stamp and its
            // content, and excludes the premundane NONEXISTENT_STAMP sentinel the store
            // mints at startup — whose module/author concepts are absent in a store
            // seeded only by this replay, and which is not part of the set.
            EntityCountSummary summary = new ExportEntitiesToProtobufFile(outputFile.toFile(),
                    new TemporalEntityAggregator(0L, Long.MAX_VALUE)).compute();
            System.out.println("Change set written: " + outputFile + " — "
                    + summary.getTotalCount() + " entities ("
                    + summary.conceptCount() + " concepts, "
                    + summary.semanticCount() + " semantics, "
                    + summary.patternCount() + " patterns, "
                    + summary.stampCount() + " stamps) from " + source.getClass().getName());
        } finally {
            PrimitiveData.stop();
        }
    }
}
