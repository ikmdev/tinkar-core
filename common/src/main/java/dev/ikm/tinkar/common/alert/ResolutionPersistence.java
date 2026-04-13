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
package dev.ikm.tinkar.common.alert;

/**
 * Indicates whether a resolution applied by a {@link Resolver} or {@link AlertResolver}
 * is temporary (valid only for the current session) or permanent (persisted across sessions).
 */
public enum ResolutionPersistence {
    /** The resolution applies only for the duration of the current session. */
    TEMPORARY,
    /** The resolution is persisted and applies across sessions. */
    PERMANENT
}
