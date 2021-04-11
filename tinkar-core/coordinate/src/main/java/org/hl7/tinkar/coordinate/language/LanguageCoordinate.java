/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 *
 * You may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributions from 2013-2017 where performed either by US government
 * employees, or under US Veterans Health Administration contracts.
 *
 * US Veterans Health Administration contributions by government employees
 * are work of the U.S. Government and are not subject to copyright
 * protection in the United States. Portions contributed by government
 * employees are USGovWork (17USC §105). Not subject to copyright.
 *
 * Contribution by contractors to the US Veterans Health Administration
 * during this period are contractually contributed under the
 * Apache License, Version 2.0.
 *
 * See: https://www.usa.gov/government-works
 *
 * Contributions prior to 2013:
 *
 * Copyright (C) International Health Terminology Standards Development Organisation.
 * Licensed under the Apache License, Version 2.0.
 *
 */
package org.hl7.tinkar.coordinate.language;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.coordinate.stamp.StampCalculator;
import org.hl7.tinkar.coordinate.stamp.StampFilter;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.entity.calculator.Latest;
import org.hl7.tinkar.terms.ConceptFacade;
import org.hl7.tinkar.terms.EntityFacade;
import org.hl7.tinkar.terms.PatternFacade;
import org.hl7.tinkar.terms.TinkarTerm;

/**
 * ImmutableCoordinate to manage the retrieval and display of language and dialect information.
 * <p>
 * Created by kec on 2/16/15.
 * TODO move determination of descriptions to language calculator...
 */
public interface LanguageCoordinate {

    LanguageCoordinateImmutable toLanguageCoordinateImmutable();

    default ImmutableList<SemanticEntity> getDescriptionsForComponent(EntityFacade entityFacade) {
        return getDescriptionsForComponent(entityFacade.nid());
    }

    ImmutableList<SemanticEntity> getDescriptionsForComponent(int componentNid);


    default ImmutableList<SemanticEntityVersion> getDescriptionsForComponentOfType(EntityFacade entityFacade,
                                                                                   ConceptFacade descriptionTypeFacade,
                                                                                   StampFilter stampFilter) {
        return getDescriptionsForComponentOfType(entityFacade.nid(), descriptionTypeFacade.nid(), stampFilter);
    }

    ImmutableList<SemanticEntityVersion> getDescriptionsForComponentOfType(int componentNid,
                                                                           int descriptionTypeNid,
                                                                           StampFilter stampFilter);


    /**
     * @return a content based uuid, such that identical language coordinates
     * will have identical uuids, and that different language coordinates will
     * always have different uuids.
     */
    default UUID getLanguageCoordinateUuid() {
        ArrayList<UUID> uuidList = new ArrayList<>();
        Entity.provider().addSortedUuids(uuidList, getDescriptionPatternList());
        Entity.provider().addSortedUuids(uuidList, getDescriptionTypePreferenceList());
        Entity.provider().addSortedUuids(uuidList, getDialectPatternPreferenceList());
        Entity.provider().addSortedUuids(uuidList, getLanguageConceptNid());
        Entity.provider().addSortedUuids(uuidList, getModulePreferenceListForLanguage());
        return UUID.nameUUIDFromBytes(uuidList.toString().getBytes());
    }

    /**
     * Return the latestDescription according to the pattern, type and dialect preferences of this {@code LanguageCoordinate}.
     *
     * @param descriptionList descriptions to consider
     * @param stampFilter     the stamp coordinate
     * @return an optional latestDescription best matching the {@code LanguageCoordinate} constraints.
     * @see getSpecifiedDescription( StampFilter , List, LanguageCoordinate)
     */
    default Latest<SemanticEntityVersion> getDescription(ImmutableList<SemanticEntity> descriptionList, StampFilter stampFilter) {
        return getSpecifiedDescription(stampFilter, descriptionList);
    }

    /**
     * @param Concept
     * @param stampFilter
     * @return Return the latestDescription according to the type and dialect preferences of this {@code LanguageCoordinate}.
     * @see #getDescriptionText(int, StampFilter)
     */
    default Optional<String> getDescriptionText(ConceptFacade Concept, StampFilter stampFilter) {
        return this.getDescriptionText(Concept.nid(), stampFilter);
    }

    /**
     * @param componentNid
     * @param stampFilter
     * @return Return the latestDescription according to the type and dialect preferences of this {@code LanguageCoordinate}.
     * @see LanguageCoordinateImmutable#getDescriptionText(int, StampFilter)
     */
    default Optional<String> getDescriptionText(int componentNid, StampFilter stampFilter) {
        return toLanguageCoordinateImmutable().getDescriptionText(componentNid, stampFilter);
    }
    default String getDescriptionTextOrNid(int componentNid, StampFilter stampFilter) {
        Optional<String> text = toLanguageCoordinateImmutable().getDescriptionText(componentNid, stampFilter);
        if (text.isPresent()) {
            return text.get();
        }
        return Integer.toString(componentNid);
    }

    /**
     * Return the latestDescription according to the type and dialect preferences of this {@code LanguageCoordinate}.
     * or a nested {@code LanguageCoordinate}
     *
     * @param entityNid   the concept nid.
     * @param stampFilter the stamp coordinate
     * @return an optional latestDescription best matching the {@code LanguageCoordinate} constraints.
     * @see #getDescription(List, StampFilter)
     */
    default Latest<SemanticEntityVersion> getDescription(int entityNid, StampFilter stampFilter) {
        return getDescription(getDescriptionsForComponent(entityNid), stampFilter);
    }

    default OptionalInt getAcceptabilityNid(int descriptionNid, int dialectAssemblageNid, StampFilter stampFilter) {
        throw new UnsupportedOperationException();
//       ImmutableIntSet acceptabilityChronologyNids = Get.assemblageService().getSemanticNidsForComponentFromAssemblage(descriptionNid, dialectAssemblageNid);
//
//       for (int acceptabilityChronologyNid: acceptabilityChronologyNids.toArray()) {
//           SemanticEntity acceptabilityChronology = Get.assemblageService().getSemanticEntity(acceptabilityChronologyNid);
//               LatestVersion<ComponentNidVersion> latestAcceptability = acceptabilityChronology.getLatestVersion(stampFilter);
//               if (latestAcceptability.isPresent()) {
//                   return OptionalInt.of(latestAcceptability.get().getComponentNid());
//               }
//       }
//       return OptionalInt.empty();
    }

    /**
     * Gets the description patterns used by this language coordinate.
     *
     * @return the description pattern list
     */
    int[] getDescriptionPatternList();

    PatternFacade[] getDescriptionPatternFacadePreferenceList();

    /**
     * Gets the description type preference list.
     *
     * @return the description type preference list
     */
    int[] getDescriptionTypePreferenceList();

    ConceptFacade[] getDescriptionTypeFacadePreferenceList();

    /**
     * Gets the dialect assemblage preference list.
     *
     * @return the dialect pattern preference list
     */
    int[] getDialectPatternPreferenceList();

    PatternFacade[] getDialectPatternFacadePreferenceList();

    /**
     * Gets the module preference list. Used to adjudicate which component to
     * return when more than one component is available. For example, if two modules
     * have different preferred names for the component, which one do you prefer to return?
     *
     * @return the module preference list.  If this list is null or empty, the returned preferred
     * name in the multiple case is unspecified.
     */
    int[] getModulePreferenceListForLanguage();

    /**
     * @return
     * @see #getModulePreferenceListForLanguage()
     */
    org.hl7.tinkar.terms.ConceptFacade[] getModuleFacadePreferenceListForLanguage();


    /**
     * Gets the latestDescription of type {@link TinkarTerm#FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE}.  Will return empty, if
     * no matching description type is found in this or any nested language coordinates
     *
     * @param descriptionList the latestDescription list
     * @param stampFilter     the stamp coordinate
     * @return the regular name latestDescription, if available
     */
    default Latest<SemanticEntityVersion> getFullyQualifiedDescription(ImmutableList<SemanticEntity> descriptionList, StampFilter stampFilter) {
        return getSpecifiedDescription(stampFilter, descriptionList, new int[]{TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid()});
    }

    /**
     * Gets the latestDescription of type {@link TinkarTerm#FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE}.  Will return empty, if
     * no matching description type is found in this or any nested language coordinates
     *
     * @param conceptId   the conceptId to get the fully specified latestDescription for
     * @param stampFilter the stamp coordinate
     * @return the fully specified latestDescription
     */
    default Latest<SemanticEntityVersion> getFullyQualifiedDescription(
            int conceptId,
            StampFilter stampFilter) {
        throw new UnsupportedOperationException();
        //return getFullyQualifiedDescription(Get.conceptService().getConceptDescriptions(conceptId), stampFilter);
    }

    /**
     * Gets the language concept nid.
     *
     * @return the language concept nid
     */
    int getLanguageConceptNid();

    /**
     * @return
     * @see #getLanguageConceptNid()
     */
    org.hl7.tinkar.terms.ConceptFacade getLanguageConcept();

    /**
     * Gets the latestDescription of type {@link TinkarTerm#REGULAR_NAME_DESCRIPTION_TYPE}, according to dialect preferences.
     * Will return empty, if no matching description type is found in this or any nested language coordinates
     *
     * @param descriptionList the latestDescription list
     * @param stampFilter     the stamp coordinate
     * @return the regular name latestDescription, if available
     */
    default Latest<SemanticEntityVersion> getRegularDescription(ImmutableList<SemanticEntity> descriptionList, StampFilter stampFilter) {
        return getSpecifiedDescription(stampFilter, descriptionList, new int[]{TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE.nid()});
    }

    /**
     * Return a description of type {@link TinkarTerm#DEFINITION_DESCRIPTION_TYPE}, or an empty latest version, if none are of type definition in this or any
     * nested language coordinates
     *
     * @param descriptionList
     * @param stampFilter
     * @return
     */
    default Latest<SemanticEntityVersion> getDefinitionDescription(ImmutableList<SemanticEntity> descriptionList, StampFilter stampFilter) {
        return getSpecifiedDescription(stampFilter, descriptionList, new int[]{TinkarTerm.DEFINITION_DESCRIPTION_TYPE.nid()});
    }

    default Optional<String> getDefinitionDescriptionText(EntityFacade entityFacade, StampFilter stampFilter) {
        return getDefinitionDescriptionText(entityFacade.nid(), stampFilter);
    }
    Optional<String> getDefinitionDescriptionText(int entityNid, StampFilter stampFilter);


    /**
     * Gets the latestDescription of type {@link TinkarTerm#REGULAR_NAME_DESCRIPTION_TYPE}.  Will return empty, if
     * no matching description type is found in this or any nested language coordinates
     *
     * @param entityNid  the conceptId to get the fully specified latestDescription for
     * @param stampFilter the stamp coordinate
     * @return the regular name latestDescription
     */
    default Latest<SemanticEntityVersion> getRegularDescription(
            int entityNid,
            StampFilter stampFilter) {
           return getRegularDescription(getDescriptionsForComponent(entityNid), stampFilter);
    }

    default Optional<String> getRegularDescriptionText(EntityFacade entityFacade, StampFilter stampFilter) {
        return getRegularDescriptionText(entityFacade.nid(), stampFilter);
    }

    /**
     * Gets the text of type {@link TinkarTerm#REGULAR_NAME_DESCRIPTION_TYPE}, and preferred according to the provided dialects.
     * Will return empty, if no matching description type is found in this or any nested language coordinates
     *
     * @param entityNid the entityNid to get a regular name for.
     * @param stampFilter  the stamp coordinate
     * @return the regular name text
     */
    Optional<String> getRegularDescriptionText(int entityNid, StampFilter stampFilter);

    /**
     * Call getRegularName or getFullyQualifiedName for better quality names before calling this method.
     *
     * @param componentNid
     * @param stampFilter
     * @return
     */
    default String getAnyName(int componentNid, StampFilter stampFilter) {
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
//                   LatestVersion<SemanticEntityVersion> latestDescription = sc.getLatestVersion(stampFilter);
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

    /**
     * Gets the text of type {@link TinkarTerm#FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE}.  Will return empty, if
     * no matching description type is found in this or any nested language coordinates
     *
     * @param componentNid    the componentNid to get a regular name for.
     * @param stampCoordinate the stamp coordinate
     * @return the regular name text
     */
    default Optional<String> getFullyQualifiedNameText(int componentNid, StampFilter stampCoordinate) {
        throw new UnsupportedOperationException();
//       switch (Get.identifierService().getObjectTypeForComponent(componentNid)) {
//         case CONCEPT:
//            LatestVersion<SemanticEntityVersion> latestDescription
//               = getFullyQualifiedDescription(Get.conceptService().getConceptDescriptions(componentNid), stampCoordinate);
//            return latestDescription.isPresent() ? Optional.of(latestDescription.get().getText()) : Optional.empty();
//         case SEMANTIC:
//            return Optional.of(Get.assemblageService().getSemanticEntity(componentNid).getVersionType().toString());
//         case UNKNOWN:
//         default:
//           return Optional.empty();
//      }
    }

    default String toUserString() {
        return "   language: " + PrimitiveData.text(this.getLanguageConceptNid())
                + ",\n   dialect preference: " + PrimitiveData.textList(this.getDialectPatternPreferenceList())
                + ",\n   type preference: " + PrimitiveData.textList(this.getDescriptionTypePreferenceList())
                + ",\n   module preference: " + PrimitiveData.textList(this.getModulePreferenceListForLanguage());

    }


    /**
     * TODO needs update. Add info on patterns, and we don't use getNextPriorityLanguageCoordinate anymore...
     * The developer can pass an ordered list of language coordinates to the language calculator.
     * <p>
     * Gets the specified description(s).
     * <p>
     * Iterates over the list of supplied descriptions, finding the descriptions that match the highest ranked
     * {@link LanguageCoordinate#getDescriptionTypePreferenceList()} (first item in the array) and the
     * {@link LanguageCoordinate#getLanguageConceptNid()}.  If no descriptions match, the process is repeated
     * with each subsequent item in {@link LanguageCoordinate#getDescriptionTypePreferenceList()}, walking
     * through the array one by one.
     * <p>
     * For any given step, if multiple descriptions match the criteria, an ACTIVE description should have priority over
     * an inactive one.
     * <p>
     * To be returned, a description MUST match one of the description types, and the specified language.
     * <p>
     * If the specified language {@link LanguageCoordinate#getLanguageConceptNid()} is {@link TermAux#LANGUAGE},
     * then language will be considered to always match, ignoring the actual value of the language in the description.
     * This allows this method to be used with a fallback behavior - where it will match a description of any language,
     * but still rank by the requested type.
     * <p>
     * - If no descriptions match this criteria, then
     * - if a {@link LanguageCoordinate#getNextPriorityLanguageCoordinate()} is supplied, the method is re-evaluated with the next coordinate.
     * - if there is no next priority coordinate, then an empty {@link Latest} is returned.
     * <p>
     * For any descriptions that matched the criteria, they are then compared with the requested
     * {@link LanguageCoordinate#getDialectPatternPreferenceList()}
     * The dialect preferences are evaluated in array order.  Each description that has a dialect annotation that matches
     * the dialect preference, with a type of {@link TermAux#PREFERRED}, it is advanced to the next ranking step (below)
     * <p>
     * If none of the descriptions has a dialect annotation of type {@link TermAux#PREFERRED} that matches a dialect
     * in the {@link LanguageCoordinate#getDialectPatternPreferenceList()}, then all matching language / type matching
     * descriptions are advanced to the next ranking step (below).
     * <p>
     * The final ranking step, is to evaluate {@link LanguageCoordinate#getModulePreferenceListForLanguage()}
     * The module list is evaluated in order.  If a description matches the requested module, then it is placed
     * into the top position, so it is returned via {@link Latest#get()}.  All other descriptions are still
     * returned, but as part of the {@link Latest#contradictions()}.
     * <p>
     * If none of the description match a specified module ranking, then the descriptions are returned in an arbitrary order,
     * between {@link Latest#get()} and {@link Latest#contradictions()}.
     *
     * @param stampFilter        used to determine which versions of descriptions and dialect annotations are current.
     * @param descriptionList    List of descriptions to consider.
     * @param languageCoordinate Used to determine ranking of candidate matches.
     * @return the specified description
     */
    default Latest<SemanticEntityVersion> getSpecifiedDescription(StampFilter stampFilter,
                                                                  ImmutableList<SemanticEntity> descriptionList) {
        return getSpecifiedDescription(stampFilter, descriptionList, getDescriptionTypePreferenceList());

    }


    /**
     * Same as getSpecifiedDescription(StampFilter stampFilter,
     * List<SemanticChronology> descriptionList,
     * LanguageCoordinate languageCoordinate);
     * but allows the descriptionTypePriority to be independent of the coordinate, without forcing a clone of
     * the coordinate.
     *
     * @param stampFilter
     * @param descriptionList
     * @param descriptionTypePriority
     * @return
     */

    default Latest<SemanticEntityVersion> getSpecifiedDescription(StampFilter stampFilter,
                                                                  ImmutableList<SemanticEntity> descriptionList,
                                                                  int[] descriptionTypePriority) {
        StampCalculator calculator = stampFilter.stampCalculator();
        final List<SemanticEntityVersion> descriptionsForLanguageOfType = new ArrayList<>();
        //Find all descriptions that match the language and description type - moving through the desired description types until
        //we find at least one.
        for (final int descType : descriptionTypePriority) {
            for (SemanticEntity descriptionChronicle : descriptionList) {
                final Latest<SemanticEntityVersion> latestDescription = calculator.latest(descriptionChronicle);

                if (latestDescription.isPresent()) {
                    for (SemanticEntityVersion descriptionVersion : latestDescription.versionList()) {
                        PatternEntity patternEntity = descriptionVersion.pattern();
                        PatternEntityVersion patternEntityVersion = calculator.latest(patternEntity).get();
                        int languageIndex = patternEntityVersion.indexForMeaning(TinkarTerm.LANGUAGE_CONCEPT_NID_FOR_DESCRIPTION);
                        Object languageObject = descriptionVersion.fields().get(languageIndex);
                        int descriptionTypeIndex = patternEntityVersion.indexForMeaning(TinkarTerm.DESCRIPTION_TYPE);
                        Object descriptionTypeObject = descriptionVersion.fields().get(descriptionTypeIndex);
                        if (languageObject instanceof EntityFacade languageFacade &&
                                descriptionTypeObject instanceof EntityFacade descriptionTypeFacade) {
                            if ((languageFacade.nid() == getLanguageConceptNid() ||
                                    getLanguageConceptNid() == TinkarTerm.LANGUAGE.nid()) // any language
                                    && descriptionTypeFacade.nid() == descType) {
                                descriptionsForLanguageOfType.add(descriptionVersion);
                            }
                        } else {
                            throw new IllegalStateException("Language object not instanceof EntityFacade: " + languageObject +
                                    " or Description type object not instance of EntityFacade: " + descriptionTypeObject);
                        }
                    }
                }
            }
            if (!descriptionsForLanguageOfType.isEmpty()) {
                //If we found at least one that matches the language and type, go on to rank by dialect
                break;
            }
        }

        if (descriptionsForLanguageOfType.isEmpty()) {
            //Didn't find any that matched any of the allowed description types.
            return new Latest<>();
        }

        // handle dialect...
        final Latest<SemanticEntityVersion> preferredForDialect = new Latest<>(SemanticEntityVersion.class);

       if (getDialectPatternPreferenceList() != null) {
            for (int dialectPatternNid : getDialectPatternPreferenceList()) {
                if (preferredForDialect.isAbsent()) {
                    calculator.latest(dialectPatternNid).ifPresent(versionObject -> {
                        if (versionObject instanceof PatternEntityVersion patternEntityVersion) {
                            int acceptabilityIndex = patternEntityVersion.indexForPurpose(TinkarTerm.DESCRIPTION_ACCEPTABILITY);
                            for (SemanticEntityVersion description : descriptionsForLanguageOfType) {
                                calculator.forEachSemanticVersionForComponentOfPattern(description.nid(), dialectPatternNid,
                                        (semanticEntityVersion, entityVersion, patternVersion) -> {
                                    if (semanticEntityVersion.fields().get(acceptabilityIndex) instanceof EntityFacade accceptabilityFacade) {
                                        if (accceptabilityFacade.nid() == TinkarTerm.PREFERRED.nid()) {
                                            preferredForDialect.addLatest(description);
                                        }
                                    }
                                });
                            }
                        }
                    });
                }
            }
        }

        //If none matched the dialect rank list, just ignore the dialect, and keep all that matched the type and language.
        if (!preferredForDialect.isPresent()) {
            descriptionsForLanguageOfType.forEach((description) -> {
                preferredForDialect.addLatest(description);
            });
        }

        // add in module preferences if there is more than one.
        if (getModulePreferenceListForLanguage() != null && getModulePreferenceListForLanguage().length != 0) {
            for (int preference : getModulePreferenceListForLanguage()) {
                for (SemanticEntityVersion descriptionVersion : preferredForDialect.versionList()) {
                    if (descriptionVersion.stamp().moduleNid() == preference) {
                        Latest<SemanticEntityVersion> preferredForModule = new Latest<>(descriptionVersion);
                        for (SemanticEntityVersion alternateVersion : preferredForDialect.versionList()) {
                            if (alternateVersion != preferredForModule.get()) {
                                preferredForModule.addLatest(alternateVersion);
                            }
                        }
                        preferredForModule.sortByState();
                        return preferredForModule;
                    }
                }
            }
        }
        preferredForDialect.sortByState();
        return preferredForDialect;
    }
}
