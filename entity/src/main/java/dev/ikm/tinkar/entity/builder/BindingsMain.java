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

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * The bindings-generation entry point build tooling invokes: discovers the project's
 * {@link KnowledgeSource} via {@link ServiceLoader}, composes it, and writes the
 * generated bindings class with {@link BindingsWriter}.
 * <p>
 * This is the stable reflective seam for the {@code ike:knowledge-bindings} Maven goal:
 * the goal loads this class in a classloader over the project's dependency classpath and
 * invokes {@link #main(String[])} in-process. Composition is store-free (builders defer
 * nid-minting work to {@link KnowledgeSet#write()}), so no datastore, providers, or
 * service registrations are needed on the generation classpath.
 * <p>
 * Arguments: {@code outputDir packageName className [sourceClassName]} — the source
 * class argument selects an implementation when the classpath provides more than one
 * {@code KnowledgeSource}; otherwise exactly one must be discoverable.
 */
public final class BindingsMain {

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

        KnowledgeSource source;
        if (args.length == 4) {
            source = (KnowledgeSource) Class.forName(args[3], true, BindingsMain.class.getClassLoader())
                    .getDeclaredConstructor().newInstance();
        } else {
            List<KnowledgeSource> found = new ArrayList<>();
            ServiceLoader.load(KnowledgeSource.class, BindingsMain.class.getClassLoader())
                    .forEach(found::add);
            if (found.size() != 1) {
                throw new IllegalStateException("Expected exactly one KnowledgeSource on the classpath, found "
                        + found.size() + (found.isEmpty() ? "" : ": "
                        + found.stream().map(s -> s.getClass().getName()).toList())
                        + " — pass the implementation class name as the fourth argument to select one");
            }
            source = found.getFirst();
        }

        KnowledgeSet knowledgeSet = source.compose();
        Path file = BindingsWriter.write(knowledgeSet, packageName, className, outputDir);
        System.out.println("Bindings written: " + file + " (" + knowledgeSet.declarations().size()
                + " declarations from " + source.getClass().getName() + ")");
    }
}
