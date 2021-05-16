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

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.id.IntIdList;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.terms.ConceptFacade;
import org.hl7.tinkar.terms.ConceptProxy;
import org.hl7.tinkar.terms.PatternFacade;

import java.util.ArrayList;
import java.util.UUID;

/**
 * LanguageCoordinate to specify the retrieval and display of language and dialect information.
 * <p>
 * Created by kec on 2/16/15.
 */
public interface LanguageCoordinate {

    LanguageCoordinateRecord toLanguageCoordinateRecord();

    /**
     * @return a content based uuid, such that identical language coordinates
     * will have identical uuids, and that different language coordinates will
     * always have different uuids.
     */
    default UUID getLanguageCoordinateUuid() {
        ArrayList<UUID> uuidList = new ArrayList<>();
        Entity.provider().addSortedUuids(uuidList, descriptionPatternPreferenceNidList());
        Entity.provider().addSortedUuids(uuidList, descriptionTypePreferenceNidList());
        Entity.provider().addSortedUuids(uuidList, dialectPatternPreferenceNidList());
        Entity.provider().addSortedUuids(uuidList, languageConceptNid());
        Entity.provider().addSortedUuids(uuidList, modulePreferenceNidListForLanguage());
        return UUID.nameUUIDFromBytes(uuidList.toString().getBytes());
    }

    /**
     * Gets the description patterns used by this language coordinate.
     *
     * @return the description pattern nid array
     */
    IntIdList descriptionPatternPreferenceNidList();

    PatternFacade[] descriptionPatternPreferenceArray();

    /**
     * Gets the description type preference nid array.
     *
     * @return the description type preference nid array
     */
    IntIdList descriptionTypePreferenceNidList();
    ImmutableList<ConceptFacade> descriptionTypePreferenceList();

    /**
     * Gets the dialect assemblage preference nid array.
     *
     * @return the dialect pattern preference nid array
     */
    IntIdList dialectPatternPreferenceNidList();
    ImmutableList<PatternFacade> dialectPatternPreferenceList();

    /**
     * Gets the module preference nid array. Used to adjudicate which component to
     * return when more than one component is available. For example, if two modules
     * have different preferred names for the component, which one do you prefer to return?
     *
     * @return the module preference nid array.  If this array is empty, the returned preferred
     * name in the multiple case is unspecified.
     */
    IntIdList modulePreferenceNidListForLanguage();
    ImmutableList<ConceptFacade> modulePreferenceListForLanguage();


    /**
     * Gets the language concept nid.
     *
     * @return the language concept nid
     */
    int languageConceptNid();

    /**
     * @return
     * @see #languageConceptNid()
     */
    default ConceptFacade languageConcept() {
        return ConceptProxy.make(languageConceptNid());
    }

    default String toUserString() {
        return "   language: " + PrimitiveData.text(this.languageConceptNid())
                + ",\n   dialect preference: " + PrimitiveData.textList(this.dialectPatternPreferenceNidList())
                + ",\n   type preference: " + PrimitiveData.textList(this.descriptionTypePreferenceNidList())
                + ",\n   module preference: " + PrimitiveData.textList(this.modulePreferenceNidListForLanguage());
    }

}
