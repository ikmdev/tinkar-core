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
package dev.ikm.tinkar.entity.builder.generator;

import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiomSemantic;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;

import java.util.ArrayList;
import java.util.List;

/**
 * Decompiles a stored stated-axiom {@link DiTreeEntity} into the ledger builder's
 * {@code isA(...)} verb — the ledger generator's (IKE-Network/ike-issues#869) answer to
 * the axiom-shape spike (AxiomShapeSpikeIT): 378 of the 379 stated-axiom semantics in the
 * full unreasoned starter set are the simple {@code NecessarySet(And(ConceptAxiom*))}
 * shape (or its single-parent variant, {@code NecessarySet(ConceptAxiom)}); this class
 * detects that shape and extracts its parent concepts. Walks raw {@link EntityVertex}
 * nodes by {@link LogicalAxiomSemantic} tag — the same approach the platform's own
 * readers use (the reasoner's {@code ElkOwlDataBuilder}/{@code ElkSnomedDataBuilder}) —
 * rather than the typed {@code LogicalAxiom.Atom} adaptor layer, whose
 * {@code PropertySequenceImplication.implication()} and interval-role bound accessors
 * are stubbed incomplete.
 * <p>
 * A tree outside this one shape (a role restriction, a disjoint-with, anything beyond
 * simple is-a) is reported, not guessed at: the one outlier the spike found needs a
 * human to hand-author its {@code statedAxioms(leb -> ...)} call, and the generator's
 * manifest names it rather than silently emitting something wrong or silently dropping
 * it.
 */
public final class AxiomDecompiler {

    private AxiomDecompiler() {
    }

    /**
     * Decompiles one stated-axiom tree.
     *
     * @param tree the stated-axiom semantic's latest field value
     * @return a simple-isA result carrying the ordered parent concepts, or a
     *         not-simple result carrying a human-readable dump of the tree for
     *         hand-authoring
     */
    public static Result decompile(DiTreeEntity tree) {
        EntityVertex root = tree.root();
        if (meaningOf(root) != LogicalAxiomSemantic.DEFINITION_ROOT) {
            return notSimple(tree, "unexpected root meaning: " + PrimitiveData.text(root.getMeaningNid()));
        }
        ImmutableIntList topSets = tree.successors(root.vertexIndex());
        if (topSets.size() != 1) {
            return notSimple(tree, "multiple top-level sets (" + topSets.size() + ")");
        }
        EntityVertex setVertex = tree.vertex(topSets.get(0));
        if (meaningOf(setVertex) != LogicalAxiomSemantic.NECESSARY_SET) {
            return notSimple(tree, "top-level set is " + meaningOf(setVertex) + ", not NecessarySet");
        }
        ImmutableIntList setChildren = tree.successors(setVertex.vertexIndex());
        if (setChildren.size() == 1 && meaningOf(tree.vertex(setChildren.get(0))) == LogicalAxiomSemantic.CONCEPT) {
            return new Result(true, List.of(conceptOf(tree.vertex(setChildren.get(0)))), null);
        }
        if (setChildren.size() != 1) {
            return notSimple(tree, "NecessarySet with " + setChildren.size() + " direct children (expected one And)");
        }
        EntityVertex connective = tree.vertex(setChildren.get(0));
        if (meaningOf(connective) != LogicalAxiomSemantic.AND) {
            return notSimple(tree, "NecessarySet(" + meaningOf(connective) + "), not an And");
        }
        ImmutableIntList andChildren = tree.successors(connective.vertexIndex());
        List<ConceptFacade> parents = new ArrayList<>(andChildren.size());
        for (int index = 0; index < andChildren.size(); index++) {
            int childIndex = andChildren.get(index);
            EntityVertex child = tree.vertex(childIndex);
            if (meaningOf(child) != LogicalAxiomSemantic.CONCEPT || !tree.successors(childIndex).isEmpty()) {
                return notSimple(tree, "And has a non-concept or non-leaf child: " + meaningOf(child));
            }
            parents.add(conceptOf(child));
        }
        return new Result(true, List.copyOf(parents), null);
    }

    private static Result notSimple(DiTreeEntity tree, String reason) {
        StringBuilder dump = new StringBuilder(reason).append('\n');
        for (EntityVertex vertex : tree.vertexMap()) {
            dump.append("  ").append(vertex.toGraphFormatString("", "", tree)).append('\n');
        }
        return new Result(false, List.of(), dump.toString());
    }

    private static ConceptFacade conceptOf(EntityVertex vertex) {
        ConceptFacade concept = vertex.propertyFast(TinkarTerm.CONCEPT_REFERENCE);
        if (concept == null) {
            throw new IllegalStateException("A CONCEPT vertex carries no CONCEPT_REFERENCE property");
        }
        return concept;
    }

    private static LogicalAxiomSemantic meaningOf(EntityVertex vertex) {
        try {
            return LogicalAxiomSemantic.get(vertex.getMeaningNid());
        } catch (IllegalStateException e) {
            return null;
        }
    }

    /**
     * The outcome of decompiling one stated-axiom tree.
     *
     * @param simpleIsA      {@code true} when the tree is the simple is-a shape
     * @param parents        the ordered parent concepts, when {@code simpleIsA}; empty
     *                       otherwise
     * @param diagnosticDump a human-readable dump of the tree, for hand-authoring, when
     *                       not {@code simpleIsA}; null otherwise
     */
    public record Result(boolean simpleIsA, List<ConceptFacade> parents, String diagnosticDump) {
    }
}
