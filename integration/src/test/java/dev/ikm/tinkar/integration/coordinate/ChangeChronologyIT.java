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

import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.io.FileUtil;
import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.stamp.change.ChangeChronology;
import dev.ikm.tinkar.coordinate.stamp.change.FieldChangeRecord;
import dev.ikm.tinkar.coordinate.stamp.change.VersionChangeRecord;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.PatternEntityVersion;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.EntityBinding;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
        expected.forEach((meaning, index) -> assertEquals(UUID.fromString(meaning),
                PrimitiveData.publicId(pattern.fieldDefinitions().get(index).meaningNid()).asUuidArray()[0],
                "Meaning of stamp version pattern field " + index));
    }

    @Test
    void stampFieldsAreRecordedAgainstTheStampVersionPattern() {
        int stampVersionPattern = EntityBinding.Stamp.Version.pattern().nid();
        ChangeChronology chronology = Calculators.Stamp.DevelopmentLatest().changeChronology(TinkarTerm.ROOT_VERTEX.nid());
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
}
