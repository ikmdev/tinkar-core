/*
 * Copyright 2017 Organizations participating in ISAAC, ISAAC's KOMET, and SOLOR development include the 
         US Veterans Health Administration, OSHERA, and the Health Services Platform Consortium..
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.hl7.tinkar.coordinate.language;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.entity.calculator.Latest;
import org.hl7.tinkar.coordinate.stamp.StampFilter;
import org.hl7.tinkar.entity.SemanticEntity;
import org.hl7.tinkar.entity.SemanticEntityVersion;
import org.hl7.tinkar.terms.ConceptFacade;
import org.hl7.tinkar.terms.PatternFacade;

/**
 *
 * @author kec
 */
public interface LanguageCoordinateProxy extends LanguageCoordinate {
   /**
    * Gets the language coordinate.
    *
    * @return a LanguageCoordinate that specifies how to manage the retrieval and display of language.
    * and dialect information.
    */
   LanguageCoordinate getLanguageCoordinate();

   @Override
   default LanguageCoordinateImmutable toLanguageCoordinateImmutable() {
      return getLanguageCoordinate().toLanguageCoordinateImmutable();
   }

   @Override
   default ConceptFacade[] getDescriptionTypeFacadePreferenceList() {
      return getLanguageCoordinate().getDescriptionTypeFacadePreferenceList();
   }

   @Override
   default PatternFacade[] getDialectPatternFacadePreferenceList() {
      return getLanguageCoordinate().getDialectPatternFacadePreferenceList();
   }

   @Override
   default int[] getModulePreferenceListForLanguage() {
      return getLanguageCoordinate().getModulePreferenceListForLanguage();
   }

   @Override
   default ConceptFacade[] getModuleFacadePreferenceListForLanguage() {
      return getLanguageCoordinate().getModuleFacadePreferenceListForLanguage();
   }

   @Override
   default ConceptFacade getLanguageConcept() {
      return getLanguageCoordinate().getLanguageConcept();
   }

   @Override
   default Latest<SemanticEntityVersion> getDefinitionDescription(ImmutableList<SemanticEntity> descriptionList, StampFilter stampFilter) {
      return getLanguageCoordinate().getDefinitionDescription(descriptionList, stampFilter);
   }

   @Override
   default Latest<SemanticEntityVersion> getDescription(ImmutableList<SemanticEntity> descriptionList, StampFilter stampFilter) {
      return getLanguageCoordinate().getDescription(descriptionList, stampFilter);
   }

   @Override
   default int[] getDescriptionTypePreferenceList() {
      return getLanguageCoordinate().getDescriptionTypePreferenceList();
   }

   @Override
   default int[] getDialectPatternPreferenceList() {
      return getLanguageCoordinate().getDialectPatternPreferenceList();
   }

   @Override
   default Latest<SemanticEntityVersion> getFullyQualifiedDescription(ImmutableList<SemanticEntity> descriptionList, StampFilter stampFilter) {
      return getLanguageCoordinate().getFullyQualifiedDescription(descriptionList, stampFilter);
   }

   @Override
   default int getLanguageConceptNid() {
      return getLanguageCoordinate().getLanguageConceptNid();
   }

   @Override
   default Latest<SemanticEntityVersion> getRegularDescription(ImmutableList<SemanticEntity> descriptionList, StampFilter stampFilter) {
      return getLanguageCoordinate().getRegularDescription(descriptionList, stampFilter);
   }
}
