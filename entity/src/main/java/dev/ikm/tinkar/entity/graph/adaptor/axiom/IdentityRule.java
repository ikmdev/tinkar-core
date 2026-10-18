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
package dev.ikm.tinkar.entity.graph.adaptor.axiom;

import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.KernelConcept;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

/**
 * The identity table (arch-vertex-identity-table; IKE-Network/ike-issues#1310): for each
 * logical-axiom meaning, how its successors are told apart, and which of its properties tell it
 * apart from its siblings when it is an operand.
 *
 * <p>The table is one exhaustive switch expression over {@link LogicalAxiomSemantic} with no
 * {@code default} branch, so a meaning added to the enum without an entry fails the build. Do
 * not add one: it would silence that check. Trees over another vocabulary take their rules from
 * a {@link dev.ikm.tinkar.entity.graph.VertexIdentityTable} of their own.
 *
 * @param successors  how this vertex's children are told apart
 * @param identifying what tells this vertex apart from its siblings, read only when its parent's
 *                    successors are {@link Successors#OPERANDS}; any other property is a value,
 *                    and changes by update
 */
public record IdentityRule(Successors successors, ImmutableList<ConceptFacade> identifying) {

    /** How a vertex's children are told apart (arch-vertex-identity-rule). */
    public enum Successors {
        /** A leaf. */
        NONE,
        /** One successor by definition, identified by its position: changing it is an update. */
        SOLE,
        /** An unordered collection, each identified by its key: changing a key is a replace. */
        OPERANDS,
        /**
         * A sequence, each identified by its position: the operands of a constructor that is not
         * commutative, such as a comparison's left and right. Changing one is an update.
         */
        ORDERED,
        /** At most one successor, identified by its position when present. */
        OPTIONAL
    }

    /**
     * The table's entry for a meaning.
     *
     * @param semantic the meaning
     * @return its rule
     */
    public static IdentityRule of(LogicalAxiomSemantic semantic) {
        return switch (semantic) {
            case DEFINITION_ROOT, AND, OR -> rule(Successors.OPERANDS);
            case NECESSARY_SET, SUFFICIENT_SET, INCLUSION_SET,
                 PROPERTY_SET, DATA_PROPERTY_SET, INTERVAL_PROPERTY_SET -> rule(Successors.SOLE);
            case ROLE -> rule(Successors.SOLE, KernelConcept.ROLE_TYPE, KernelConcept.ROLE_OPERATOR);
            case CONCEPT -> rule(Successors.NONE, KernelConcept.CONCEPT_REFERENCE);
            case DISJOINT_WITH -> rule(Successors.NONE, KernelConcept.DISJOINT_WITH);
            case FEATURE -> rule(Successors.NONE, KernelConcept.FEATURE_TYPE, KernelConcept.CONCRETE_DOMAIN_OPERATOR);
            case INTERVAL_ROLE -> rule(Successors.NONE, KernelConcept.INTERVAL_ROLE_TYPE);
            case PROPERTY_SEQUENCE_IMPLICATION ->
                    rule(Successors.NONE, KernelConcept.PROPERTY_SEQUENCE, KernelConcept.PROPERTY_SEQUENCE_IMPLICATION);
        };
    }

    /**
     * The table's entry for a vertex meaning.
     *
     * @param meaningNid the vertex's meaning
     * @return its rule
     * @throws IllegalStateException for a meaning outside {@link LogicalAxiomSemantic}: an unknown
     *                               meaning is refused, never mapped to a fallback
     */
    public static IdentityRule of(long meaningNid) {
        return of(LogicalAxiomSemantic.get(meaningNid));
    }

    private static IdentityRule rule(Successors successors, ConceptFacade... identifying) {
        return new IdentityRule(successors, Lists.immutable.of(identifying));
    }
}
