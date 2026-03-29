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
package dev.ikm.tinkar.integration.snomed.language;

import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.SemanticVersionRecord;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.integration.NewEphemeralKeyValueProvider;
import org.eclipse.collections.api.list.ImmutableList;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.UUID;

import static dev.ikm.tinkar.integration.snomed.core.TinkarStarterConceptUtil.ACTIVE;
import static dev.ikm.tinkar.integration.snomed.core.TinkarStarterConceptUtil.DEVELOPMENT_PATH;
import static dev.ikm.tinkar.integration.snomed.core.TinkarStarterConceptUtil.INACTIVE;
import static dev.ikm.tinkar.integration.snomed.core.TinkarStarterConceptUtil.SNOMED_CT_AUTHOR;
import static dev.ikm.tinkar.integration.snomed.core.TinkarStarterConceptUtil.SNOMED_TEXT_MODULE_ID;
import static dev.ikm.tinkar.integration.snomed.core.TinkarStarterConceptUtil.loadSnomedFile;
import static dev.ikm.tinkar.integration.snomed.language.SnomedToLanguageTransform.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ExtendWith(NewEphemeralKeyValueProvider.class)
public class TestSnomedToLanguageTransform {

    @Test
    @Order(1)
    @DisplayName("Test for one row of inactive stamp data in Language File")
    public void testStampWithOneActiveRow() {
        List<String> row = loadSnomedFile(TestSnomedToLanguageTransform.class, "der2_cRefset_LanguageFull-en_US1000124_20220901_1.txt");
        StampRecord record = createStampChronology(row.get(0));
        assertEquals(1, record.versions().size(), "Has more than one row");
    }

    @Test
    @Order(2)
    @DisplayName("Test for Active stamp in Language File")
    public void testStampWithActiveTransformResult() {
        List<String> rows = loadSnomedFile(TestSnomedToLanguageTransform.class, "der2_cRefset_LanguageFull-en_US1000124_20220901_1.txt");

        StampRecord record = createStampChronology(rows.get(0));
        UUID testStampUUID = getStampUUID(rows.get(0));

        // Assert that stamp is Active
        assertEquals(EntityService.get().nidForUuids(ACTIVE), record.stateNid(), "State is not active");
        assertEquals(EntityService.get().nidForUuids(SNOMED_CT_AUTHOR), record.authorNid(), "Author couldn't be referenced");
        assertEquals(EntityService.get().nidForUuids(DEVELOPMENT_PATH), record.pathNid(), "Path could not be referenced");
        assertEquals(EntityService.get().nidForUuids(SNOMED_TEXT_MODULE_ID), record.moduleNid(), "Module could ot be referenced");
        assertEquals(EntityService.get().nidForUuids(testStampUUID), record.nid(), "Stamp UUID was not populated");
    }

    @Test
    @Order(3)
    @DisplayName("Test for one row of inactive stamp data in Language File")
    public void testStampWithOneInactiveRow() {
        List<String> row = loadSnomedFile(TestSnomedToLanguageTransform.class, "der2_cRefset_LanguageFull-en_US1000124_20220901_2.txt");
        StampRecord record = createStampChronology(row.get(0));
        assertEquals(1, record.versions().size(), "Has more than one row");
    }

    @Test
    @Order(4)
    @DisplayName("Test for Inactive stamp in Language File")
    public void testStampWithInactiveTransformResult() {
        List<String> rows = loadSnomedFile(TestSnomedToLanguageTransform.class, "der2_cRefset_LanguageFull-en_US1000124_20220901_2.txt");
        StampRecord record = createStampChronology(rows.get(0));
        UUID testStampUUID = getStampUUID(rows.get(0));

        // Assert that stamp is Active
        assertEquals(EntityService.get().nidForUuids(INACTIVE), record.stateNid(), "State is active");
        assertEquals(EntityService.get().nidForUuids(SNOMED_CT_AUTHOR), record.authorNid(), "Author couldn't be referenced");
        assertEquals(EntityService.get().nidForUuids(DEVELOPMENT_PATH), record.pathNid(), "Path could not be referenced");
        assertEquals(EntityService.get().nidForUuids(SNOMED_TEXT_MODULE_ID), record.moduleNid(), "Module could ot be referenced");
        assertEquals(EntityService.get().nidForUuids(testStampUUID), record.nid(), "Stamp " + testStampUUID + " UUID was not populated");
    }

    @Test
    @Order(5)
    @DisplayName("Test language for identifier semantic")
    public void testLanguageWithIdentifierSemantic() {
        List<String> rows = loadSnomedFile(TestSnomedToLanguageTransform.class, "der2_cRefset_LanguageFull-en_US1000124_20220901_3.txt");
        UUID identifierPatternUUID = getIdentifierPatternUUID();
        UUID identifierSemanticUUID = getIdentifierSemanticUUID(rows.get(0));
        UUID referenceComponentUUID = getIdentifierReferenceComponentUUID(rows.get(0));

        // testing values
        SemanticRecord testIdentifierSemanticRecord = createLanguageIdentifierSemantic(rows.get(0));

        assertTrue(rows.size() == 1, "File with no or more than one language row exist");
        assertEquals(EntityService.get().nidForUuids(identifierSemanticUUID),testIdentifierSemanticRecord.nid(),  "Identifier Semantic nids doesnt match");
        assertEquals(EntityService.get().nidForUuids(identifierPatternUUID),testIdentifierSemanticRecord.patternNid(),  "Identifier Semantic pattern nids doesnt match");
        assertEquals(EntityService.get().nidForUuids(referenceComponentUUID),testIdentifierSemanticRecord.referencedComponentNid(),  "Identifier Semantic referencecomponent nids doesnt match");
    }

    @Test
    @Order(6)
    @DisplayName("Test language for single identifier semantic version")
    public void testLanguageWithIdentifierSemanticVersion() {
        List<String> rows = loadSnomedFile(TestSnomedToLanguageTransform.class, "der2_cRefset_LanguageFull-en_US1000124_20220901_3.txt");
        UUID identifierPatternUUID = getIdentifierPatternUUID();
        UUID identifierSemanticUUID = getIdentifierSemanticUUID(rows.get(0));
        UUID referenceComponentUUID = getIdentifierReferenceComponentUUID(rows.get(0));

        Integer expectedStampRecordNid = createStampChronology(rows.get(0)).nid();

        // testing values
        SemanticRecord testIdentifierSemanticRecord = createLanguageIdentifierSemantic(rows.get(0));
        ImmutableList<SemanticVersionRecord> testSemanticVersionRecord = testIdentifierSemanticRecord.versions();

        assertTrue(testSemanticVersionRecord.size() == 1,  "No version or more than one version of identifier semantic pattern exist");
        assertEquals(expectedStampRecordNid,testSemanticVersionRecord.get(0).stampNid(),  "Doesnt point to same stamp entity");
        assertEquals(EntityService.get().nidForUuids(identifierPatternUUID),testSemanticVersionRecord.get(0).patternNid(),  "Do not reference expected parent pattern entity");
        assertEquals(EntityService.get().nidForUuids(identifierSemanticUUID),testSemanticVersionRecord.get(0).nid(),  "Do not reference expected semantic entity");
        assertEquals(EntityService.get().nidForUuids(referenceComponentUUID),testSemanticVersionRecord.get(0).referencedComponentNid(),  "Do not reference expected parent semantic entity");
    }

    @Test
    @Order(7)
    @DisplayName("Test language for language acceptability semantic")
    public void testLanguageWithLanguageAcceptabilitySemantic() {
        List<String> rows = loadSnomedFile(TestSnomedToLanguageTransform.class, "der2_cRefset_LanguageFull-en_US1000124_20220901_4.txt");
        UUID languageAcceptabilityPatternUUID = getLanguageAcceptabilityPatternUUID();
        UUID languageAcceptabilitySemanticUUID = getLanguageAcceptabilitySemanticUUID(rows.get(0));
        UUID referenceComponentUUID = getLanguageAcceptabilityReferenceComponentUUID(rows.get(0));

        // testing values
        SemanticRecord testLanguageAcceptabilitySemanticRecord = createLanguageAceeptabilitySemantic(rows.get(0));

        assertTrue(rows.size() == 1, "File with no or more than one language concept row exist");
        assertEquals(EntityService.get().nidForUuids(languageAcceptabilitySemanticUUID),testLanguageAcceptabilitySemanticRecord.nid(),  "Language Acceptability Semantic nids doesnt match");
        assertEquals(EntityService.get().nidForUuids(languageAcceptabilityPatternUUID),testLanguageAcceptabilitySemanticRecord.patternNid(),  "Language Acceptability Semantic pattern nids doesnt match");
        assertEquals(EntityService.get().nidForUuids(referenceComponentUUID),testLanguageAcceptabilitySemanticRecord.referencedComponentNid(),  "Language Acceptability Semantic referencecomponent nids doesnt match");
    }

    @Test
    @Order(8)
    @DisplayName("Test language for single language acceptability semantic version")
    public void testLanguageWithLanguageAcceptabilitySemanticVersion() {
        List<String> rows = loadSnomedFile(TestSnomedToLanguageTransform.class, "der2_cRefset_LanguageFull-en_US1000124_20220901_4.txt");
        UUID languageAcceptabilityPatternUUID = getLanguageAcceptabilityPatternUUID();
        UUID languageAcceptabilitySemanticUUID = getLanguageAcceptabilitySemanticUUID(rows.get(0));
        UUID languageAcceptabilityReferenceComponentUUID = getLanguageAcceptabilityReferenceComponentUUID(rows.get(0));

        Integer expectedStampRecordNid = createStampChronology(rows.get(0)).nid();

        // testing values
        SemanticRecord testLanguageAcceptabilitySemanticRecord = createLanguageAceeptabilitySemantic(rows.get(0));
        ImmutableList<SemanticVersionRecord> testSemanticVersionRecord = testLanguageAcceptabilitySemanticRecord.versions();

        assertTrue(testSemanticVersionRecord.size() == 1,  "No version or more than one version of identifier semantic pattern exist");
        assertEquals(expectedStampRecordNid,testSemanticVersionRecord.get(0).stampNid(),  "Doesnt point to same stamp entity");
        assertEquals(EntityService.get().nidForUuids(languageAcceptabilityPatternUUID),testSemanticVersionRecord.get(0).patternNid(),  "Do not reference expected parent pattern entity");
        assertEquals(EntityService.get().nidForUuids(languageAcceptabilitySemanticUUID),testSemanticVersionRecord.get(0).nid(),  "Do not reference expected semantic entity");
        assertEquals(EntityService.get().nidForUuids(languageAcceptabilityReferenceComponentUUID),testSemanticVersionRecord.get(0).referencedComponentNid(),  "Do not reference expected parent semantic entity");
    }

}
