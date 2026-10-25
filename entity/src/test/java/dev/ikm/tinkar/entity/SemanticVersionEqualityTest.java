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
package dev.ikm.tinkar.entity;

import org.eclipse.collections.api.factory.Lists;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * A semantic version read from the store holds its field values in the {@link RecordListBuilder}
 * the codec reads them into; one built in memory holds an ordinary immutable list. Versions with
 * the same stamp and the same field values are equal whichever list holds the values, in either
 * direction: an editor compares the version it is editing against the stored one to tell whether
 * an edit is unsaved.
 */
class SemanticVersionEqualityTest {

    private static final long SEMANTIC_NID = 41;
    private static final long PATTERN_NID = 42;
    private static final long REFERENCED_NID = 43;
    private static final long STAMP_NID = 44;

    @Test
    @DisplayName("A version as the codec reads it equals one built in memory with the same values")
    void storedAndBuiltVersionsAreEqual() {
        SemanticRecord semantic = semantic();
        SemanticVersionRecord stored = stored(semantic, "text", 7);
        SemanticVersionRecord built = new SemanticVersionRecord(semantic, STAMP_NID, Lists.immutable.of("text", 7));

        assertEquals(stored, built, "stored compared with built");
        assertEquals(built, stored, "built compared with stored");
        assertEquals(stored.hashCode(), built.hashCode());
    }

    @Test
    @DisplayName("A different field value still makes the versions unequal")
    void aDifferentValueIsUnequal() {
        SemanticRecord semantic = semantic();
        SemanticVersionRecord stored = stored(semantic, "text", 7);

        assertNotEquals(new SemanticVersionRecord(semantic, STAMP_NID, Lists.immutable.of("edited", 7)), stored);
        assertNotEquals(stored, new SemanticVersionRecord(semantic, STAMP_NID, Lists.immutable.of("edited", 7)));
        assertNotEquals(new SemanticVersionRecord(semantic, STAMP_NID, Lists.immutable.of("text")), stored);
    }

    private static SemanticRecord semantic() {
        return new SemanticRecord(0x5e3a_11c2_9b4d_4f0aL, 0x8d2e_7c41_a6b3_19f5L, null,
                SEMANTIC_NID, PATTERN_NID, REFERENCED_NID, Lists.immutable.empty());
    }

    /** Field values in a {@link RecordListBuilder}, as the codec reads a stored version. */
    private static SemanticVersionRecord stored(SemanticRecord semantic, Object... values) {
        RecordListBuilder<Object> fields = RecordListBuilder.make();
        for (Object value : values) {
            fields.add(value);
        }
        fields.build();
        return new SemanticVersionRecord(semantic, STAMP_NID, fields);
    }
}
