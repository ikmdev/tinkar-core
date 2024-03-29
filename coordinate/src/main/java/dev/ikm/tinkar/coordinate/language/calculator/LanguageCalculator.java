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
package dev.ikm.tinkar.coordinate.language.calculator;

import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.component.Stamp;
import dev.ikm.tinkar.coordinate.language.LanguageCoordinate;
import dev.ikm.tinkar.coordinate.language.LanguageCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StampCoordinate;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.entity.*;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import dev.ikm.tinkar.common.id.IntIdList;
import dev.ikm.tinkar.common.id.IntIdSet;
import dev.ikm.tinkar.common.id.IntIds;
import dev.ikm.tinkar.common.util.text.NaturalOrder;
import dev.ikm.tinkar.common.util.time.DateTimeUtil;
import dev.ikm.tinkar.terms.*;

import java.util.*;
import java.util.function.Function;

public interface LanguageCalculator {

    ImmutableList<LanguageCoordinateRecord> languageCoordinateList();

    default ImmutableList<SemanticEntity> getDescriptionsForComponent(EntityFacade entityFacade) {
        return getDescriptionsForComponent(entityFacade.nid());
    }

    /**
     * Gets all descriptions from the first pattern in the language coordinate pattern priority list
     * that contains any descriptions.
     *
     * @param componentNid
     * @return descriptions from the first pattern in the the language coordinate pattern priority list
     * that contains any descriptions.
     */
    ImmutableList<SemanticEntity> getDescriptionsForComponent(int componentNid);


    default ImmutableList<SemanticEntityVersion> getDescriptionsForComponentOfType(EntityFacade entityFacade,
                                                                                   ConceptFacade descriptionTypeFacade) {
        return getDescriptionsForComponentOfType(entityFacade.nid(), descriptionTypeFacade.nid());
    }

    ImmutableList<SemanticEntityVersion> getDescriptionsForComponentOfType(int componentNid,
                                                                           int descriptionTypeNid);

    default ImmutableList<String> getPreferredDescriptionTextListForComponents(IntIdSet ids) {
        return getPreferredDescriptionTextListForComponents(ids.toArray());
    }

    default ImmutableList<String> getPreferredDescriptionTextListForComponents(int... nids) {
        MutableList<String> descriptionTextList = Lists.mutable.ofInitialCapacity(nids.length);
        for (int nid : nids) {
            descriptionTextList.add(getPreferredDescriptionTextWithFallbackOrNid(nid));
        }
        return descriptionTextList.toImmutable();
    }

    default String getPreferredDescriptionTextWithFallbackOrNid(int nid) {
        Optional<String> optionalResult = getRegularDescriptionText(nid);
        if (optionalResult.isPresent()) {
            return optionalResult.get();
        }
        optionalResult = getFullyQualifiedNameText(nid);
        if (optionalResult.isPresent()) {
            return optionalResult.get();
        }
        optionalResult = getSemanticText(nid);
        if (optionalResult.isPresent()) {
            return optionalResult.get();
        }
        return Integer.toString(nid);
    }

    Optional<String> getRegularDescriptionText(int entityNid);

    default Optional<String> getFullyQualifiedNameText(int componentNid) {
        return getDescriptionTextForComponentOfType(componentNid, TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid());
    }

    Optional<String> getSemanticText(int nid);

    Optional<String> getDescriptionTextForComponentOfType(int entityNid, int descriptionTypeNid);

    /**
     * Gets the text of type {@link TinkarTerm#FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE}.  Will return empty, if
     * no matching description type is found in this or any nested language coordinates
     *
     * @param componentFacade the component to get a regular name for.
     * @return the regular name text
     */


    default Optional<String> getFullyQualifiedNameText(EntityFacade componentFacade) {
        return getFullyQualifiedNameText(componentFacade.nid());
    }

    default ImmutableList<String> getPreferredDescriptionTextListForComponents(EntityFacade... entities) {
        MutableList<String> descriptionTextList = Lists.mutable.ofInitialCapacity(entities.length);
        for (EntityFacade entity : entities) {
            descriptionTextList.add(getPreferredDescriptionTextWithFallbackOrNid(entity));
        }
        return descriptionTextList.toImmutable();
    }

    default String getPreferredDescriptionTextWithFallbackOrNid(EntityFacade entityFacade) {
        return getPreferredDescriptionTextWithFallbackOrNid(entityFacade.nid());
    }

    default ImmutableList<String> getFullyQualifiedTextListForComponents(EntityFacade... entities) {
        MutableList<String> descriptionTextList = Lists.mutable.ofInitialCapacity(entities.length);
        for (EntityFacade entity : entities) {
            descriptionTextList.add(getFullyQualifiedNameTextOrNid(entity));
        }
        return descriptionTextList.toImmutable();
    }

    default String getFullyQualifiedNameTextOrNid(EntityFacade entityFacade) {
        return getFullyQualifiedNameTextOrNid(entityFacade.nid());
    }

    default String getFullyQualifiedNameTextOrNid(int componentNid) {
        Optional<String> optionalText = getDescriptionTextForComponentOfType(componentNid, TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid());
        if (optionalText.isPresent()) {
            return optionalText.get();
        }
        return Integer.toString(componentNid);
    }

    default ImmutableList<String> getDescriptionTextListForComponents(EntityFacade... entities) {
        MutableList<String> descriptionTextList = Lists.mutable.ofInitialCapacity(entities.length);
        for (EntityFacade entity : entities) {
            descriptionTextList.add(getDescriptionTextOrNid(entity));
        }
        return descriptionTextList.toImmutable();
    }

    default String getDescriptionTextOrNid(EntityFacade entity) {
        return getDescriptionTextOrNid(entity.nid());
    }

    default String getDescriptionTextOrNid(int componentNid) {
        Optional<String> text = getDescriptionText(componentNid);
        if (text.isPresent()) {
            return text.get();
        }
        return Integer.toString(componentNid);
    }

    /**
     * @param componentNid
     * @return Return the latestDescription according to the type and dialect preferences of this {@code LanguageCoordinate}.
     */
    Optional<String> getDescriptionText(int componentNid);

    Optional<String> getAnyName(int componentNid);

    /*
    Allow the pattern to also define a pattern for user text.
     */
    Optional<String> getUserText();

    /**
     * @param entity
     * @return Return the latestDescription according to the type and dialect preferences of this {@code LanguageCoordinate}.
     * @see #getDescriptionText(int)
     */
    default Optional<String> getDescriptionText(EntityFacade entity) {
        return this.getDescriptionText(entity.nid());
    }

    default Latest<SemanticEntityVersion> getDescription(EntityFacade entityFacade) {
        return getDescription(entityFacade.nid());
    }

    /**
     * Return the latestDescription according to the type and dialect preferences of this {@code LanguageCoordinate}.
     * or a nested {@code LanguageCoordinate}
     *
     * @param entityNid the concept nid.
     * @return an optional latestDescription best matching the {@code LanguageCoordinate} constraints.
     */
    default Latest<SemanticEntityVersion> getDescription(int entityNid) {
        return getDescription(getDescriptionsForComponent(entityNid));
    }

    /**
     * Return the latestDescription according to the pattern, type and dialect preferences of this {@code LanguageCoordinate}.
     *
     * @param descriptionList descriptions to consider
     * @return an optional latestDescription best matching the {@code LanguageCoordinate} constraints.
     */
    default Latest<SemanticEntityVersion> getDescription(ImmutableList<SemanticEntity> descriptionList) {
        return getSpecifiedDescription(descriptionList);
    }

    /**
     * TODO needs update. Add info on patterns, and we don't use getNextPriorityLanguageCoordinate anymore...
     * The developer can pass an ordered list of language coordinates to the language stampCoordinateRecord.
     * <p>
     * Gets the specified description(s).
     * <p>
     * Iterates over the list of supplied descriptions, finding the descriptions that match the highest ranked
     * {@link LanguageCoordinate#descriptionTypePreferenceNidList()} (first item in the array) and the
     * {@link LanguageCoordinate#languageConceptNid()}.  If no descriptions match, the process is repeated
     * with each subsequent item in {@link LanguageCoordinate#descriptionTypePreferenceNidList()}, walking
     * through the array one by one.
     * <p>
     * For any given step, if multiple descriptions match the criteria, an ACTIVE description should have priority over
     * an inactive one.
     * <p>
     * To be returned, a description MUST match one of the description types, and the specified language.
     * <p>
     * If the specified language {@link LanguageCoordinate#languageConceptNid()} is {@link TinkarTerm#LANGUAGE},
     * then language will be considered to always match, ignoring the actual value of the language in the description.
     * This allows this method to be used with a fallback behavior - where it will match a description of any language,
     * but still rank by the requested type.
     * <p>
     * For any descriptions that matched the criteria, they are then compared with the requested
     * {@link LanguageCoordinate#dialectPatternPreferenceNidList()}
     * The dialect preferences are evaluated in array order.  Each description that has a dialect annotation that matches
     * the dialect preference, with a type of {@link TinkarTerm#PREFERRED}, it is advanced to the next ranking step (below)
     * <p>
     * If none of the descriptions has a dialect annotation of type {@link TinkarTerm#PREFERRED} that matches a dialect
     * in the {@link LanguageCoordinate#dialectPatternPreferenceNidList()}, then all matching language / type matching
     * descriptions are advanced to the next ranking step (below).
     * <p>
     * The final ranking step, is to evaluate {@link LanguageCoordinate#modulePreferenceNidListForLanguage()}
     * The module list is evaluated in order.  If a description matches the requested module, then it is placed
     * into the top position, so it is returned via {@link Latest#get()}.  All other descriptions are still
     * returned, but as part of the {@link Latest#contradictions()}.
     * <p>
     * If none of the description match a specified module ranking, then the descriptions are returned in an arbitrary order,
     * between {@link Latest#get()} and {@link Latest#contradictions()}.
     *
     * @param descriptionList List of descriptions to consider.
     * @return the specified description
     */
    Latest<SemanticEntityVersion> getSpecifiedDescription(ImmutableList<SemanticEntity> descriptionList);

    Optional<String> getTextFromSemanticVersion(SemanticEntityVersion semanticEntityVersion);

    default String getPreferredTextForStamp(int stampNid) {
        return getPreferredTextForStamp(Entity.getStamp(stampNid));
    }
    default String getPreferredTextForStamp(StampEntity stamp) {
        StampVersion lastVersion = stamp.lastVersion();
        return "s:" + getPreferredDescriptionStringOrNid(lastVersion.stateNid()) +
                " t:" + DateTimeUtil.format(lastVersion.time(), DateTimeUtil.SEC_FORMATTER) +
                " a:" + getPreferredDescriptionStringOrNid(lastVersion.authorNid()) +
                " m:" + getPreferredDescriptionStringOrNid(lastVersion.moduleNid()) +
                " p:" + getPreferredDescriptionStringOrNid(lastVersion.pathNid());
    }
    default String getFullyQualifiedTextForStamp(int stampNid) {
        return getFullyQualifiedTextForStamp(Entity.getStamp(stampNid));
    }
    default String getFullyQualifiedTextForStamp(StampEntity stamp) {
        StampVersion lastVersion = stamp.lastVersion();
        StringBuilder sb = new StringBuilder();
        return "s:" + getFullyQualifiedNameTextOrNid(lastVersion.stateNid()) +
                " t:" + DateTimeUtil.format(lastVersion.time(), DateTimeUtil.TEXT_FORMAT_WITH_ZONE) +
                " a:" + getFullyQualifiedNameTextOrNid(lastVersion.authorNid()) +
                " m:" + getFullyQualifiedNameTextOrNid(lastVersion.moduleNid()) +
                " p:" + getFullyQualifiedNameTextOrNid(lastVersion.pathNid());
    }

    default String getFullyQualifiedDescriptionTextWithFallbackOrNid(EntityFacade entityFacade) {
        return getFullyQualifiedDescriptionTextWithFallbackOrNid(entityFacade.nid());
    }

    default String getFullyQualifiedDescriptionTextWithFallbackOrNid(int nid) {
        Optional<String> optionalResult = getFullyQualifiedNameText(nid);
        if (optionalResult.isPresent()) {
            return optionalResult.get();
        }
        optionalResult = getRegularDescriptionText(nid);
        if (optionalResult.isPresent()) {
            return optionalResult.get();
        }
        optionalResult = getSemanticText(nid);
        if (optionalResult.isPresent()) {
            return optionalResult.get();
        }
        return Integer.toString(nid);
    }

    default String getPreferredDescriptionStringOrNid(int nid) {
        return toEntityStringOrNid(nid, this::getRegularDescriptionText);
    }

    default String toEntityStringOrNid(int nid, Function<Integer, Optional<String>> toOptionalEntityString) {
        Optional<String> optionalEntityString = toOptionalEntityString.apply(nid);
        if (optionalEntityString.isPresent()) {
            return optionalEntityString.get();
        }
        return Integer.toString(nid);
    }

    default String getPreferredDescriptionStringOrNid(EntityFacade entityFacade) {
        return toEntityStringOrNid(entityFacade, this::getRegularDescriptionText);
    }

    default String toEntityStringOrNid(EntityFacade entityFacade, Function<EntityFacade, Optional<String>> toOptionalEntityString) {
        Optional<String> optionalEntityString = toOptionalEntityString.apply(entityFacade);
        if (optionalEntityString.isPresent()) {
            return optionalEntityString.get();
        }
        return Integer.toString(entityFacade.nid());
    }

    default Optional<String> getRegularDescriptionText(EntityFacade entity) {
        return getRegularDescriptionText(entity.nid());
    }

    /**
     * Used where a String property is optionally an Entity XML fragment, or
     * similar circumstances.
     *
     * @param possibleEntityString
     * @return
     */
    default String toPreferredEntityStringOrInputString(String possibleEntityString) {
        return toEntityStringOrInputString(possibleEntityString, this::getRegularDescriptionText);
    }

    default String toEntityStringOrInputString(String possibleEntityString, Function<Integer, Optional<String>> toOptionalEntityString) {
        Optional<EntityProxy> optionalEntity = ProxyFactory.fromXmlFragmentOptional(possibleEntityString);
        if (optionalEntity.isPresent()) {
            Optional<String> optionalEntityString = toOptionalEntityString.apply(optionalEntity.get().nid());
            if (optionalEntityString.isPresent()) {
                return optionalEntityString.get();
            }
        }
        return possibleEntityString;
    }

    /**
     * Used where a String property is optionally an Entity XML fragment, or
     * similar circumstances.
     *
     * @param possibleEntityString
     * @return
     */
    default String toFullyQualifiedEntityStringOrInputString(String possibleEntityString) {
        return toEntityStringOrInputString(possibleEntityString, this::getFullyQualifiedNameText);
    }

    default String toEntityStringOrPublicIdAndNid(EntityFacade entityFacade) {
        return toEntityStringOrPublicIdAndNid(entityFacade, this::getRegularDescriptionText);
    }

    default String toEntityStringOrPublicIdAndNid(EntityFacade entityFacade, Function<EntityFacade, Optional<String>> toOptionalEntityString) {
        Optional<String> optionalEntityString = toOptionalEntityString.apply(entityFacade);
        if (optionalEntityString.isPresent()) {
            return optionalEntityString.get();
        }
        return Entity.get(entityFacade).get().publicId().toString() + " <" + Integer.toString(entityFacade.nid()) + ">";
    }

    default String toEntityString(Object object, Function<EntityFacade, String> toEntityString) {
        StringBuilder sb = new StringBuilder();
        toEntityString(object, toEntityString, sb);
        return sb.toString();
    }

    default void toEntityString(Object object, Function<EntityFacade, String> toEntityString, StringBuilder sb) {
        if (object == null) {
            return;
        }
        if (object instanceof EntityFacade entityFacade) {
            sb.append(toEntityString.apply(entityFacade));
        } else if (object instanceof Collection collection) {

            if (object instanceof Set set) {
                // a set, so order does not matter. Alphabetic order desirable.
                if (set.isEmpty()) {
                    toEntityString(set.toArray(), toEntityString, sb);
                } else {
                    Object[] conceptSpecs = set.toArray();
                    Arrays.sort(conceptSpecs, (o1, o2) ->
                            NaturalOrder.compareStrings(toEntityString.apply((EntityFacade) o1), toEntityString.apply((EntityFacade) o2)));
                    toEntityString(conceptSpecs, toEntityString, sb);
                }
            } else {
                // not a set, so order matters
                toEntityString(collection.toArray(), toEntityString, sb);
            }
        } else if (object.getClass().isArray()) {
            Object[] a = (Object[]) object;
            final int iMax = a.length - 1;
            if (iMax == -1) {
                sb.append("[]");
            } else {
                sb.append('[');
                int indent = sb.length();
                for (int i = 0; ; i++) {
                    if (i > 0) {
                        sb.append('\u200A');
                    }
                    sb.append(toEntityString(a[i], toEntityString));
                    if (i == iMax) {
                        sb.append(']').toString();
                        return;
                    }
                    if (iMax > 0) {
                        sb.append(",\n");
                        for (int indentIndex = 0; indentIndex < indent; indentIndex++) {
                            sb.append('\u2004'); //
                        }
                    }
                }
            }
        } else if (object instanceof String string) {
            Optional<EntityProxy> optionalEntity = ProxyFactory.fromXmlFragmentOptional(string);
            if (optionalEntity.isPresent()) {
                sb.append(toEntityString(optionalEntity.get(), toEntityString));
            } else {
                sb.append(string);
            }
        } else if (object instanceof Long) {
            sb.append(DateTimeUtil.format((Long) object));
        } else {
            sb.append(object.toString());
        }
    }

    /**
     * Gets the latestDescription of type {@link TinkarTerm#REGULAR_NAME_DESCRIPTION_TYPE}, according to dialect preferences.
     * Will return empty, if no matching description type is found in this or any nested language coordinates
     *
     * @param descriptionList the latestDescription list
     * @return the regular name latestDescription, if available
     */
    default Latest<SemanticEntityVersion> getRegularDescription(ImmutableList<SemanticEntity> descriptionList) {
        return getSpecifiedDescription(descriptionList, IntIds.list.of(TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE.nid()));
    }

    /**
     * Same as getSpecifiedDescription(StampFilter stampCoordinateRecord,
     * List<SemanticChronology> descriptionList,
     * LanguageCoordinate languageCoordinate);
     * but allows the descriptionTypePriority to be independent of the coordinate, without forcing a clone of
     * the coordinate.
     *
     * @param descriptionList
     * @param descriptionTypePriority
     * @return
     */

    Latest<SemanticEntityVersion> getSpecifiedDescription(ImmutableList<SemanticEntity> descriptionList,
                                                          IntIdList descriptionTypePriority);

    /**
     * Return a description of type {@link TinkarTerm#DEFINITION_DESCRIPTION_TYPE}, or an empty latest version, if none are of type definition in this or any
     * nested language coordinates
     *
     * @param descriptionList
     * @return
     */
    default Latest<SemanticEntityVersion> getDefinitionDescription(ImmutableList<SemanticEntity> descriptionList) {
        return getSpecifiedDescription(descriptionList, IntIds.list.of(TinkarTerm.DEFINITION_DESCRIPTION_TYPE.nid()));
    }

    default Optional<String> getDefinitionDescriptionText(EntityFacade entityFacade) {
        return getDefinitionDescriptionText(entityFacade.nid());
    }

    default Optional<String> getDefinitionDescriptionText(int entityNid) {
        return getDescriptionTextForComponentOfType(entityNid, TinkarTerm.DEFINITION_DESCRIPTION_TYPE.nid());
    }

    default Latest<SemanticEntityVersion> getRegularDescription(EntityFacade entity) {
        return getRegularDescription(entity.nid());
    }

    /**
     * Gets the latestDescription of type {@link TinkarTerm#REGULAR_NAME_DESCRIPTION_TYPE}.  Will return empty, if
     * no matching description type is found in this or any nested language coordinates
     *
     * @param entityNid the conceptId to get the fully specified latestDescription for
     * @return the regular name latestDescription
     */
    default Latest<SemanticEntityVersion> getRegularDescription(int entityNid) {
        return getSpecifiedDescription(getDescriptionsForComponent(entityNid), IntIds.list.of(TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE.nid()));
    }

    /**
     * Gets the latestDescription of type {@link TinkarTerm#FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE}.  Will return empty, if
     * no matching description type is found in this or any nested language coordinates
     *
     * @param descriptionList the latestDescription list
     * @return the regular name latestDescription, if available
     */
    default Latest<SemanticEntityVersion> getFullyQualifiedDescription(ImmutableList<SemanticEntity> descriptionList) {
        return getSpecifiedDescription(descriptionList, IntIds.list.of(TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid()));
    }

    default Latest<SemanticEntityVersion> getFullyQualifiedDescription(EntityFacade entityFacade) {
        return getFullyQualifiedDescription(entityFacade.nid());
    }

    /**
     * Gets the latestDescription of type {@link TinkarTerm#FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE}.  Will return empty, if
     * no matching description type is found in this or any nested language coordinates
     *
     * @param conceptId the conceptId to get the fully specified latestDescription for
     * @return the fully specified latestDescription
     */
    default Latest<SemanticEntityVersion> getFullyQualifiedDescription(int conceptId) {
        return getSpecifiedDescription(getDescriptionsForComponent(conceptId), IntIds.list.of(TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid()));
    }

    /**
     * Call getRegularName or getFullyQualifiedName for better quality names before calling this method.
     *
     * @param componentNid
     * @param stampCoordinate
     * @return
     */
    default String getAnyName(int componentNid, StampCoordinate stampCoordinate) {
        throw new UnsupportedOperationException();
//       switch (Get.identifierService().getObjectTypeForComponent(componentNid)) {
//           case CONCEPT: {
//               List<SemanticEntity> descriptions = Get.conceptService().getConceptDescriptions(componentNid);
//               if (descriptions.isEmpty()) {
//                   return "No descriptions for: " + Get.identifierService().getUuidPrimordialForNid(componentNid);
//               }
//               SemanticEntityVersion descriptionVersion = (SemanticEntityVersion) descriptions.get(0).getVersionList().get(0);
//               return descriptionVersion.getText();
//           }
//           case SEMANTIC: {
//               SemanticEntity sc = Get.assemblageService().getSemanticEntity(componentNid);
//               if (sc.getVersionType() == VersionType.DESCRIPTION) {
//                   LatestVersion<SemanticEntityVersion> latestDescription = sc.getLatestVersion(stampCoordinateRecord);
//                   if (latestDescription.isPresent()) {
//                       return latestDescription.get().getText();
//                   }
//                   return ((SemanticEntityVersion) sc.getVersionList().get(0)).getText();
//               }
//               return Get.assemblageService().getSemanticEntity(componentNid).toString();
//           }
//           case UNKNOWN:
//           default:
//               return "No name for: " + Get.identifierService().getUuidPrimordialForNid(componentNid);
//       }
    }

    default OptionalInt getAcceptabilityNid(int descriptionNid, int dialectAssemblageNid, StampCoordinate stampCoordinate) {
        throw new UnsupportedOperationException();
//       ImmutableIntSet acceptabilityChronologyNids = Get.assemblageService().getSemanticNidsForComponentFromAssemblage(descriptionNid, dialectAssemblageNid);
//
//       for (int acceptabilityChronologyNid: acceptabilityChronologyNids.toArray()) {
//           SemanticEntity acceptabilityChronology = Get.assemblageService().getSemanticEntity(acceptabilityChronologyNid);
//               LatestVersion<ComponentNidVersion> latestAcceptability = acceptabilityChronology.getLatestVersion(stampCoordinateRecord);
//               if (latestAcceptability.isPresent()) {
//                   return OptionalInt.of(latestAcceptability.get().getComponentNid());
//               }
//       }
//       return OptionalInt.empty();
    }

}
