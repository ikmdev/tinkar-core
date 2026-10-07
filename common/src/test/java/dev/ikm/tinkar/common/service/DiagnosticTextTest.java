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
package dev.ikm.tinkar.common.service;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * {@link DiagnosticText} with no store running ({@code IKE-Network/ike-issues#1189}). A message
 * is composed while something has already failed, so the routine must give text, and throw
 * nothing, when there is no store to ask. What it writes against a store is tested in the
 * integration module.
 */
class DiagnosticTextTest {

    private static final long NID = -2147483000;
    private static final String FIRST = "0b6f1f4c-3d3e-4a53-9c1d-7a2f0a9b1c11";
    private static final String SECOND = "5e2c7a90-6f41-4d7e-8b0a-3c9d2e1f4a22";

    @Test
    void noStoreIsRunningInThisTest() {
        assertFalse(PrimitiveData.running());
    }

    @Test
    void aNidIsWrittenAsANidOfThisStoreWhenThereIsNoPublicIdToWrite() {
        assertEquals("nid -2147483000 in this store, which has no public id for it",
                DiagnosticText.component(NID));
        assertEquals("nid -2147483000 in this store", DiagnosticText.name(NID));
    }

    @Test
    void aPublicIdIsWrittenByEveryUuid() {
        assertEquals("UUID " + FIRST, DiagnosticText.component(PublicIds.of(FIRST)));
        assertEquals("UUIDs " + FIRST + ", " + SECOND, DiagnosticText.component(PublicIds.of(FIRST, SECOND)));
        assertEquals("UUIDs " + SECOND + ", " + FIRST, DiagnosticText.component(PublicIds.of(SECOND, FIRST)));
    }

    @Test
    void aMissingPublicIdIsNamedAsMissing() {
        assertEquals("a component with no public id", DiagnosticText.component((PublicId) null));
    }
}
