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

/**
 * An immutable summary of entity counts within a Tinkar datastore,
 * broken down by entity type: concepts, semantics, patterns, and stamps.
 *
 * @param conceptCount  the number of concept entities
 * @param semanticCount the number of semantic entities
 * @param patternCount  the number of pattern entities
 * @param stampCount    the number of stamp entities
 */
public record EntityCountSummary(
        long conceptCount,
        long semanticCount,
        long patternCount,
        long stampCount
    ) {

    /**
     * Returns the sum of all entity counts.
     *
     * @return the total count across all entity types
     */
    public long getTotalCount() {
        return conceptCount + semanticCount + patternCount + stampCount;
    }
}
