package org.hl7.tinkar.coordinate.stamp.calculator;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.util.functional.TriConsumer;
import org.hl7.tinkar.component.graph.DiTree;
import org.hl7.tinkar.coordinate.stamp.StateSet;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.entity.graph.VersionVertex;

import java.util.List;
import java.util.OptionalInt;
import java.util.function.BiConsumer;

public interface StampCalculatorDelegate extends StampCalculator {
    @Override
    default <V extends EntityVersion> Latest<V> latest(int nid) {
        return stampCalculator().latest(nid);
    }

    @Override
    default <V extends EntityVersion> List<DiTree<VersionVertex<V>>> getVersionGraphList(Entity<V> chronicle) {
        return stampCalculator().getVersionGraphList(chronicle);
    }

    @Override
    default <V extends EntityVersion> Latest<V> latest(Entity<V> chronicle) {
        return stampCalculator().latest(chronicle);
    }

    @Override
    default StateSet allowedStates() {
        return stampCalculator().allowedStates();
    }

    StampCalculator stampCalculator();

    @Override
    default RelativePosition relativePosition(int stampNid, int stampNid2) {
        return stampCalculator().relativePosition(stampNid, stampNid2);
    }

    @Override
    default void forEachSemanticVersionOfPattern(int patternNid, BiConsumer<SemanticEntityVersion, PatternEntityVersion> procedure) {
        stampCalculator().forEachSemanticVersionOfPattern(patternNid, procedure);
    }

    @Override
    default void forEachSemanticVersionForComponent(int componentNid, BiConsumer<SemanticEntityVersion, EntityVersion> procedure) {
        stampCalculator().forEachSemanticVersionForComponent(componentNid, procedure);
    }

    @Override
    default void forEachSemanticVersionForComponentOfPattern(int componentNid, int patternNid, TriConsumer<SemanticEntityVersion, EntityVersion, PatternEntityVersion> procedure) {
        stampCalculator().forEachSemanticVersionForComponentOfPattern(componentNid, patternNid, procedure);
    }

    @Override
    default void forEachSemanticVersionWithFieldsForComponent(int componentNid, TriConsumer<SemanticEntityVersion, ImmutableList<Field>, EntityVersion> procedure) {
        stampCalculator().forEachSemanticVersionWithFieldsForComponent(componentNid, procedure);
    }

    @Override
    default Latest<PatternEntityVersion> latestPatternEntityVersion(int patternNid) {
        return stampCalculator().latestPatternEntityVersion(patternNid);
    }

    @Override
    default OptionalInt getIndexForMeaning(int patternNid, int meaningNid) {
        return stampCalculator().getIndexForMeaning(patternNid, meaningNid);
    }

    @Override
    default OptionalInt getIndexForPurpose(int patternNid, int purposeNid) {
        return stampCalculator().getIndexForPurpose(patternNid, purposeNid);
    }
}
