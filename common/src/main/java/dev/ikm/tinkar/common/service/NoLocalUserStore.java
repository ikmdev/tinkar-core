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
 * Marker for a {@link PrimitiveDataService} that has no local author/STAMP store to select a
 * user from — e.g. a read-only remote-backed provider.
 *
 * <p>UI layers check {@code PrimitiveData.get() instanceof NoLocalUserStore} to decide whether
 * to skip author login/selection after a data source finishes loading, instead of hardcoding a
 * check against a specific provider implementation.
 */
public interface NoLocalUserStore {
}
