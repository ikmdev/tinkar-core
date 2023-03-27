package dev.ikm.tinkar.coordinate.navigation.calculator;

import dev.ikm.tinkar.coordinate.language.LanguageCoordinateRecord;
import dev.ikm.tinkar.coordinate.language.calculator.LanguageCalculator;
import dev.ikm.tinkar.coordinate.language.calculator.LanguageCalculatorDelegate;
import dev.ikm.tinkar.coordinate.navigation.NavigationCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StateSet;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculatorDelegate;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import org.eclipse.collections.api.list.ImmutableList;
import dev.ikm.tinkar.common.id.IntIdList;
import dev.ikm.tinkar.common.id.IntIdSet;

public interface NavigationCalculatorDelegate extends NavigationCalculator, LanguageCalculatorDelegate, StampCalculatorDelegate {

    NavigationCalculator navigationCalculator();

    @Override
    default IntIdList toSortedList(IntIdList inputList) {
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
    default IntIdList unsortedParentsOf(int conceptNid) {
        return navigationCalculator().unsortedParentsOf(conceptNid);
    }

    @Override
    default IntIdList unsortedChildrenOf(int conceptNid) {
        return navigationCalculator().unsortedChildrenOf(conceptNid);
    }

    @Override
    default IntIdList sortedParentsOf(int conceptNid) {
        return navigationCalculator().sortedParentsOf(conceptNid);
    }

    @Override
    default IntIdList sortedChildrenOf(int conceptNid) {
        return navigationCalculator().sortedChildrenOf(conceptNid);
    }

    default NavigationCoordinateRecord navigationCoordinate() {
        return navigationCalculator().navigationCoordinate();
    }

    @Override
    default IntIdList unsortedParentsOf(int conceptNid, int patternNid) {
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
    default IntIdSet kindOf(int conceptNid) {
        return navigationCalculator().kindOf(conceptNid);
    }

    @Override
    default ImmutableList<Edge> sortedParentEdges(int conceptNid) {
        return navigationCalculator().sortedParentEdges(conceptNid);
    }

    @Override
    default ImmutableList<Edge> unsortedParentEdges(int conceptNid) {
        return navigationCalculator().unsortedParentEdges(conceptNid);
    }

    @Override
    default ImmutableList<Edge> sortedChildEdges(int conceptNid) {
        return navigationCalculator().sortedChildEdges(conceptNid);
    }

    @Override
    default ImmutableList<Edge> unsortedChildEdges(int conceptNid) {
        return navigationCalculator().unsortedChildEdges(conceptNid);
    }

    @Override
    default IntIdSet descendentsOf(int conceptNid) {
        return navigationCalculator().descendentsOf(conceptNid);
    }

    @Override
    default IntIdSet ancestorsOf(int conceptNid) {
        return navigationCalculator().ancestorsOf(conceptNid);
    }
}
