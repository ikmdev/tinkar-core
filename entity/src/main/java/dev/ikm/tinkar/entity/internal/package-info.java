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
/**
 * The entity layer's contract with the entity provider, exported only to the provider's module.
 *
 * <p>{@link dev.ikm.tinkar.entity.internal.EntityLookup} is the provider's lookup by nid. It is
 * here, and not on {@link dev.ikm.tinkar.entity.EntityService}, so that nothing outside the
 * entity layer and the provider can call it: everything else looks entities up through
 * {@link dev.ikm.tinkar.entity.EntityHandle}.
 */
package dev.ikm.tinkar.entity.internal;
