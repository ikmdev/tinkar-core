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

import dev.ikm.tinkar.common.id.LongIdList;
import dev.ikm.tinkar.common.id.LongIdSet;
import dev.ikm.tinkar.coordinate.language.LanguageCoordinateRecord;
import dev.ikm.tinkar.coordinate.language.calculator.LanguageCalculator;
import dev.ikm.tinkar.coordinate.language.calculator.LanguageCalculatorDelegate;
import dev.ikm.tinkar.coordinate.navigation.NavigationCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StateSet;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculatorDelegate;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import org.eclipse.collections.api.list.ImmutableList;

public interface NavigationCalculatorDelegate extends NavigationCalculator, LanguageCalculatorDelegate, StampCalculatorDelegate {

    NavigationCalculator navigationCalculator();

    @Override
    default LongIdList toSortedList(LongIdList inputList) {
        return navigationCalculator().toSortedList(inputList);
    }

    @Override
    default StampCalculatorWithCache vertexStampCalculator() {
        return navigationCalculator().vertexStampCalculator();
    }

    @Override
    default StateSet allowedVertexStates() {
        return navigationCalculator().allowedVertexStates();
    }

    @Override
    default StampCalculator stampCalculator() {
        return navigationCalculator().stampCalculator();
    }

    @Override
    default boolean sortVertices() {
        return navigationCalculator().sortVertices();
    }

    @Override
    default LongIdList unsortedParentsOf(long conceptNid) {
        return navigationCalculator().unsortedParentsOf(conceptNid);
    }

    @Override
    default LongIdList unsortedChildrenOf(long conceptNid) {
        return navigationCalculator().unsortedChildrenOf(conceptNid);
    }

    @Override
    default LongIdList unsortedUnversionedChildrenOf(long conceptNid) {
        return navigationCalculator().unsortedUnversionedChildrenOf(conceptNid);
    }

    @Override
    default LongIdList unsortedUnversionedParentsOf(long conceptNid) {
        return navigationCalculator().unsortedUnversionedParentsOf(conceptNid);
    }

    @Override
    default LongIdList sortedParentsOf(long conceptNid) {
        return navigationCalculator().sortedParentsOf(conceptNid);
    }

    @Override
    default LongIdList sortedChildrenOf(long conceptNid) {
        return navigationCalculator().sortedChildrenOf(conceptNid);
    }

    default NavigationCoordinateRecord navigationCoordinate() {
        return navigationCalculator().navigationCoordinate();
    }

    @Override
    default LongIdList unsortedParentsOf(long conceptNid, long patternNid) {
        return navigationCalculator().unsortedParentsOf(conceptNid, patternNid);
    }

    @Override
    default ImmutableList<LanguageCoordinateRecord> languageCoordinateList() {
        return navigationCalculator().languageCoordinateList();
    }

    @Override
    default LanguageCalculator languageCalculator() {
        return navigationCalculator().languageCalculator();
    }

    @Override
    default LongIdSet kindOf(long conceptNid) {
        return navigationCalculator().kindOf(conceptNid);
    }

    @Override
    default ImmutableList<Edge> sortedParentEdges(long conceptNid) {
        return navigationCalculator().sortedParentEdges(conceptNid);
    }

    @Override
    default ImmutableList<Edge> unsortedParentEdges(long conceptNid) {
        return navigationCalculator().unsortedParentEdges(conceptNid);
    }

    @Override
    default ImmutableList<Edge> sortedChildEdges(long conceptNid) {
        return navigationCalculator().sortedChildEdges(conceptNid);
    }

    @Override
    default ImmutableList<Edge> unsortedChildEdges(long conceptNid) {
        return navigationCalculator().unsortedChildEdges(conceptNid);
    }

    @Override
    default LongIdSet descendentsOf(long conceptNid) {
        return navigationCalculator().descendentsOf(conceptNid);
    }

    @Override
    default LongIdSet ancestorsOf(long conceptNid) {
        return navigationCalculator().ancestorsOf(conceptNid);
    }
}
