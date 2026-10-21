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
package dev.ikm.tinkar.integration.helper;

/**
 * The stores an integration test runs on, by controller name (IKE-Network/ike-issues#1313).
 * {@link #EPHEMERAL_STORE}, the memory-only store, is the 64-bit spined array's since that role
 * moved to it; {@link #SPINED_ARRAY_STORE}, the persistent one, is still the sequential spined
 * array's. The tests of the sequential store itself name it by a pinned constant, which no switch
 * changes.
 *
 * <p>{@code -Dintegration.engine} runs the suite on one engine without editing a test:
 * {@code 64} gives a test asking for {@link #SPINED_ARRAY_STORE} the persistent 64-bit store;
 * {@code sequential} gives one asking for {@link #EPHEMERAL_STORE} the sequential memory-only
 * store, as the suite ran before the move.
 */
public enum DataStore {
    /** The persistent store: the sequential spined array. */
    SPINED_ARRAY_STORE("Open SpinedArrayStore", false),
    /** The memory-only store: the 64-bit spined array held in memory only. */
    EPHEMERAL_STORE("Load Memory-Only 64-bit Store", false),
    /** The 64-bit spined array, persistent. */
    SPINED_ARRAY_64_STORE("Open 64-bit SpinedArrayStore", false),
    /** The 64-bit spined array held in memory only, by its own name. */
    MEMORY_ONLY_64_STORE("Load Memory-Only 64-bit Store", false),
    /** The sequential spined array, persistent, for the tests of that store itself: no switch changes it. */
    SEQUENTIAL_SPINED_ARRAY_STORE("Open SpinedArrayStore", true),
    /** The sequential spined array's memory-only mode, for the tests of that store itself: no switch changes it. */
    SEQUENTIAL_EPHEMERAL_STORE("Load Ephemeral Store", true);

    /** The system property naming the engine the suite runs on: {@code 64} or {@code sequential}, or unset for the defaults. */
    public static final String ENGINE = "integration.engine";

    final String CONTROLLER_NAME;
    private final boolean pinned;

    DataStore(String contollerName, boolean pinned) {
        this.CONTROLLER_NAME = contollerName;
        this.pinned = pinned;
    }

    /** The store a test asking for this one runs on, under {@value #ENGINE}. */
    DataStore effective() {
        if (pinned) {
            return this;
        }
        String engine = System.getProperty(ENGINE, "");
        return switch (engine) {
            case "64" -> this == SPINED_ARRAY_STORE ? SPINED_ARRAY_64_STORE : this;
            case "sequential" -> this == EPHEMERAL_STORE || this == MEMORY_ONLY_64_STORE ? SEQUENTIAL_EPHEMERAL_STORE
                    : this == SPINED_ARRAY_64_STORE ? SEQUENTIAL_SPINED_ARRAY_STORE : this;
            default -> this;
        };
    }
}
