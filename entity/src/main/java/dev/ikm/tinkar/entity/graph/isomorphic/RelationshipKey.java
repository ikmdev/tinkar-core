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
package dev.ikm.tinkar.entity.graph.isomorphic;

import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntSets;

import java.util.Objects;

/**
 * The Class RelationshipKey. Equal when equivalent, ignoring vertexIndex.
 *
 * 
 */
public class RelationshipKey
        implements Comparable<RelationshipKey> {
    /** The concepts referenced at node or below. */
    final int vertexIndex;
    final boolean necessarySet;
    final ImmutableIntList conceptsReferencedAtNodeOrBelow;
    final int hashCode;

    //~--- constructors --------------------------------------------------------

    /**
     * Instantiates a new relationship key.
     *
     * @param vertexIndex the vertex id
     * @param expression the expression
     */
    public RelationshipKey(int vertexIndex, DiTreeEntity expression) {
        this.vertexIndex = vertexIndex;
        this.necessarySet = expression.hasParentVertexWithMeaning(vertexIndex, TinkarTerm.NECESSARY_SET.nid());
        MutableIntSet conceptsReferencedAtNodeOrBelowCollector = IntSets.mutable.empty();
        processVertexAndChildren(vertexIndex, expression, conceptsReferencedAtNodeOrBelowCollector);
        this.conceptsReferencedAtNodeOrBelow = conceptsReferencedAtNodeOrBelowCollector.toSortedList().toImmutable();
        this.hashCode = Objects.hash(necessarySet,
                IsomorphicResultsLeafHash.makeNidListHash(expression.vertex(vertexIndex).getMeaningNid(),
                this.conceptsReferencedAtNodeOrBelow.toArray()));
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    /**
     * Adds the nodes.
     *
     * @param vertexIndex the vertex index
     * @param tree the tree
     */
    private void processVertexAndChildren(int vertexIndex, DiTreeEntity tree, MutableIntSet conceptsReferencedAtNodeOrBelowCollector) {
        final EntityVertex vertex = tree.vertex(vertexIndex);

        tree.vertex(vertexIndex).addConceptsReferencedByVertex(conceptsReferencedAtNodeOrBelowCollector);
        for (EntityVertex childVertex: tree.successors(vertex)) {
            processVertexAndChildren(childVertex.vertexIndex(), tree, conceptsReferencedAtNodeOrBelowCollector);
        }
    }

    //~--- methods -------------------------------------------------------------

    /**
     * Compare to.
     *
     * @param o the o
     * @return the int
     */
    @Override
    public int compareTo(RelationshipKey o) {
        if (this.hashCode != o.hashCode) {
            return Integer.compare(this.hashCode, o.hashCode);
        }
        if (this.necessarySet != o.necessarySet) {
            return Boolean.compare(this.necessarySet, o.necessarySet);
        }
        int comparison = Integer.compare(this.conceptsReferencedAtNodeOrBelow.size(), o.conceptsReferencedAtNodeOrBelow.size());

        if (comparison != 0) {
            return comparison;
        }

        final int[] thisKeys  = this.conceptsReferencedAtNodeOrBelow.toArray();
        final int[] otherKeys = o.conceptsReferencedAtNodeOrBelow.toArray();

        for (int i = 0; i < thisKeys.length; i++) {
            if (thisKeys[i] != otherKeys[i]) {
                return Integer.compare(thisKeys[i], otherKeys[i]);
            }
        }
        return 0;
    }

    @Override
    public boolean equals(Object other) {
        if (other instanceof RelationshipKey otherKey) {
            return compareTo(otherKey) == 0;
        }
        return false;
    }

    @Override
    public String toString() {
        return "RelationshipKey{" + "vertexId=" + vertexIndex + ", in necessary set=" + necessarySet + ", conceptsReferencedAtNodeOrBelow=" + conceptsReferencedAtNodeOrBelow + '}';
    }
}
