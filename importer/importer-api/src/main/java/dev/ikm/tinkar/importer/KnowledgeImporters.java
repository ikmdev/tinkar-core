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
package dev.ikm.tinkar.importer;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Finds knowledge importers on the module or class path.
 */
public final class KnowledgeImporters {

    private KnowledgeImporters() {
    }

    /**
     * Every importer present, in name order.
     *
     * @return the importers
     */
    public static List<KnowledgeImporter> all() {
        List<KnowledgeImporter> found = new ArrayList<>();
        ServiceLoader.load(KnowledgeImporter.class, KnowledgeImporters.class.getClassLoader()).forEach(found::add);
        if (Thread.currentThread().getContextClassLoader() != null) {
            ServiceLoader.load(KnowledgeImporter.class, Thread.currentThread().getContextClassLoader())
                    .forEach(importer -> {
                        if (found.stream().noneMatch(f -> f.getClass().equals(importer.getClass()))) {
                            found.add(importer);
                        }
                    });
        }
        found.sort(java.util.Comparator.comparing(KnowledgeImporter::name));
        return List.copyOf(found);
    }

    /**
     * The importer with a name.
     *
     * @param name the importer's name
     * @return the importer
     * @throws ImportException when none, or more than one, has that name
     */
    public static KnowledgeImporter named(String name) {
        List<KnowledgeImporter> all = all();
        List<KnowledgeImporter> matching = all.stream().filter(importer -> importer.name().equals(name)).toList();
        if (matching.size() == 1) {
            return matching.getFirst();
        }
        throw new ImportException((matching.isEmpty() ? "No" : matching.size() + "")
                + " knowledge importer named \"" + name + "\"; present: "
                + all.stream().map(KnowledgeImporter::name).toList());
    }
}
