package org.hl7.tinkar.coordinate.view;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.id.IntIdList;
import org.hl7.tinkar.common.id.IntIdSet;
import org.hl7.tinkar.common.util.functional.QuadConsumer;
import org.hl7.tinkar.coordinate.language.LanguageCoordinate;
import org.hl7.tinkar.coordinate.logic.LogicCoordinate;
import org.hl7.tinkar.coordinate.navigation.NavigationCoordinate;
import org.hl7.tinkar.coordinate.stamp.StampCalculator;
import org.hl7.tinkar.coordinate.stamp.StampFilter;
import org.hl7.tinkar.entity.EntityVersion;
import org.hl7.tinkar.entity.Field;
import org.hl7.tinkar.entity.PatternEntityVersion;
import org.hl7.tinkar.entity.SemanticEntityVersion;
import org.hl7.tinkar.common.util.functional.TriConsumer;
import org.hl7.tinkar.terms.ConceptFacade;
import org.hl7.tinkar.terms.EntityFacade;
import org.hl7.tinkar.terms.PatternFacade;

public interface View {

    StampCalculator stampCalculator();

    default StampFilter stampFilter() {
        return stampCalculator().filter();
    }

    LanguageCoordinate language();

    LogicCoordinate logic();

    NavigationCoordinate navigation();

    default IntIdSet parents(ConceptFacade concept) {
        return parents(concept.nid());
    }
    IntIdSet parents(int conceptNid);

    default IntIdSet children(ConceptFacade concept) {
        return children(concept.nid());
    }
    IntIdSet children(int conceptNid);

    default IntIdList sortedParents(ConceptFacade concept) {
        return sortedParents(concept.nid());
    }
    IntIdList sortedParents(int conceptNid);

    default IntIdList sortedChildren(ConceptFacade concept) {
        return sortedChildren(concept.nid());
    }
    IntIdList sortedChildren(int conceptNid);

    default void forEachSemanticVersionWithFieldsOfPattern(PatternFacade patternFacade,
                                                           TriConsumer<SemanticEntityVersion, ImmutableList<Field>, PatternEntityVersion> procedure) {
        forEachSemanticVersionWithFieldsOfPattern(patternFacade.nid(), procedure);
    }

    default void forEachSemanticVersionWithFieldsOfPattern(int patternNid, TriConsumer<SemanticEntityVersion, ImmutableList<Field>, PatternEntityVersion> procedure) {
        stampCalculator().forEachSemanticVersionOfPattern(patternNid, (semanticEntityVersion, patternVersion) -> procedure.accept(semanticEntityVersion, semanticEntityVersion.fields(patternVersion), patternVersion));
    }

    default void forEachSemanticVersionWithFieldsForComponentOfPattern(EntityFacade component,
                                                             PatternFacade patternFacade,
                                                             QuadConsumer<SemanticEntityVersion, ImmutableList<Field>, EntityVersion, PatternEntityVersion> procedure) {
        forEachSemanticVersionWithFieldsForComponentOfPattern(component.nid(), patternFacade.nid(), procedure);
    }

    default void forEachSemanticVersionWithFieldsForComponentOfPattern(int componentNid, int patternNid, QuadConsumer<SemanticEntityVersion, ImmutableList<Field>, EntityVersion, PatternEntityVersion> procedure) {
        stampCalculator().forEachSemanticVersionForComponentOfPattern(componentNid, patternNid, (semanticEntityVersion, entityVersion, patternEntityVersion) -> procedure.accept(semanticEntityVersion, semanticEntityVersion.fields(patternEntityVersion), entityVersion, patternEntityVersion));
    }

    default void forEachSemanticVersionWithFieldsForComponent(EntityFacade component,
                                                              TriConsumer<SemanticEntityVersion, ImmutableList<Field>, EntityVersion> procedure) {
        forEachSemanticVersionWithFieldsForComponent(component.nid(), procedure);
    }

    void forEachSemanticVersionWithFieldsForComponent(int componentNid,
                                                      TriConsumer<SemanticEntityVersion, ImmutableList<Field>, EntityVersion> procedure);
}
