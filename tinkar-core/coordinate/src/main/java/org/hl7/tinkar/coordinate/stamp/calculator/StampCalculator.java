package org.hl7.tinkar.coordinate.stamp.calculator;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.util.functional.QuadConsumer;
import org.hl7.tinkar.common.util.functional.TriConsumer;
import org.hl7.tinkar.coordinate.stamp.calculator.Latest;
import org.hl7.tinkar.coordinate.stamp.calculator.RelativePosition;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.terms.EntityFacade;
import org.hl7.tinkar.terms.PatternFacade;

import java.util.function.BiConsumer;

public interface StampCalculator {

    <V extends EntityVersion> Latest<V> latest(Entity<V> chronicle);

    RelativePosition relativePosition(int stampNid, int stampNid2);

    default RelativePosition relativePosition(EntityVersion v1, EntityVersion v2) {
        return relativePosition(v1.stampNid(), v2.stampNid());
    }

    default RelativePosition relativePosition(StampEntity stamp1, StampEntity stamp2) {
        return relativePosition(stamp1.nid(), stamp2.nid());
    }

    default <V extends EntityVersion> Latest<V> latest(EntityFacade entityFacade) {
        return latest(entityFacade.nid());
    }

    <V extends EntityVersion> Latest<V> latest(int nid);

    default void forEachSemanticVersionOfPattern(PatternFacade patternFacade,
                                                 BiConsumer<SemanticEntityVersion, PatternEntityVersion> procedure) {
        forEachSemanticVersionOfPattern(patternFacade.nid(), procedure);

    }

    void forEachSemanticVersionOfPattern(int patternNid, BiConsumer<SemanticEntityVersion, PatternEntityVersion> procedure);

    default void forEachSemanticVersionForComponent(EntityFacade component,
                                                    BiConsumer<SemanticEntityVersion, EntityVersion> procedure) {
        forEachSemanticVersionForComponent(component.nid(), procedure);
    }

    void forEachSemanticVersionForComponent(int componentNid,
                                            BiConsumer<SemanticEntityVersion, EntityVersion> procedure);

    default void forEachSemanticVersionForComponentOfPattern(EntityFacade component,
                                                             PatternFacade patternFacade,
                                                             TriConsumer<SemanticEntityVersion, EntityVersion, PatternEntityVersion> procedure) {
        forEachSemanticVersionForComponentOfPattern(component.nid(), patternFacade.nid(), procedure);
    }

    void forEachSemanticVersionForComponentOfPattern(int componentNid, int patternNid, TriConsumer<SemanticEntityVersion, EntityVersion, PatternEntityVersion> procedure);


    default void forEachSemanticVersionWithFieldsOfPattern(PatternFacade patternFacade,
                                                           TriConsumer<SemanticEntityVersion, ImmutableList<Field>, PatternEntityVersion> procedure) {
        forEachSemanticVersionWithFieldsOfPattern(patternFacade.nid(), procedure);
    }

    default void forEachSemanticVersionWithFieldsOfPattern(int patternNid, TriConsumer<SemanticEntityVersion, ImmutableList<Field>, PatternEntityVersion> procedure) {
        forEachSemanticVersionOfPattern(patternNid, (semanticEntityVersion, patternVersion) -> procedure.accept(semanticEntityVersion, semanticEntityVersion.fields(patternVersion), patternVersion));
    }

    default void forEachSemanticVersionWithFieldsForComponentOfPattern(EntityFacade component,
                                                                       PatternFacade patternFacade,
                                                                       QuadConsumer<SemanticEntityVersion, ImmutableList<Field>, EntityVersion, PatternEntityVersion> procedure) {
        forEachSemanticVersionWithFieldsForComponentOfPattern(component.nid(), patternFacade.nid(), procedure);
    }

    default void forEachSemanticVersionWithFieldsForComponentOfPattern(int componentNid, int patternNid, QuadConsumer<SemanticEntityVersion, ImmutableList<Field>, EntityVersion, PatternEntityVersion> procedure) {
        forEachSemanticVersionForComponentOfPattern(componentNid, patternNid, (semanticEntityVersion, entityVersion, patternEntityVersion) -> procedure.accept(semanticEntityVersion, semanticEntityVersion.fields(patternEntityVersion), entityVersion, patternEntityVersion));
    }

    default void forEachSemanticVersionWithFieldsForComponent(EntityFacade component,
                                                              TriConsumer<SemanticEntityVersion, ImmutableList<Field>, EntityVersion> procedure) {
        forEachSemanticVersionWithFieldsForComponent(component.nid(), procedure);
    }

    void forEachSemanticVersionWithFieldsForComponent(int componentNid,
                                                      TriConsumer<SemanticEntityVersion, ImmutableList<Field>, EntityVersion> procedure);

}
