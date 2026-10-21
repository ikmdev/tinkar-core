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
 * The stores an integration test runs on, by controller name. {@code -Dintegration.engine=64}
 * runs every test that names the sequential spined array on the 64-bit spined array instead, in
 * the same mode, persistent or memory-only (IKE-Network/ike-issues#1313): the suite run against
 * the new engine without editing each test.
 */
public enum DataStore {
    SPINED_ARRAY_STORE("Open SpinedArrayStore"),
    EPHEMERAL_STORE("Load Ephemeral Store"),
    /** The 64-bit spined array, persistent. */
    SPINED_ARRAY_64_STORE("Open 64-bit SpinedArrayStore"),
    /** The 64-bit spined array held in memory only. */
    MEMORY_ONLY_64_STORE("Load Memory-Only 64-bit Store");

    /** The system property naming the engine the sequential stores are replaced by: {@code 64}, or unset for none. */
    public static final String ENGINE = "integration.engine";

    final String CONTROLLER_NAME;

    DataStore(String contollerName) {
        this.CONTROLLER_NAME = contollerName;
    }

    /** The store a test asking for this one runs on: the 64-bit store of the same mode when {@value #ENGINE} is {@code 64}. */
    DataStore effective() {
        if (!"64".equals(System.getProperty(ENGINE))) {
            return this;
        }
        return switch (this) {
            case SPINED_ARRAY_STORE -> SPINED_ARRAY_64_STORE;
            case EPHEMERAL_STORE -> MEMORY_ONLY_64_STORE;
            default -> this;
        };
    }
}
