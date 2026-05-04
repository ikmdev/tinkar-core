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
package dev.ikm.tinkar.entity.maintenance;

import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;

/**
 * Patterns whose semantics are conventionally one-per-referenced-component.
 * No flag exists on {@code PatternEntity} to declare this property; the set is
 * curated here and consumed by {@link SingleSemanticDuplicateWithdrawer} and
 * its callers (classifier hot path, Maven goal, Komet menu command).
 */
public final class SingleSemanticPatterns {

    public static final ImmutableList<EntityProxy.Pattern> DEFAULT = Lists.immutable.of(
            TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN,
            TinkarTerm.EL_PLUS_PLUS_INFERRED_AXIOMS_PATTERN,
            TinkarTerm.EL_PLUS_PLUS_STATED_DIGRAPH,
            TinkarTerm.EL_PLUS_PLUS_INFERRED_DIGRAPH,
            TinkarTerm.STATED_NAVIGATION_PATTERN,
            TinkarTerm.INFERRED_NAVIGATION_PATTERN);

    private SingleSemanticPatterns() {
    }
}
