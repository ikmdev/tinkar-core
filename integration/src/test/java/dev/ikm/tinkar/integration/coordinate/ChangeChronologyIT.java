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
package dev.ikm.tinkar.integration.coordinate;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.NonExistentValue;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.stamp.change.ChangeChronology;
import dev.ikm.tinkar.coordinate.stamp.change.FieldChangeRecord;
import dev.ikm.tinkar.coordinate.stamp.change.VersionChangeRecord;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.FieldDefinitionForEntity;
import dev.ikm.tinkar.entity.PatternEntityVersion;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.entity.transaction.StampedWriter;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityBinding;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import dev.ikm.tinkar.terms.State;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import static dev.ikm.tinkar.entity.transaction.StampedWriter.field;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A component's change chronology records each stamp field against the stamp version
 * pattern ({@link EntityBinding.Stamp.Version#pattern()}), whose first field is the stamp
 * itself: status, time, author, module and path are its fields 1 to 5, and each recorded
 * value is the stamp's own.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ChangeChronologyIT {

    private static final File DATASTORE_ROOT = TestConstants.createFilePathInTargetFromClassName.apply(ChangeChronologyIT.class);

    @BeforeAll
    void beforeAll() {
        FileUtil.recursiveDelete(DATASTORE_ROOT);
        TestHelper.startDataBase(DataStore.SPINED_ARRAY_STORE, DATASTORE_ROOT);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA);
    }

    @AfterAll
    void afterAll() {
        TestHelper.stopDatabase();
        FileUtil.recursiveDelete(DATASTORE_ROOT);
    }

    /**
     * Each stamp field index the binding names is the field the starter data's stamp
     * version pattern defines there, by the field's meaning.
     */
    @Test
    void theStampVersionBindingNamesThePatternsFields() {
        PatternEntityVersion pattern = (PatternEntityVersion) Calculators.Stamp.DevelopmentLatest()
                .latest(EntityBinding.Stamp.Version.pattern().nid()).get();
        Map<String, Integer> expected = Map.of(
                "3d821e64-a2ee-4414-8949-1bc92ef5d5b6", EntityBinding.Stamp.Version.stampFieldDefinitionIndex(),   // STAMP field
                "f2c79ebb-3095-44ea-831f-992aed48801f", EntityBinding.Stamp.Version.statusFieldDefinitionIndex(),  // Status field
                "15293325-c16b-4f2e-8109-5b22b3355bcd", EntityBinding.Stamp.Version.timeFieldDefinitionIndex(),    // Time field
                "a9210ad6-cc48-47df-86e5-2192d56704a6", EntityBinding.Stamp.Version.authorFieldDefinitionIndex(),  // Author field
                "e6359a86-a1df-4721-8a1a-1f1f075ec3d9", EntityBinding.Stamp.Version.moduleFieldDefinitionIndex(),  // Module field
                "6622a391-e2e6-45a0-97e1-c58cd0184092", EntityBinding.Stamp.Version.pathFieldDefinitionIndex());  // Path field
        assertEquals(expected.size(), pattern.fieldDefinitions().size(), "Fields of the stamp version pattern");
        expected.forEach((meaning, index) -> assertTrue(
                PrimitiveData.publicId(pattern.fieldDefinitions().get(index).meaningNid()).contains(UUID.fromString(meaning)),
                "Meaning of stamp version pattern field " + index));
    }

    @Test
    void stampFieldsAreRecordedAgainstTheStampVersionPattern() {
        long stampVersionPattern = EntityBinding.Stamp.Version.pattern().nid();
        ChangeChronology chronology = Calculators.Stamp.DevelopmentLatest().changeChronology(KernelConcept.ROOT_VERTEX.nid());
        assertFalse(chronology.changeRecords().isEmpty(), "No change records for the root concept");

        for (VersionChangeRecord versionChange : chronology.changeRecords()) {
            StampEntity<?> stamp = Entity.getStamp(versionChange.stampNid());
            TreeMap<Integer, Object> stampFields = new TreeMap<>();
            for (FieldChangeRecord change : versionChange.changes()) {
                if (change.currentValue().patternNid() == stampVersionPattern) {
                    stampFields.put(change.currentValue().indexInPattern(), change.currentValue().value());
                }
            }
            // The first version of a component (last in the chronology, which runs newest
            // first) records every stamp field that differs from the nonexistent stamp's;
            // later versions record only the fields that changed.
            if (versionChange == chronology.changeRecords().getLast()) {
                List<Integer> expected = new ArrayList<>();
                for (int i = 0; i < stamp.fieldValues().size(); i++) {
                    if (!Objects.deepEquals(stamp.fieldValues().get(i), StampRecord.nonExistentStamp().fieldValues().get(i))) {
                        expected.add(i + 1);
                    }
                }
                assertFalse(expected.isEmpty(), "The first stamp equals the nonexistent stamp: " + stamp);
                assertEquals(expected, List.copyOf(stampFields.keySet()),
                        "Stamp fields of " + stamp + " by index in the stamp version pattern");
            }
            stampFields.forEach((index, value) ->
                    assertEquals(stamp.fieldValues().get(index - 1), value,
                            "Stamp version pattern field " + index + " of " + stamp));
        }
    }

    /**
     * Each pattern version field index the binding names is the field the starter data's
     * pattern version pattern defines there, by the field's meaning.
     */
    @Test
    void thePatternVersionBindingNamesThePatternsFields() {
        PatternEntityVersion pattern = (PatternEntityVersion) Calculators.Stamp.DevelopmentLatest()
                .latest(EntityBinding.Pattern.Version.pattern().nid()).get();
        Map<String, Integer> expected = Map.of(
                "996d0023-a355-422f-a84d-16dda6ece1b0", EntityBinding.Pattern.Version.patternMeaningFieldDefinitionIndex(),     // Pattern meaning field
                "352c821b-7a11-454c-a127-48ad3206573d", EntityBinding.Pattern.Version.patternPurposeFieldDefinitionIndex(),     // Pattern purpose field
                "14171f07-e74f-409a-b555-06b478818f76", EntityBinding.Pattern.Version.fieldDefinitionListFieldDefinitionIndex()); // Field definition field
        expected.forEach((meaning, index) -> assertTrue(
                PrimitiveData.publicId(pattern.fieldDefinitions().get(index).meaningNid()).contains(UUID.fromString(meaning)),
                "Meaning of pattern version pattern field " + index));
    }

    /**
     * A pattern version records its own fields against the pattern version pattern: its first
     * version every field, a later one only the meaning, purpose and field definitions that differ.
     */
    @Test
    void aPatternVersionRecordsItsMeaningPurposeAndChangedFieldDefinitions() {
        PublicId patternId = PublicIds.newRandom();
        long patternNid;
        try (StampedWriter writer = open("pattern, first version", TIME, KernelConcept.USER)) {
            patternNid = writer.pattern(patternId, KernelConcept.ENGLISH_LANGUAGE, KernelConcept.DESCRIPTION_NOT_CASE_SENSITIVE,
                    field(KernelConcept.ENGLISH_LANGUAGE, KernelConcept.ENGLISH_LANGUAGE, KernelConcept.STRING));
            writer.commit();
        }
        try (StampedWriter writer = open("pattern, second version", TIME + 1, KernelConcept.USER)) {
            writer.pattern(patternId, KernelConcept.PREFERRED, KernelConcept.DESCRIPTION_NOT_CASE_SENSITIVE,
                    field(KernelConcept.ENGLISH_LANGUAGE, KernelConcept.PREFERRED, KernelConcept.STRING),
                    field(KernelConcept.PREFERRED, KernelConcept.PREFERRED, KernelConcept.STRING));
            writer.commit();
        }

        ChangeChronology chronology = Calculators.Stamp.DevelopmentLatest().changeChronology(patternNid);
        assertEquals(2, chronology.changeRecords().size(), "One change record for each pattern version");

        // The chronology runs newest first.
        Map<Integer, List<FieldChangeRecord>> second = patternVersionFields(chronology.changeRecords().getFirst());
        assertEquals(Set.of(MEANING, FIELD_DEFINITIONS), second.keySet(), "The second version changes meaning and field definitions, not purpose");
        FieldChangeRecord meaning = second.get(MEANING).getFirst();
        assertEquals(KernelConcept.ENGLISH_LANGUAGE.nid(), nid(meaning.priorValue().value()), "Prior meaning");
        assertEquals(KernelConcept.PREFERRED.nid(), nid(meaning.currentValue().value()), "Current meaning");

        List<FieldChangeRecord> definitions = second.get(FIELD_DEFINITIONS);
        assertEquals(2, definitions.size(), "The revised field definition and the added one");
        FieldDefinitionForEntity revisedPrior = (FieldDefinitionForEntity) definitions.get(0).priorValue().value();
        FieldDefinitionForEntity revised = (FieldDefinitionForEntity) definitions.get(0).currentValue().value();
        assertEquals(0, revised.indexInPattern(), "The revised field definition is the first");
        assertEquals(KernelConcept.ENGLISH_LANGUAGE.nid(), revisedPrior.purposeNid(), "Prior purpose of the first field");
        assertEquals(KernelConcept.PREFERRED.nid(), revised.purposeNid(), "Current purpose of the first field");
        assertInstanceOf(NonExistentValue.class, definitions.get(1).priorValue().value(), "The second field did not exist before");
        FieldDefinitionForEntity added = (FieldDefinitionForEntity) definitions.get(1).currentValue().value();
        assertEquals(1, added.indexInPattern(), "The added field definition is the second");
        assertEquals(KernelConcept.PREFERRED.nid(), added.meaningNid(), "Meaning of the added field");

        Map<Integer, List<FieldChangeRecord>> first = patternVersionFields(chronology.changeRecords().getLast());
        assertEquals(Set.of(MEANING, PURPOSE, FIELD_DEFINITIONS), first.keySet(), "The first version records every field");
        first.values().forEach(changes -> changes.forEach(change -> assertInstanceOf(NonExistentValue.class,
                change.priorValue().value(), "A first version's fields did not exist before")));
    }

    /**
     * A canceled version is on no route: the change chronology leaves it out. Its time,
     * Long.MIN_VALUE, would otherwise make it the root of its path, and the committed version
     * a change from it. A store reads a record without its canceled versions, so a canceled
     * version reaches the calculator in a record held from before the cancel, as an observer
     * or a review surface holds it: its version's stamp now reads canceled.
     */
    @Test
    void aCanceledVersionIsLeftOutOfTheChronology() {
        PublicId conceptId = PublicIds.newRandom();
        EntityProxy.Semantic semantic = EntityProxy.Semantic.make(PublicIds.newRandom());
        try (StampedWriter writer = open("first committed", TIME + 10, KernelConcept.USER)) {
            writer.concept(conceptId);
            writer.semantic(semantic, KernelPattern.COMMENT_PATTERN.publicId(), conceptId, "first");
            writer.commit();
        }
        StampEntity<?> canceledStamp;
        SemanticEntity<?> held;
        try (StampedWriter writer = openPending("canceled", KernelConcept.USER)) {
            writer.semantic(semantic, KernelPattern.COMMENT_PATTERN.publicId(), conceptId, "canceled");
            held = EntityHandle.get(semantic).expectSemantic();
            canceledStamp = writer.stamp();
            writer.cancel();
        }
        assertTrue(held.versions().anySatisfy(version -> ((EntityVersion) version).canceled()),
                "The held record's pending version is now canceled");

        ChangeChronology chronology = Calculators.Stamp.DevelopmentLatest().changeChronology(held);
        assertEquals(1, chronology.changeRecords().size(), "Only the committed version has a change record");
        VersionChangeRecord only = chronology.changeRecords().getOnly();
        assertNotEquals(canceledStamp.nid(), only.stampNid(), "The change record is not the canceled version's");
        FieldChangeRecord comment = commentField(only);
        assertInstanceOf(NonExistentValue.class, comment.priorValue().value(), "The first committed version is new");
        assertEquals("first", comment.currentValue().value());
    }

    /**
     * Two uncommitted versions on one path do not supersede each other: each is a change from
     * the committed version they both follow, and neither is lost.
     */
    @Test
    void twoUncommittedVersionsOnOnePathEachFollowTheCommittedVersion() {
        PublicId conceptId = PublicIds.newRandom();
        EntityProxy.Semantic semantic = EntityProxy.Semantic.make(PublicIds.newRandom());
        try (StampedWriter writer = open("committed", TIME + 20, KernelConcept.USER)) {
            writer.concept(conceptId);
            writer.semantic(semantic, KernelPattern.COMMENT_PATTERN.publicId(), conceptId, "committed");
            writer.commit();
        }
        try (StampedWriter first = openPending("pending, first author", KernelConcept.USER);
             StampedWriter second = openPending("pending, second author", KernelConcept.KOMET_USER)) {
            first.semantic(semantic, KernelPattern.COMMENT_PATTERN.publicId(), conceptId, "pending first");
            second.semantic(semantic, KernelPattern.COMMENT_PATTERN.publicId(), conceptId, "pending second");
            SemanticEntity<?> entity = EntityHandle.get(semantic).expectSemantic();
            assertEquals(2, entity.versions().count(version -> ((EntityVersion) version).uncommitted()),
                    "The semantic has two uncommitted versions");

            ChangeChronology chronology = Calculators.Stamp.DevelopmentLatest().changeChronology(entity.nid());
            assertEquals(3, chronology.changeRecords().size(), "The committed version and both uncommitted ones");
            Set<Object> pending = new HashSet<>();
            for (VersionChangeRecord change : chronology.changeRecords()) {
                if (Entity.getStamp(change.stampNid()).uncommitted()) {
                    FieldChangeRecord comment = commentField(change);
                    assertEquals("committed", comment.priorValue().value(), "An uncommitted version follows the committed one");
                    pending.add(comment.currentValue().value());
                }
            }
            assertEquals(Set.of("pending first", "pending second"), pending, "Both uncommitted versions are in the chronology");
        }
    }

    private static final long TIME = 1_760_000_000_000L;
    private static final int MEANING = EntityBinding.Pattern.Version.patternMeaningFieldDefinitionIndex();
    private static final int PURPOSE = EntityBinding.Pattern.Version.patternPurposeFieldDefinitionIndex();
    private static final int FIELD_DEFINITIONS = EntityBinding.Pattern.Version.fieldDefinitionListFieldDefinitionIndex();

    private static StampedWriter open(String name, long time, ConceptFacade author) {
        return StampedWriter.open(name, State.ACTIVE, time, author, KernelConcept.PRIMORDIAL_MODULE, KernelConcept.DEVELOPMENT_PATH);
    }

    /** A writer whose stamp stays uncommitted until it commits, and is canceled if it does not. */
    private static StampedWriter openPending(String name, ConceptFacade author) {
        return StampedWriter.open(name, State.ACTIVE, author, KernelConcept.PRIMORDIAL_MODULE, KernelConcept.DEVELOPMENT_PATH);
    }

    /** A version's changes to pattern version fields, by index in the pattern version pattern. */
    private static Map<Integer, List<FieldChangeRecord>> patternVersionFields(VersionChangeRecord versionChange) {
        long patternVersionPattern = EntityBinding.Pattern.Version.pattern().nid();
        Map<Integer, List<FieldChangeRecord>> fields = new TreeMap<>();
        for (FieldChangeRecord change : versionChange.changes()) {
            if (change.currentValue().patternNid() == patternVersionPattern) {
                fields.computeIfAbsent(change.currentValue().indexInPattern(), index -> new ArrayList<>()).add(change);
            }
        }
        return fields;
    }

    /** A version's change to the comment pattern's only field. */
    private static FieldChangeRecord commentField(VersionChangeRecord versionChange) {
        return versionChange.changes().detect(change -> change.currentValue().patternNid() == KernelPattern.COMMENT_PATTERN.nid());
    }

    private static long nid(Object value) {
        return ((EntityFacade) value).nid();
    }
}
