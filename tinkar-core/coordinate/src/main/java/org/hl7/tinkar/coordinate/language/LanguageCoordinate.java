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

import org.hl7.tinkar.coordinate.stamp.StampFilter;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.component.LatestVersion;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.terms.TinkarTerm;

/**
 * ImmutableCoordinate to manage the retrieval and display of language and dialect information.
 *
 * Created by kec on 2/16/15.
 */
public interface LanguageCoordinate {

   LanguageCoordinateImmutable toLanguageCoordinateImmutable();

    /**
     * 
     * @return a content based uuid, such that identical language coordinates
     * will have identical uuids, and that different language coordinates will 
     * always have different uuids.
     */
   default UUID getLanguageCoordinateUuid() {
       ArrayList<UUID> uuidList = new ArrayList<>();
       Entity.provider().addSortedUuids(uuidList, getDescriptionTypePreferenceList());
       Entity.provider().addSortedUuids(uuidList, getDialectAssemblagePreferenceList());
       Entity.provider().addSortedUuids(uuidList, getLanguageConceptNid());
       Entity.provider().addSortedUuids(uuidList, getModulePreferenceListForLanguage());
       return UUID.nameUUIDFromBytes(uuidList.toString().getBytes());
   }

   /**
    * Return the latestDescription according to the type and dialect preferences of this {@code LanguageCoordinate}.
    * @see getSpecifiedDescription( StampFilter , List, LanguageCoordinate)
    *
    * @param descriptionList descriptions to consider
    * @param stampFilter the stamp coordinate
    * @return an optional latestDescription best matching the {@code LanguageCoordinate} constraints.
    */
   default LatestVersion<SemanticEntityVersion> getDescription(List<SemanticEntity> descriptionList, StampFilter stampFilter) {
       return getSpecifiedDescription(stampFilter, descriptionList, this);
   }

   /**
    * @see #getDescriptionText(int, StampFilter)
    * 
    * @param Concept
    * @param stampFilter
    * @return Return the latestDescription according to the type and dialect preferences of this {@code LanguageCoordinate}.
    */
   default Optional<String> getDescriptionText(ConceptEntity Concept, StampFilter stampFilter) {
      return this.getDescriptionText(Concept.nid(), stampFilter);
   }

   /**
    * @see LanguageCoordinateImmutable#getDescriptionText(int, StampFilter)
    * 
    * @param componentNid
    * @param stampFilter
    * @return Return the latestDescription according to the type and dialect preferences of this {@code LanguageCoordinate}.
    */
   default Optional<String> getDescriptionText(int componentNid, StampFilter stampFilter) {
      return toLanguageCoordinateImmutable().getDescriptionText(componentNid, stampFilter);
   }
   
   /**
    * Return the latestDescription according to the type and dialect preferences of this {@code LanguageCoordinate}.
    * or a nested {@code LanguageCoordinate}
    * 
    * @see #getDescription(List, StampFilter)
    *
    * @param conceptNid the concept nid. 
    * @param stampFilter the stamp coordinate
    * @return an optional latestDescription best matching the {@code LanguageCoordinate} constraints.
    */
   default LatestVersion<SemanticEntityVersion> getDescription(int conceptNid, StampFilter stampFilter) {
        throw new UnsupportedOperationException();
//      return getDescription(Get.conceptService().getConceptDescriptions(conceptNid), stampFilter);
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
    * Gets the latestDescription type preference list.
    *
    * @return the latestDescription type preference list
    */
   int[] getDescriptionTypePreferenceList();
   Concept[] getDescriptionTypeSpecPreferenceList();

   /**
    * Gets the dialect assemblage preference list.
    *
    * @return the dialect assemblage preference list
    */
   int[] getDialectAssemblagePreferenceList();
   Concept[] getDialectAssemblageSpecPreferenceList();
   
   /**
    * Gets the module preference list. Used to adjudicate which component to 
    * return when more than one component is available. For example, if two modules
    * have different preferred names for the component, which one do you prefer to return?
    * @return the module preference list.  If this list is null or empty, the returned preferred
    * name in the multiple case is unspecified.
    */
   int[] getModulePreferenceListForLanguage();

   /**
    * @see #getModulePreferenceListForLanguage()
    * @return
    */
   Concept[] getModuleSpecPreferenceListForLanguage();
   

   /**
    * Gets the latestDescription of type {@link TinkarTerm#FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE}.  Will return empty, if
    * no matching description type is found in this or any nested language coordinates
    * 
    * @param descriptionList the latestDescription list
    * @param stampFilter the stamp coordinate
    * @return the regular name latestDescription, if available
    */
   default public LatestVersion<SemanticEntityVersion> getFullyQualifiedDescription(List<SemanticEntity> descriptionList, StampFilter stampFilter) {
       return getSpecifiedDescription(stampFilter, descriptionList, new int[]{TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid()}, this);
   }

   /**
    * Gets the latestDescription of type {@link TinkarTerm#FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE}.  Will return empty, if
    * no matching description type is found in this or any nested language coordinates
    *
    * @param conceptId the conceptId to get the fully specified latestDescription for
    * @param stampFilter the stamp coordinate
    * @return the fully specified latestDescription
    */
   default LatestVersion<SemanticEntityVersion> getFullyQualifiedDescription(
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
    * @see #getLanguageConceptNid()
    * @return 
    */
    Concept getLanguageConcept();

   /**
    * Gets the latestDescription of type {@link TinkarTerm#REGULAR_NAME_DESCRIPTION_TYPE}, according to dialect preferences.
    * Will return empty, if no matching description type is found in this or any nested language coordinates
    * 
    * @param descriptionList the latestDescription list
    * @param stampFilter the stamp coordinate
    * @return the regular name latestDescription, if available
    */
   default LatestVersion<SemanticEntityVersion> getRegularDescription(List<SemanticEntity> descriptionList, StampFilter stampFilter) {
      return getSpecifiedDescription(stampFilter, descriptionList, new int[]{TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE.nid()}, this);
   }

   /**
    * Return a description of type {@link TinkarTerm#DEFINITION_DESCRIPTION_TYPE}, or an empty latest version, if none are of type definition in this or any
    * nested language coordinates
    * @param descriptionList
    * @param stampFilter
    * @return
    */
   default LatestVersion<SemanticEntityVersion> getDefinitionDescription(List<SemanticEntity> descriptionList, StampFilter stampFilter) {
       return getSpecifiedDescription(stampFilter, descriptionList, new int[]{TinkarTerm.DEFINITION_DESCRIPTION_TYPE.nid()}, this);
   }

   /**
    * Gets the latestDescription of type {@link TinkarTerm#REGULAR_NAME_DESCRIPTION_TYPE}.  Will return empty, if
    * no matching description type is found in this or any nested language coordinates
    *
    * @param conceptNid the conceptId to get the fully specified latestDescription for
    * @param stampFilter the stamp coordinate
    * @return the regular name latestDescription
    */
   default LatestVersion<SemanticEntityVersion> getRegularDescription(
           int conceptNid,
           StampFilter stampFilter) {
       throw new UnsupportedOperationException();
//       Optional<? extends ConceptEntity> optionalConcept = Get.conceptService().getOptionalConcept(conceptNid);
//       if (optionalConcept.isPresent()) {
//           return getRegularDescription(optionalConcept.get().getConceptDescriptionList(), stampFilter);
//       }
//       return LatestVersion.empty();
   }

   /**
    * Gets the text of type {@link TinkarTerm#REGULAR_NAME_DESCRIPTION_TYPE}, and preferred according to the provided dialects.
    * Will return empty, if no matching description type is found in this or any nested language coordinates
    *
    * @param componentNid the componentNid to get a regular name for.
    * @param stampFilter the stamp coordinate
    * @return the regular name text
    */
   default Optional<String> getRegularDescriptionText(int componentNid, StampFilter stampFilter) {
       throw new UnsupportedOperationException();
//      switch (Get.identifierService().getObjectTypeForComponent(componentNid)) {
//         case CONCEPT: {
//            LatestVersion<SemanticEntityVersion> latestDescription
//               = getRegularDescription(Get.conceptService().getConceptDescriptions(componentNid), stampFilter);
//            return latestDescription.isPresent() ? Optional.of(latestDescription.get().getText()) : Optional.empty();
//         }
//         case SEMANTIC: {
//             SemanticEntity sc = Get.assemblageService().getSemanticEntity(componentNid);
//             if (sc.getVersionType() == VersionType.DESCRIPTION) {
//                LatestVersion<SemanticEntityVersion> latestDescription = sc.getLatestVersion(stampFilter);
//                if (latestDescription.isPresent()) {
//                    return Optional.of("desc: " + latestDescription.get().getText());
//                }
//                return Optional.of("inactive desc: " + ((SemanticEntityVersion) sc.getVersionList().get(0)).getText());
//             }
//            return Optional.of(Get.assemblageService().getSemanticEntity(componentNid).getVersionType().toString());
//         }
//         case UNKNOWN:
//         default:
//           return Optional.empty();
//      }
   }

    /**
     * Call getRegularName or getFullyQualifiedName for better quality names before calling this method.
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
    * @param componentNid the componentNid to get a regular name for.
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
           return "   language: " + DefaultDescriptionText.get(this.getLanguageConceptNid())
                   + ",\n   dialect preference: " + DefaultDescriptionText.getList(this.getDialectAssemblagePreferenceList())
                   + ",\n   type preference: " + DefaultDescriptionText.getList(this.getDescriptionTypePreferenceList())
                   + ",\n   module preference: " + DefaultDescriptionText.getList(this.getModulePreferenceListForLanguage());

   }



    /**
     * Gets the specified description(s).
     *
     * Iterates over the list of supplied descriptions, finding the descriptions that match the highest ranked
     * {@link LanguageCoordinate#getDescriptionTypePreferenceList()} (first item in the array) and the
     * {@link LanguageCoordinate#getLanguageConceptNid()}.  If no descriptions match, the process is repeated
     * with each subsequent item in {@link LanguageCoordinate#getDescriptionTypePreferenceList()}, walking
     * through the array one by one.
     *
     * For any given step, if multiple descriptions match the criteria, an ACTIVE description should have priority over
     * an inactive one.
     *
     * To be returned, a description MUST match one of the description types, and the specified language.
     *
     * If the specified language {@link LanguageCoordinate#getLanguageConceptNid()} is {@link TermAux#LANGUAGE},
     * then language will be considered to always match, ignoring the actual value of the language in the description.
     * This allows this method to be used with a fallback behavior - where it will match a description of any language,
     * but still rank by the requested type.
     *
     *  - If no descriptions match this criteria, then
     *    - if a {@link LanguageCoordinate#getNextPriorityLanguageCoordinate()} is supplied, the method is re-evaluated with the next coordinate.
     *    - if there is no next priority coordinate, then an empty {@link LatestVersion} is returned.
     *
     * For any descriptions that matched the criteria, they are then compared with the requested
     * {@link LanguageCoordinate#getDialectAssemblagePreferenceList()}
     * The dialect preferences are evaluated in array order.  Each description that has a dialect annotation that matches
     * the dialect preference, with a type of {@link TermAux#PREFERRED}, it is advanced to the next ranking step (below)
     *
     * If none of the descriptions has a dialect annotation of type {@link TermAux#PREFERRED} that matches a dialect
     * in the {@link LanguageCoordinate#getDialectAssemblagePreferenceList()}, then all matching language / type matching
     * descriptions are advanced to the next ranking step (below).
     *
     * The final ranking step, is to evaluate {@link LanguageCoordinate#getModulePreferenceListForLanguage()}
     * The module list is evaluated in order.  If a description matches the requested module, then it is placed
     * into the top position, so it is returned via {@link LatestVersion#get()}.  All other descriptions are still
     * returned, but as part of the {@link LatestVersion#contradictions()}.
     *
     * If none of the description match a specified module ranking, then the descriptions are returned in an arbitrary order,
     * between {@link LatestVersion#get()} and {@link LatestVersion#contradictions()}.
     *
     * @param stampFilter used to determine which versions of descriptions and dialect annotations are current.
     * @param descriptionList List of descriptions to consider.
     * @param languageCoordinate Used to determine ranking of candidate matches.
     * @return the specified description
     */
    default LatestVersion<SemanticEntityVersion> getSpecifiedDescription(StampFilter stampFilter,
                                                                     List<SemanticEntity> descriptionList,
                                                                     LanguageCoordinate languageCoordinate) {
        int[] descriptionTypes = languageCoordinate.getDescriptionTypePreferenceList();
        return getSpecifiedDescription(stampFilter, descriptionList, descriptionTypes, languageCoordinate);

    }


    /**
     * Same as getSpecifiedDescription(StampFilter stampFilter,
     *                                 List<SemanticChronology> descriptionList,
     *                                 LanguageCoordinate languageCoordinate);
     * but allows the descriptionTypePriority to be independent of the coordinate, without forcing a clone of
     * the coordinate.
     *
     * @param stampFilter
     * @param descriptionList
     * @param descriptionTypePriority
     * @param languageCoordinate
     * @return
     */

    default LatestVersion<SemanticEntityVersion> getSpecifiedDescription(StampFilter stampFilter,
                                                                     List<SemanticEntity> descriptionList,
                                                                     int[] descriptionTypePriority,
                                                                     LanguageCoordinate languageCoordinate) {
        throw new UnsupportedOperationException();
//        final List<SemanticEntityVersion> descriptionsForLanguageOfType = new ArrayList<>();
//        //Find all descriptions that match the language and description type - moving through the desired description types until
//        //we find at least one.
//        for (final int descType : descriptionTypePriority) {
//            for (SemanticEntity descriptionChronicle : descriptionList) {
//                final LatestVersion<SemanticEntityVersion> latestDescription = descriptionChronicle.getLatestVersion(stampFilter);
//
//                if (latestDescription.isPresent()) {
//                    for (SemanticEntityVersion descriptionVersion : latestDescription.versionList()) {
//                        if ((descriptionVersion.getLanguageConceptNid() == languageCoordinate.getLanguageConceptNid() ||
//                                languageCoordinate.getLanguageConceptNid() == TermAux.LANGUAGE.getNid())
//                                && descriptionVersion.getDescriptionTypeConceptNid() == descType) {
//                            descriptionsForLanguageOfType.add(descriptionVersion);
//                        }
//                    }
//                }
//            }
//            if (!descriptionsForLanguageOfType.isEmpty()) {
//                //If we found at least one that matches the language and type, go on to rank by dialect
//                break;
//            }
//        }
//
//        if (descriptionsForLanguageOfType.isEmpty()) {
//            //Didn't find any that matched any of the allowed description types.  See if there is another priority coordinate to continue with
//            Optional<? extends LanguageCoordinate> nextPriorityCoordinate = languageCoordinate.getNextPriorityLanguageCoordinate();
//            if (nextPriorityCoordinate.isPresent()) {
//                return getSpecifiedDescription(stampFilter, descriptionList, nextPriorityCoordinate.get());
//            }
//            else {
//                return new LatestVersion<>();
//            }
//        }
//
//        // handle dialect...
//        final LatestVersion<SemanticEntityVersion> preferredForDialect = new LatestVersion<>(SemanticEntityVersion.class);
//        final SemanticSnapshotService<ComponentNidVersion> acceptabilitySnapshot = Get.assemblageService().getSnapshot(ComponentNidVersion.class,
//                stampFilter);
//        if (languageCoordinate.getDialectAssemblagePreferenceList() != null) {
//            for (int dialectAssemblageNid : languageCoordinate.getDialectAssemblagePreferenceList()) {
//                if (!preferredForDialect.isPresent()) {
//                    for (SemanticEntityVersion description : descriptionsForLanguageOfType) {
//                        for (LatestVersion<ComponentNidVersion> acceptabilityVersion : acceptabilitySnapshot
//                                .getLatestSemanticVersionsForComponentFromAssemblage(description.getNid(), dialectAssemblageNid)) {
//                            acceptabilityVersion.ifPresent((acceptability) -> {
//                                if (acceptability.getComponentNid() == getPreferredConceptNid()) {
//                                    preferredForDialect.addLatest(description);
//                                }
//                            });
//                        }
//                    }
//                }
//            }
//        }
//
//        //If none matched the dialect rank list, just ignore the dialect, and keep all that matched the type and language.
//        if (!preferredForDialect.isPresent()) {
//            descriptionsForLanguageOfType.forEach((description) -> {
//                preferredForDialect.addLatest(description);
//            });
//        }
//
//        // add in module preferences if there is more than one.
//        if (languageCoordinate.getModulePreferenceListForLanguage() != null && languageCoordinate.getModulePreferenceListForLanguage().length != 0) {
//            for (int preference : languageCoordinate.getModulePreferenceListForLanguage()) {
//                for (SemanticEntityVersion descriptionVersion : preferredForDialect.versionList()) {
//                    if (descriptionVersion.getModuleNid() == preference) {
//                        LatestVersion<SemanticEntityVersion> preferredForModule = new LatestVersion<>(descriptionVersion);
//                        for (SemanticEntityVersion alternateVersion : preferredForDialect.versionList()) {
//                            if (alternateVersion != preferredForModule.get()) {
//                                preferredForModule.addLatest(alternateVersion);
//                            }
//                        }
//                        preferredForModule.sortByState();
//                        return preferredForModule;
//                    }
//                }
//            }
//        }
//        preferredForDialect.sortByState();
//        return preferredForDialect;
    }

}
