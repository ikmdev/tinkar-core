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
package dev.ikm.tinkar.coordinate.stamp.calculator;

import dev.ikm.tinkar.coordinate.stamp.StateSet;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.PrimitiveDataSearchResult;
import dev.ikm.tinkar.common.util.functional.QuadConsumer;
import dev.ikm.tinkar.common.util.functional.TriConsumer;
import dev.ikm.tinkar.component.graph.DiTree;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.entity.graph.DiTreeVersion;
import dev.ikm.tinkar.entity.graph.VersionVertex;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.PatternFacade;

import java.util.List;
import java.util.Optional;
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

    <V extends EntityVersion> List<DiTreeVersion<V>> getVersionGraphList(Entity<V> chronicle);

    /**
     * @param semanticNid identifier of the semantic to test its latest version against the provided fields.
     * @param fields      Fields of the current state, which will be used to create a new version if necessary.
     * @param stampNid    stampNid of the new version that will contain the changed fields.
     * @return Optional new semantic record containing new version. Caller is responsible to write to entity store
     * and manage associated transaction.
     */
    default Optional<SemanticRecord> updateIfFieldsChanged(int semanticNid, ImmutableList<Object> fields, int stampNid) {
        return updateIfFieldsChanged(Entity.getFast(semanticNid), fields, stampNid);
    }

    /**
     * @param chronicle
     * @param fields
     * @param stampNid
     * @return a new SemanticRecord with the new SemanticVersionRecord added. It is the responsibility of the caller
     * to write to the store, and manage transactions.
     */
    default Optional<SemanticRecord> updateIfFieldsChanged(SemanticRecord chronicle, ImmutableList<Object> fields, int stampNid) {
        Latest<SemanticVersionRecord> latest = latest(chronicle);
        if (latest.isPresent()) {
            for (SemanticVersionRecord version : latest.getWithContradictions()) {
                if (version.fieldValues().equals(fields)) {
                    return Optional.empty();
                }
            }
        }
        return Optional.of(chronicle.with(new SemanticVersionRecord(chronicle, stampNid, fields)).build());
    }

    default SemanticRecord updateFields(int semanticNid, ImmutableList<Object> fields, int stampNid) {
        return updateFields(Entity.getFast(semanticNid), fields, stampNid);
    }

    default SemanticRecord updateFields(SemanticRecord chronicle, ImmutableList<Object> fields, int stampNid) {
        return chronicle.with(new SemanticVersionRecord(chronicle, stampNid, fields)).build();
    }

    <V extends EntityVersion> Latest<V> latest(Entity<V> chronicle);

    default Optional<SemanticRecord> updateIfFieldsChanged(int semanticNid, ImmutableList<Object> fields, StampEntity stampEntity) {
        return updateIfFieldsChanged(Entity.getFast(semanticNid), fields, stampEntity.nid());
    }

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

    void forEachSemanticVersionOfPatternParallel(int patternNid, BiConsumer<SemanticEntityVersion, PatternEntityVersion> procedure);

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

    default <T extends Object> Latest<Field<T>> getFieldForSemanticWithMeaning(SemanticEntityVersion semanticVersion, EntityFacade meaning) {
        return getFieldForSemanticWithMeaning(Latest.of(semanticVersion), meaning);
    }
    default <T extends Object> Latest<Field<T>> getFieldForSemanticWithPurpose(SemanticEntityVersion semanticVersion, EntityFacade purpose) {
        return getFieldForSemanticWithPurpose(Latest.of(semanticVersion), purpose);
    }

    default <T extends Object> Latest<Field<T>> getFieldForSemanticWithMeaning(Latest<SemanticEntityVersion> latestSemantic, EntityFacade meaning) {
        return getFieldForSemantic(latestSemantic, meaning.nid(), FieldCriterion.MEANING);
    }
    default <T extends Object> Latest<Field<T>> getFieldForSemanticWithPurpose(Latest<SemanticEntityVersion> latestSemantic, EntityFacade meaning) {
        return getFieldForSemantic(latestSemantic, meaning.nid(), FieldCriterion.PURPOSE);
    }

    <T> Latest<Field<T>> getFieldForSemantic(Latest<SemanticEntityVersion> latestSemanticVersion, int criterionNid, FieldCriterion fieldCriterion);

    default <T extends Object> Latest<Field<T>> getFieldForSemanticWithMeaning(SemanticEntityVersion semanticVersion, int meaningNid) {
        return getFieldForSemantic(Latest.of(semanticVersion), meaningNid, FieldCriterion.MEANING);
    }
    default <T extends Object> Latest<Field<T>> getFieldForSemanticWithPurpose(SemanticEntityVersion semanticVersion, int purposeNid) {
        return getFieldForSemantic(Latest.of(semanticVersion), purposeNid, FieldCriterion.PURPOSE);
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

    default boolean latestIsActive(Entity entity) {
        Latest<EntityVersion> latest = latest(entity);
        if (latest.isPresent()) {
            return latest.get().active();
        }
        return false;
    }

    default boolean latestIsActive(int nid) {
        Latest<EntityVersion> latest = latest(nid);
        if (latest.isPresent()) {
            return latest.get().active();
        }
        return false;
    }



    enum FieldCriterion {MEANING, PURPOSE}
}
