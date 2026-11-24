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

import dev.ikm.tinkar.common.service.EntityCountSummary;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.PatternEntity;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.aggregator.EntityAggregator;
import dev.ikm.tinkar.entity.boundary.DataKind;
import dev.ikm.tinkar.entity.boundary.EntityBoundaries;
import dev.ikm.tinkar.entity.export.ExportEntitiesToProtobufFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.LongConsumer;
import java.util.function.LongFunction;

/**
 * Writes the components of a scope to change sets (IKE-Network/ike-issues#1313): all of them to one
 * file, as an export does, or each module's to a file in the folder of its kind, as a move between
 * kinds and an import do, so the knowledge base's own records hold data that did not come by a commit.
 */
final class ScopeChangeSets {

    private ScopeChangeSets() {
    }

    /**
     * Writes each module's data of a scope to a change set of its kind. Data saved in a file needs
     * none: the file is its record.
     *
     * @param newChangeSet a new change-set file for a module's data, in the folder of its kind
     * @param roots        the scope's roots; the scope is resolved here, as it now is
     * @param modules      the modules its data is written under
     * @param kind         the kind the data now is
     * @return the files written, none for data saved in a file or a store that keeps no change sets
     */
    static List<File> write(LongFunction<Optional<File>> newChangeSet, long[] roots, Collection<Long> modules, DataKind kind) {
        List<File> files = new ArrayList<>();
        if (kind == DataKind.FILE) {
            return files;
        }
        Scope scope = Scope.of(roots);
        for (long module : modules) {
            Optional<File> file = newChangeSet.apply(module);
            if (file.isEmpty()) {
                continue;
            }
            List<Long> stamps = new ArrayList<>();
            for (long stamp : scope.stamps().toArray()) {
                if (EntityBoundaries.moduleOf(stamp) == module) {
                    stamps.add(stamp);
                }
            }
            List<Long> members = new ArrayList<>();
            for (long member : scope.members().toArray()) {
                EntityHandle.get(member).entity().filter(entity -> entity.versions().stream()
                        .anyMatch(version -> stamps.contains(version.stampNid()))).ifPresent(_ -> members.add(member));
            }
            write(file.get(), stamps, members);
            files.add(file.get());
        }
        return files;
    }

    /**
     * Writes a scope to one change set.
     *
     * @param file  the change set
     * @param scope the scope
     * @return the components written
     */
    static EntityCountSummary write(File file, Scope scope) {
        List<Long> stamps = new ArrayList<>();
        scope.stamps().forEach(stamps::add);
        List<Long> members = new ArrayList<>();
        scope.members().forEach(members::add);
        return write(file, stamps, members);
    }

    private static EntityCountSummary write(File file, List<Long> stamps, List<Long> members) {
        EntityAggregator aggregator = new EntityAggregator() {
            @Override
            public EntityCountSummary aggregate(LongConsumer nids) {
                long concepts = 0;
                long patterns = 0;
                long semantics = 0;
                stamps.forEach(nids::accept);
                for (long member : members) {
                    nids.accept(member);
                    switch (EntityHandle.get(member).orNull()) {
                        case ConceptEntity<?> _ -> concepts++;
                        case PatternEntity<?> _ -> patterns++;
                        case SemanticEntity<?> _ -> semantics++;
                        case null, default -> { }
                    }
                }
                return new EntityCountSummary(concepts, semantics, patterns, stamps.size());
            }
        };
        try {
            return new ExportEntitiesToProtobufFile(file, aggregator).compute();
        } catch (Exception e) {
            throw new IllegalStateException("Writing " + file.getName() + " failed", e);
        }
    }
}
