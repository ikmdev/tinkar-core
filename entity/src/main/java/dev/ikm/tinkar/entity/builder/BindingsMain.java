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
import dev.ikm.tinkar.common.service.DataServiceController;
import dev.ikm.tinkar.common.service.PluggableService;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ServiceKeys;
import dev.ikm.tinkar.common.service.ServiceProperties;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * The bindings-generation entry point build tooling invokes: discovers the project's
 * {@link KnowledgeSetSource} via {@link ServiceLoader}, composes it, and writes the
 * generated bindings class with {@link BindingsWriter}.
 * <p>
 * This is the stable reflective seam for the {@code ike:knowledge-bindings} Maven goal:
 * the goal loads this class in a classloader over the project's dependency classpath and
 * invokes {@link #main(String[])} in-process. Composition is store-free when the
 * source's builders defer all nid-minting to {@link KnowledgeSet#write()}; sources that
 * resolve nids eagerly during composition ({@code PatternBuilder.at()}/
 * {@code flushPendingVersion()} via {@code EntityProxy.nid()}) additionally need a
 * running {@link PrimitiveData} service. To serve both without forcing a store
 * dependency onto every consumer's generation classpath, this entry point probes for
 * the {@value #EPHEMERAL_STORE_CONTROLLER} controller: when present, composition runs
 * inside a disposable ephemeral-store lifecycle (the same pattern used by other
 * in-process {@code KnowledgeSetSource} consumers, e.g. {@code FoundationFidelityIT});
 * when absent, composition runs store-free under the original contract. Nothing is
 * persisted either way.
 * <p>
 * Arguments: {@code outputDir packageName className [sourceClassName]} — the source
 * class argument selects an implementation when the classpath provides more than one
 * {@code KnowledgeSetSource}; otherwise exactly one must be discoverable.
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
public final class BindingsMain {

    /**
     * Controller name of the disposable in-memory store used around composition when a
     * provider is on the classpath. Must match the name the probe and
     * {@link PrimitiveData#selectControllerByName(String)} both see.
     */
    private static final String EPHEMERAL_STORE_CONTROLLER = "Load Ephemeral Store";

    private BindingsMain() {
    }

    /**
     * Generates the bindings source file.
     *
     * @param args {@code outputDir packageName className [sourceClassName]}
     * @throws Exception if discovery, composition, or writing fails
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4) {
            throw new IllegalArgumentException(
                    "Usage: BindingsMain <outputDir> <packageName> <className> [sourceClassName]");
        }
        Path outputDir = Path.of(args[0]);
        String packageName = args[1];
        String className = args[2];

        KnowledgeSetSource source;
        if (args.length == 4) {
            source = (KnowledgeSetSource) Class.forName(args[3], true, BindingsMain.class.getClassLoader())
                    .getDeclaredConstructor().newInstance();
        } else {
            List<KnowledgeSetSource> found = new ArrayList<>();
            ServiceLoader.load(KnowledgeSetSource.class, BindingsMain.class.getClassLoader())
                    .forEach(found::add);
            if (found.size() != 1) {
                throw new IllegalStateException("Expected exactly one KnowledgeSetSource on the classpath, found "
                        + found.size() + (found.isEmpty() ? "" : ": "
                        + found.stream().map(s -> s.getClass().getName()).toList())
                        + " — pass the implementation class name as the fourth argument to select one");
            }
            source = found.getFirst();
        }

        if (ephemeralStoreAvailable()) {
            CachingService.clearAll();
            ServiceProperties.set(ServiceKeys.DATA_STORE_ROOT,
                    Files.createTempDirectory("ike-bindings").toFile());
            PrimitiveData.selectControllerByName(EPHEMERAL_STORE_CONTROLLER);
            PrimitiveData.start();
            try {
                composeAndWrite(source, outputDir, packageName, className);
            } finally {
                PrimitiveData.stop();
            }
        } else {
            try {
                composeAndWrite(source, outputDir, packageName, className);
            } catch (IllegalStateException e) {
                if (e.getMessage() != null
                        && e.getMessage().startsWith("No PrimitiveDataService provider available")) {
                    throw new IllegalStateException(source.getClass().getName()
                            + " resolved nids during composition, which needs a running data store,"
                            + " but no \"" + EPHEMERAL_STORE_CONTROLLER + "\" controller is on the"
                            + " generation classpath. Add an ephemeral-store provider (e.g."
                            + " network.ike.knowledge:ike-knowledge-provider) as a runtime dependency"
                            + " of the bindings module.", e);
                }
                throw e;
            }
        }
    }

    /**
     * Probes the classpath for the {@value #EPHEMERAL_STORE_CONTROLLER} controller using
     * the same discovery mechanism as
     * {@link PrimitiveData#selectControllerByName(String)}, so the probe and the
     * subsequent selection cannot disagree.
     *
     * @return whether an ephemeral-store controller is available to select
     */
    private static boolean ephemeralStoreAvailable() {
        for (DataServiceController<?> controller : PluggableService.load(DataServiceController.class)) {
            if (EPHEMERAL_STORE_CONTROLLER.equals(controller.controllerName())) {
                return true;
            }
        }
        return false;
    }

    /**
     * Composes the source and writes the generated bindings class.
     *
     * @param source      the knowledge-set source to compose
     * @param outputDir   directory the bindings source file is written under
     * @param packageName package of the generated class
     * @param className   simple name of the generated class
     * @throws Exception if composition or writing fails
     */
    private static void composeAndWrite(KnowledgeSetSource source, Path outputDir,
                                        String packageName, String className) throws Exception {
        KnowledgeSet knowledgeSet = source.compose();
        Path file = BindingsWriter.write(knowledgeSet, packageName, className, outputDir);
        System.out.println("Bindings written: " + file + " (" + knowledgeSet.declarations().size()
                + " declarations from " + source.getClass().getName() + ")");
    }
}
