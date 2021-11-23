package org.hl7.tinkar.coordinate.stamp.calculator;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.service.PrimitiveDataSearchResult;
import org.hl7.tinkar.common.util.functional.QuadConsumer;
import org.hl7.tinkar.common.util.functional.TriConsumer;
import org.hl7.tinkar.component.graph.DiTree;
import org.hl7.tinkar.coordinate.stamp.StateSet;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.entity.graph.VersionVertex;
import org.hl7.tinkar.terms.EntityFacade;
import org.hl7.tinkar.terms.PatternFacade;

import java.util.List;
import java.util.OptionalInt;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public interface StampCalculator {
    default Stream<Latest<SemanticEntityVersion>> streamLatestVersionForPattern(PatternFacade patternFacade) {
        return streamLatestVersionForPattern(patternFacade.nid());
    }

    Stream<Latest<SemanticEntityVersion>> streamLatestVersionForPattern(int patternNid);

    default Stream<SemanticEntityVersion> streamLatestActiveVersionForPattern(PatternFacade patternFacade) {
        return streamLatestActiveVersionForPattern(patternFacade.nid());
    }

    default Stream<SemanticEntityVersion> streamLatestActiveVersionForPattern(int patternNid) {
        return streamLatestVersionForPattern(patternNid)
                .filter(latestVersion -> latestVersion.ifAbsentOrFunction(() -> false, latest -> latest.active()))
                .map(semanticEntityVersionLatest -> semanticEntityVersionLatest.get());
    }

    default Stream<Entity> streamReferencedComponentIfSemanticActiveForPattern(PatternFacade patternFacade) {
        return streamReferencedComponentIfSemanticActiveForPattern(patternFacade.nid());
    }

    default Stream<Entity> streamReferencedComponentIfSemanticActiveForPattern(int patternNid) {
        return streamLatestActiveVersionForPattern(patternNid)
                .map(semanticEntityVersion -> Entity.getFast(semanticEntityVersion.referencedComponentNid()));
    }

    default List<ConceptEntity> referencedConceptsIfSemanticActiveForPattern(PatternFacade patternFacade) {
        return referencedConceptsIfSemanticActiveForPattern(patternFacade.nid());
    }

    default List<ConceptEntity> referencedConceptsIfSemanticActiveForPattern(int patternNid) {
        return streamReferencedComponentIfSemanticActiveForPattern(patternNid)
                .filter(entity -> entity instanceof ConceptEntity)
                .map(entity -> (ConceptEntity) entity).toList();
    }

    default boolean isLatestActive(EntityFacade facade) {
        return isLatestActive(facade.nid());
    }

    default boolean isLatestActive(int nid) {
        Latest<EntityVersion> latest = latest(nid);
        if (latest.isPresent()) {
            return StateSet.ACTIVE.contains(latest.get().stamp().state());
        }
        return false;
    }

    <V extends EntityVersion> Latest<V> latest(int nid);

    <V extends EntityVersion> List<DiTree<VersionVertex<V>>> getVersionGraphList(Entity<V> chronicle);

    <V extends EntityVersion> Latest<V> latest(Entity<V> chronicle);

    StateSet allowedStates();

    default RelativePosition relativePosition(EntityVersion v1, EntityVersion v2) {
        return relativePosition(v1.stampNid(), v2.stampNid());
    }

    RelativePosition relativePosition(int stampNid, int stampNid2);

    default RelativePosition relativePosition(StampEntity stamp1, StampEntity stamp2) {
        return relativePosition(stamp1.nid(), stamp2.nid());
    }

    default <V extends EntityVersion> Latest<V> latest(EntityFacade entityFacade) {
        return latest(entityFacade.nid());
    }

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
                                                           TriConsumer<SemanticEntityVersion, ImmutableList<? extends Field>, PatternEntityVersion> procedure) {
        forEachSemanticVersionWithFieldsOfPattern(patternFacade.nid(), procedure);
    }

    default void forEachSemanticVersionWithFieldsOfPattern(int patternNid, TriConsumer<SemanticEntityVersion, ImmutableList<? extends Field>, PatternEntityVersion> procedure) {
        forEachSemanticVersionOfPattern(patternNid, (semanticEntityVersion, patternVersion) -> procedure.accept(semanticEntityVersion, semanticEntityVersion.fields(patternVersion), patternVersion));
    }

    default void forEachSemanticVersionWithFieldsForComponentOfPattern(EntityFacade component,
                                                                       PatternFacade patternFacade,
                                                                       QuadConsumer<SemanticEntityVersion, ImmutableList<? extends Field>, EntityVersion, PatternEntityVersion> procedure) {
        forEachSemanticVersionWithFieldsForComponentOfPattern(component.nid(), patternFacade.nid(), procedure);
    }

    default void forEachSemanticVersionWithFieldsForComponentOfPattern(int componentNid, int patternNid, QuadConsumer<SemanticEntityVersion, ImmutableList<? extends Field>, EntityVersion, PatternEntityVersion> procedure) {
        forEachSemanticVersionForComponentOfPattern(componentNid, patternNid, (semanticEntityVersion, entityVersion, patternEntityVersion) -> procedure.accept(semanticEntityVersion, semanticEntityVersion.fields(patternEntityVersion), entityVersion, patternEntityVersion));
    }

    default void forEachSemanticVersionWithFieldsForComponent(EntityFacade component,
                                                              TriConsumer<SemanticEntityVersion, ImmutableList<? extends Field>, EntityVersion> procedure) {
        forEachSemanticVersionWithFieldsForComponent(component.nid(), procedure);
    }

    void forEachSemanticVersionWithFieldsForComponent(int componentNid,
                                                      TriConsumer<SemanticEntityVersion, ImmutableList<? extends Field>, EntityVersion> procedure);

    default Latest<PatternEntityVersion> latestPatternEntityVersion(PatternFacade patternFacade) {
        return latestPatternEntityVersion(patternFacade.nid());
    }

    Latest<PatternEntityVersion> latestPatternEntityVersion(int patternNid);

    OptionalInt getIndexForMeaning(int patternNid, int meaningNid);

    OptionalInt getIndexForPurpose(int patternNid, int meaningNid);

    // TODO add methods for getFieldForSemanticWithPurpose
    default <T extends Object> Latest<Field<T>> getFieldForSemanticWithMeaning(SemanticEntityVersion semanticVersion, EntityFacade meaning) {
        return getFieldForSemanticWithMeaning(Latest.of(semanticVersion), meaning);
    }

    // TODO add methods for getFieldForSemanticWithPurpose
    default <T extends Object> Latest<Field<T>> getFieldForSemanticWithMeaning(Latest<SemanticEntityVersion> latestSemantic, EntityFacade meaning) {
        return getFieldForSemantic(latestSemantic, meaning.nid(), FieldCriterion.MEANING);
    }

    <T> Latest<Field<T>> getFieldForSemantic(Latest<SemanticEntityVersion> latestSemanticVersion, int criterionNid, FieldCriterion fieldCriterion);

    // TODO add methods for getFieldForSemanticWithPurpose
    default <T extends Object> Latest<Field<T>> getFieldForSemanticWithMeaning(SemanticEntityVersion semanticVersion, int meaningNid) {
        return getFieldForSemantic(Latest.of(semanticVersion), meaningNid, FieldCriterion.MEANING);
    }

    default <T extends Object> Latest<Field<T>> getFieldForSemanticWithPurpose(Latest<SemanticEntityVersion> latestSemantic, int meaningNid) {
        return getFieldForSemantic(latestSemantic, meaningNid, FieldCriterion.PURPOSE);
    }

    default <T extends Object> Latest<Field<T>> getFieldForSemanticWithMeaning(Latest<SemanticEntityVersion> latestSemantic, int meaningNid) {
        return getFieldForSemantic(latestSemantic, meaningNid, FieldCriterion.MEANING);
    }

    default <T extends Object> Latest<Field<T>> getFieldForSemanticWithMeaning(int componentNid, EntityFacade meaning) {
        return getFieldForSemantic(componentNid, meaning.nid(), FieldCriterion.MEANING);
    }

    <T> Latest<Field<T>> getFieldForSemantic(int componentNid, int criterionNid, FieldCriterion fieldCriterion);

    default <T extends Object> Latest<Field<T>> getFieldForSemanticWithMeaning(EntityFacade component, EntityFacade meaning) {
        return getFieldForSemantic(component.nid(), meaning.nid(), FieldCriterion.MEANING);
    }

    default <T extends Object> Latest<Field<T>> getFieldForSemanticWithPurpose(int componentNid, EntityFacade purpose) {
        return getFieldForSemantic(componentNid, purpose.nid(), FieldCriterion.PURPOSE);
    }

    default <T extends Object> Latest<Field<T>> getFieldForSemanticWithPurpose(int componentNid, int purposeNid) {
        return getFieldForSemantic(componentNid, purposeNid, FieldCriterion.PURPOSE);
    }

    default <T extends Object> Latest<Field<T>> getFieldForSemanticWithPurpose(EntityFacade component, EntityFacade purpose) {
        return getFieldForSemantic(component.nid(), purpose.nid(), FieldCriterion.PURPOSE);
    }

    default <T extends Object> Latest<Field<T>> getFieldForSemanticWithMeaning(int componentNid, int meaningNid) {
        return getFieldForSemantic(componentNid, meaningNid, FieldCriterion.MEANING);
    }

    default ImmutableList<LatestVersionSearchResult> search(String query, int maxResultSize) throws Exception {
        PrimitiveDataSearchResult[] primitiveResults = PrimitiveData.get().search(query, maxResultSize);
        MutableList<LatestVersionSearchResult> latestResults = Lists.mutable.withInitialCapacity(primitiveResults.length);
        for (PrimitiveDataSearchResult primitiveResult : primitiveResults) {
            Latest<SemanticEntityVersion> latestVersion = latest(primitiveResult.nid());
            latestVersion.ifPresent(semanticVersion -> latestResults.add(
                    new LatestVersionSearchResult(latestVersion, primitiveResult.fieldIndex(), primitiveResult.score(),
                            primitiveResult.highlightedString())));
        }
        return latestResults.toImmutable();
    }


    enum FieldCriterion {MEANING, PURPOSE}
}
