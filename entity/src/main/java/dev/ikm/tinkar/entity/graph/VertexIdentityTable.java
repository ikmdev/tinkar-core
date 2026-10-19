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
package dev.ikm.tinkar.entity.graph;

import dev.ikm.tinkar.common.service.PluggableService;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.IdentityRule;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiomSemantic;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * An identity table for one vocabulary of definition-tree meanings (arch-vertex-identity-table;
 * IKE-Network/ike-issues#1310): for each meaning it covers, how its successors are told apart
 * and which of its properties identify it as an operand.
 *
 * <p>{@link #LOGICAL} is the table for {@link LogicalAxiomSemantic}. A tree over another
 * vocabulary, such as a Komet plugin's, takes the table its module provides as a service; a
 * table should be one exhaustive switch over its vocabulary's enum with no {@code default}
 * branch, so a meaning added without its rule cannot build. The write path correlates a tree
 * when one table covers every vertex of it, and leaves it untouched otherwise.
 */
public interface VertexIdentityTable {

    /**
     * Whether the table has a rule for a meaning.
     *
     * @param meaningNid a vertex meaning
     * @return true when {@link #rule} answers for it
     */
    boolean covers(long meaningNid);

    /**
     * The rule for a meaning the table covers.
     *
     * @param meaningNid a vertex meaning
     * @return its rule
     */
    IdentityRule rule(long meaningNid);

    /**
     * Whether the table covers every vertex of a tree.
     *
     * @param tree the tree
     * @return true when every vertex's meaning has a rule here
     */
    default boolean covers(DiTreeEntity tree) {
        for (EntityVertex vertex : tree.vertexMap()) {
            if (vertex == null || !covers(vertex.getMeaningNid())) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether a meaning's successors are a sequence, told apart by position.
     *
     * @param meaningNid a vertex meaning the table covers
     * @return true for {@link IdentityRule.Successors#ORDERED}
     */
    default boolean ordered(long meaningNid) {
        return covers(meaningNid) && rule(meaningNid).successors() == IdentityRule.Successors.ORDERED;
    }

    /** The table for {@link LogicalAxiomSemantic}: {@link IdentityRule#of(LogicalAxiomSemantic)}. */
    VertexIdentityTable LOGICAL = new VertexIdentityTable() {
        @Override
        public boolean covers(long meaningNid) {
            for (LogicalAxiomSemantic semantic : LogicalAxiomSemantic.values()) {
                if (semantic.nid == meaningNid) {
                    return true;
                }
            }
            return false;
        }

        @Override
        public IdentityRule rule(long meaningNid) {
            return IdentityRule.of(meaningNid);
        }

        @Override
        public String toString() {
            return "LogicalAxiomSemantic";
        }
    };

    /**
     * The table that covers every vertex of a tree: {@link #LOGICAL}, or else the first, by class
     * name, of the tables provided as services.
     *
     * @param tree the tree
     * @return its table, or empty when none covers it
     */
    static Optional<VertexIdentityTable> forTree(DiTreeEntity tree) {
        if (LOGICAL.covers(tree)) {
            return Optional.of(LOGICAL);
        }
        for (VertexIdentityTable table : Provided.tables()) {
            if (table.covers(tree)) {
                return Optional.of(table);
            }
        }
        return Optional.empty();
    }

    /**
     * The tables provided as services, ordered by class name. Found again until some are, since a
     * plugin layer may load after the first tree is written; kept once found.
     */
    final class Provided {
        private static volatile List<VertexIdentityTable> tables = List.of();

        private Provided() {
        }

        static List<VertexIdentityTable> tables() {
            List<VertexIdentityTable> found = tables;
            if (found.isEmpty()) {
                List<VertexIdentityTable> loaded = new ArrayList<>();
                try {
                    PluggableService.load(VertexIdentityTable.class).forEach(loaded::add);
                } catch (RuntimeException | java.util.ServiceConfigurationError unavailable) {
                    // no plugin layer yet: no provided tables
                }
                loaded.sort(Comparator.comparing(table -> table.getClass().getName()));
                found = List.copyOf(loaded);
                tables = found;
            }
            return found;
        }
    }
}
