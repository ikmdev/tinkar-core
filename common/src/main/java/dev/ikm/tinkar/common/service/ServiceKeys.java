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

public enum ServiceKeys {
    /**
     * Folder containing data store files &amp; folders
     */
    DATA_STORE_ROOT,
    /**
     * When true, providers should ensure the data store root is new/empty.
     */
    DATA_STORE_EXPECT_EMPTY,
    /**
     * When true, the entity service cancels, as it starts, every stamp left uncommitted outside
     * a transaction. Off by default: an uncommitted stamp is work in progress, and it survives a
     * restart and travels in change sets. A deployment that wants every session to begin with
     * no uncommitted work turns this on.
     */
    CANCEL_UNCOMMITTED_STAMPS_AT_STARTUP,
    /**
     * Unique to each invocation of the JVM. Will persist across cache resets.
     */
    JVM_UUID,
    /**
     * Unique to each cache period. It is reset each time CachingService.reset() is called.
     */
    CACHE_PERIOD_UUID
}
