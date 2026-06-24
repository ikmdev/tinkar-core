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
package dev.ikm.tinkar.coordinate.edit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Field-mapping correctness for {@link EditCoordinateRecord} factory methods. Uses the nid-based
 * {@code make} overloads with arbitrary distinct nids, so the tests are hermetic (no datastore): the
 * accessors read fields directly and prove that each {@code make} argument lands in the matching
 * component. Guards IKE-Network/ike-issues#744, where the record's component declaration order was
 * transposed (promotionPath/destinationModule), so every {@code make} call stored those two values
 * swapped — which in turn made {@code ObservableViewWithOverride.getOriginalValue()} (it rebuilds through
 * {@code make}) disagree with {@code getValue()} on a freshly opened card (#743).
 */
class EditCoordinateRecordTest {

    private static final int AUTHOR = 10;
    private static final int DEFAULT_MODULE = 20;
    private static final int DESTINATION_MODULE = 30;
    private static final int DEFAULT_PATH = 40;
    private static final int PROMOTION_PATH = 50;

    @Test
    @DisplayName("make(int x5) stores each argument in its own component")
    void fiveArgMakeMapsEachFieldByName() {
        EditCoordinateRecord coordinate = EditCoordinateRecord.make(
                AUTHOR, DEFAULT_MODULE, DESTINATION_MODULE, DEFAULT_PATH, PROMOTION_PATH);

        assertEquals(AUTHOR, coordinate.getAuthorNidForChanges());
        assertEquals(DEFAULT_MODULE, coordinate.getDefaultModuleNid());
        assertEquals(DESTINATION_MODULE, coordinate.getDestinationModuleNid(),
                "make()'s destinationModule argument must be retrievable via getDestinationModuleNid()");
        assertEquals(DEFAULT_PATH, coordinate.getDefaultPathNid());
        assertEquals(PROMOTION_PATH, coordinate.getPromotionPathNid(),
                "make()'s promotionPath argument must be retrievable via getPromotionPathNid()");
    }

    @Test
    @DisplayName("make(author, module, path) uses module for both module slots and path for both path slots")
    void threeArgMakeFillsBothModuleAndPathSlots() {
        EditCoordinateRecord coordinate = EditCoordinateRecord.make(AUTHOR, DEFAULT_MODULE, DEFAULT_PATH);

        assertEquals(AUTHOR, coordinate.getAuthorNidForChanges());
        assertEquals(DEFAULT_MODULE, coordinate.getDefaultModuleNid());
        assertEquals(DEFAULT_MODULE, coordinate.getDestinationModuleNid());
        assertEquals(DEFAULT_PATH, coordinate.getDefaultPathNid());
        assertEquals(DEFAULT_PATH, coordinate.getPromotionPathNid());
    }

    @Test
    @DisplayName("Rebuilding via make() from the accessors reproduces an equal record (the #743 round-trip)")
    void rebuildThroughMakeIsIdentity() {
        EditCoordinateRecord original = EditCoordinateRecord.make(
                AUTHOR, DEFAULT_MODULE, DESTINATION_MODULE, DEFAULT_PATH, PROMOTION_PATH);

        // Exactly what ObservableEditCoordinateWithOverride.getOriginalValue() does: read each accessor and
        // feed it back through make(). A transposed component order made this swap destinationModule and
        // promotionPath, so the rebuilt record was not equal to the original.
        EditCoordinateRecord rebuilt = EditCoordinateRecord.make(
                original.getAuthorNidForChanges(),
                original.getDefaultModuleNid(),
                original.getDestinationModuleNid(),
                original.getDefaultPathNid(),
                original.getPromotionPathNid());

        assertEquals(original, rebuilt);
    }
}
