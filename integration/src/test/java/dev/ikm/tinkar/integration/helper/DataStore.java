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
 * The stores an integration test runs on, by controller name: both are the 64-bit spined array's,
 * persistent and memory-only. The sequential spined array, once the persistent store here, left
 * tinkar-core for komet-sequential-store-plugin, and its own tests went with it
 * (IKE-Network/ike-issues#1313).
 */
public enum DataStore {
    /** The persistent store: the 64-bit spined array. */
    SPINED_ARRAY_STORE("Open 64-bit SpinedArrayStore"),
    /** The memory-only store: the 64-bit spined array held in memory only. */
    EPHEMERAL_STORE("Load Memory-Only 64-bit Store");

    final String CONTROLLER_NAME;

    DataStore(String contollerName) {
        this.CONTROLLER_NAME = contollerName;
    }
}
