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
package dev.ikm.tinkar.entity.builder;

/**
 * The service a ledger module provides so tooling can compose its knowledge set without
 * knowing the module's internals: bindings generation ({@link BindingsMain}), change-set
 * export, and the release verifier all discover the ledger through this interface via
 * {@link java.util.ServiceLoader}.
 * <p>
 * Implementations compose the full ledger — every wave — into the set's session and
 * return the set. Composition must be store-free: builders defer everything that mints
 * nids (axiom graph materialization happens at {@link KnowledgeSet#write()}), so a
 * {@code KnowledgeSetSource} can be composed for reflection over its declarations without a
 * running datastore. Ledger modules should register the implementation both in
 * {@code module-info} ({@code provides}) and in {@code META-INF/services} for classpath
 * discovery by build tooling.
 */
public interface KnowledgeSetSource {

    /**
     * Composes the full ledger into the knowledge set's session and returns the set.
     * Repeatable only across JVMs, not within one — the session registry accumulates, so
     * tooling calls this once.
     *
     * @return the composed knowledge set
     */
    KnowledgeSet compose();
}
