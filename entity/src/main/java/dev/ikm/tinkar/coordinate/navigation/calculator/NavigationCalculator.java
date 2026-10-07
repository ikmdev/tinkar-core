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
package dev.ikm.tinkar.coordinate.navigation.calculator;

import dev.ikm.tinkar.terms.KernelTerm;
import dev.ikm.tinkar.common.id.LongIdList;
import dev.ikm.tinkar.common.id.LongIdSet;
import dev.ikm.tinkar.common.id.LongIds;
import dev.ikm.tinkar.coordinate.language.calculator.LanguageCalculatorDelegate;
import dev.ikm.tinkar.coordinate.navigation.NavigationCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StateSet;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculatorDelegate;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.PatternFacade;
import org.eclipse.collections.api.list.ImmutableList;

public interface NavigationCalculator extends StampCalculatorDelegate, LanguageCalculatorDelegate {

    StampCalculatorWithCache vertexStampCalculator();

    StateSet allowedVertexStates();

    default LongIdList parentsOf(ConceptFacade concept) {
        return parentsOf(concept.nid());
    }

    default LongIdList parentsOf(long conceptNid) {
        if (sortVertices()) {
            return sortedParentsOf(conceptNid);
        }
        return unsortedParentsOf(conceptNid);
    }

    boolean sortVertices();

    LongIdList sortedParentsOf(long conceptNid);

    LongIdList unsortedParentsOf(long conceptNid);

    default LongIdSet descendentsOf(ConceptFacade concept) {
        return descendentsOf(concept.nid());
    }

    LongIdSet descendentsOf(long conceptNid);

    default LongIdSet ancestorsOf(ConceptFacade concept) {
        return descendentsOf(concept.nid());
    }

    LongIdSet ancestorsOf(long conceptNid);

    default LongIdSet kindOf(ConceptFacade concept) {
        return kindOf(concept.nid());
    }

    LongIdSet kindOf(long conceptNid);

    default ImmutableList<Edge> parentEdges(ConceptFacade concept) {
        return childEdges(concept.nid());
    }

    default ImmutableList<Edge> childEdges(long conceptNid) {
        if (sortVertices()) {
            return sortedChildEdges(conceptNid);
        }
        return unsortedChildEdges(conceptNid);
    }

    ImmutableList<Edge> sortedChildEdges(long conceptNid);

    ImmutableList<Edge> unsortedChildEdges(long conceptNid);

    default ImmutableList<Edge> parentEdges(long conceptNid) {
        if (sortVertices()) {
            return sortedParentEdges(conceptNid);
        }
        return unsortedParentEdges(conceptNid);
    }

    ImmutableList<Edge> sortedParentEdges(long conceptNid);

    ImmutableList<Edge> unsortedParentEdges(long conceptNid);

    default ImmutableList<Edge> sortedParentEdges(ConceptFacade concept) {
        return sortedParentEdges(concept.nid());
    }

    default ImmutableList<Edge> unsortedParentEdges(ConceptFacade concept) {
        return unsortedParentEdges(concept.nid());
    }

    default ImmutableList<Edge> childEdges(ConceptFacade concept) {
        return childEdges(concept.nid());
    }

    default ImmutableList<Edge> sortedChildEdges(ConceptFacade concept) {
        return sortedChildEdges(concept.nid());
    }

    default ImmutableList<Edge> unsortedChildEdges(ConceptFacade concept) {
        return unsortedChildEdges(concept.nid());
    }

    default LongIdList childrenOf(ConceptFacade concept) {
        return childrenOf(concept.nid());
    }

    default LongIdList childrenOf(long conceptNid) {
        if (sortVertices()) {
            return sortedChildrenOf(conceptNid);
        }
        return unsortedChildrenOf(conceptNid);
    }

    LongIdList sortedChildrenOf(long conceptNid);

    LongIdList unsortedChildrenOf(long conceptNid);

    LongIdList unsortedUnversionedChildrenOf(long conceptNid);

    LongIdList unsortedUnversionedParentsOf(long conceptNid);

    default LongIdList sortedParentsOf(ConceptFacade concept) {
        return sortedParentsOf(concept.nid());
    }

    default LongIdList sortedChildrenOf(ConceptFacade concept) {
        return sortedChildrenOf(concept.nid());
    }

    default LongIdList unsortedChildrenOf(ConceptFacade concept) {
        return unsortedChildrenOf(concept.nid());
    }

    default LongIdList unsortedParentsOf(ConceptFacade concept) {
        return unsortedParentsOf(concept.nid());
    }

    default LongIdList toSortedList(LongIdSet inputSet) {
        // TODO add pattern sort to implementation...
        return toSortedList(LongIds.list.of(inputSet.toArray()));
    }

    LongIdList toSortedList(LongIdList inputList);

    NavigationCoordinateRecord navigationCoordinate();


    default LongIdList unsortedParentsOf(ConceptFacade concept, PatternFacade patternFacade) {
        return unsortedParentsOf(concept.nid(), patternFacade.nid());
    }

    LongIdList unsortedParentsOf(long conceptNid, long patternNid);


    default boolean isMultiparent(EntityFacade facade) {
        return isMultiparent(facade.nid());
    }

    default boolean isMultiparent(long conceptNid) {
        if (conceptNid == -1
                || conceptNid == KernelTerm.UNINITIALIZED_COMPONENT.nid()) {
            return false;
        }
        return parentsOf(conceptNid).size() > 1;
    }

}
