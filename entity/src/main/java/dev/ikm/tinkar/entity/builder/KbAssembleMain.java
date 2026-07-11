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
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * The knowledge-base assembly entry point: loads one or more protobuf artifacts, in
 * order, into a Spined Array store at the given root — a fresh KB assembled from base
 * data plus knowledge-set change sets, ready to open in Komet. Assembling under
 * {@code ~/Solor/<name>} makes the result directly selectable from Komet's data-source
 * chooser.
 * <p>
 * Load order is the fit contract: the base (for example the Tinkar starter data) comes
 * first so the identities every change set references are present; each change set then
 * merges by public id — the shared-identity join. The store may also be an existing one:
 * loading into a non-empty root imports the artifacts into it.
 * <p>
 * Like {@link ChangeSetMain} and {@link BindingsMain}, this is a stable reflective seam
 * for build tooling (the future {@code ike:kb-assemble} goal): the classpath must carry
 * the entity, executor, and Spined Array providers, with their legacy
 * {@code META-INF/services} controller registrations when running on the classpath.
 * <p>
 * Arguments: {@code storeRoot pbZip [pbZip...]}.
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
public final class KbAssembleMain {

    private KbAssembleMain() {
    }

    /**
     * Assembles a Spined Array store from the given protobuf artifacts.
     *
     * @param args {@code storeRoot pbZip [pbZip...]}
     * @throws Exception if the store cannot be opened or any artifact fails to load
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: KbAssembleMain <storeRoot> <pbZip> [pbZip...]");
        }
        Path storeRoot = Path.of(args[0]);
        Files.createDirectories(storeRoot);
        for (int i = 1; i < args.length; i++) {
            if (!Files.isRegularFile(Path.of(args[i]))) {
                throw new IllegalArgumentException("Not a file: " + args[i]);
            }
        }

        CachingService.clearAll();
        ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT, storeRoot.toFile());
        PrimitiveData.selectControllerByName("Open SpinedArrayStore");
        PrimitiveData.start();
        try {
            for (int i = 1; i < args.length; i++) {
                Path artifact = Path.of(args[i]);
                EntityCountSummary summary = new LoadEntitiesFromProtobufFile(artifact.toFile()).compute();
                System.out.println("Loaded " + artifact.getFileName() + " — "
                        + summary.getTotalCount() + " entities ("
                        + summary.conceptCount() + " concepts, "
                        + summary.semanticCount() + " semantics, "
                        + summary.patternCount() + " patterns, "
                        + summary.stampCount() + " stamps) into " + storeRoot);
            }
        } finally {
            PrimitiveData.stop();
        }
        System.out.println("Knowledge base assembled: " + storeRoot);
    }
}
