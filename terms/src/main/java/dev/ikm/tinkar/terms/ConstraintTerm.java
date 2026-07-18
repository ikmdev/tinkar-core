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
package dev.ikm.tinkar.terms;

import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;

/**
 * Java bindings for the member-match relation seam of the value-set field constraint
 * (IKE-Network/ike-issues#889): the settled constraint design's rule that <em>how a
 * value must match an enumerated member</em> is a concept from a closed taxonomy —
 * never invented syntax — while evaluation dispatch stays code-sovereign. Code points
 * at knowledge: each {@code MemberMatchEvaluator} implementation declares which
 * relation concept it evaluates by returning one of these constants; knowledge never
 * carries an operative pointer at code, so imported content can never redirect what
 * constraint checking executes.
 *
 * <p>The admission gate is a bijection, enforced by test where the content lives
 * (IKE-Network/ike-issues#890): the relation concepts — children of
 * {@link #MEMBER_MATCH_RELATION} under the checking view — must correspond one-to-one
 * with the service-loaded evaluators. Minting a relation without shipping its
 * evaluator fails, shipping an evaluator without minting its relation fails.
 *
 * <h2>Home and identity derivation</h2>
 * As with {@link DefaultsTemplateTerm}: these are not SOLOR starter-data bindings but
 * the declared-identity seam for concepts the IkeFoundation knowledge set mints.
 * Identity is the birth-FQN-derived IkeFoundation mint,
 * {@code T5(DefaultsTemplateTerm.IKE_FOUNDATION_NAMESPACE, birthFqn)}, so the bound
 * identity equals the identity the ledger mints for the same birth FQN. Each
 * constant's initializer is a single expression, so renaming a birth FQN before
 * integration is a one-line change.
 *
 * <p><strong>Naming status (2026-07-18):</strong> the design is settled (one pattern
 * per parameter shape; relations closed, directed, evaluator-backed) — the two birth
 * FQNs below are working titles pending KEC's text pass.
 */
public final class ConstraintTerm {

    /**
     * The closed member-match relation taxonomy's parent: every admitted relation is
     * its child, and the value-set field constraint's relation field is
     * immediate-child constrained to it — the apparatus constrains itself. Relations
     * are directed and named in full (a value containing a member fragment and a
     * value contained in a member are different relations).
     * <p>
     * Identity is the birth-FQN-derived IkeFoundation mint
     * {@code T5(IKE_FOUNDATION_NAMESPACE, "Member match relation (IkeFoundation)")}.
     */
    public static final EntityProxy.Concept MEMBER_MATCH_RELATION = EntityProxy.Concept.make(
            "Member match relation (IkeFoundation)",
            UuidT5Generator.get(DefaultsTemplateTerm.IKE_FOUNDATION_NAMESPACE,
                    "Member match relation (IkeFoundation)"));

    /**
     * The workhorse relation: a value matches a member when they are equal by the
     * member datatype's natural equality — entity references by identity, scalars by
     * value, byte arrays by content, Decimal by numeric equality
     * ({@code compareTo == 0}, so an identical-representation rule would be its own
     * future relation), with IEEE comparison semantics for Float ({@code NaN} matches
     * nothing, keeping the loud-defaults coherence: an unrevised default fails its
     * constraint until revised). Graph types are outside this relation's operand
     * conformance — graph enumerations await the isomorphic relations, which is where
     * the canonical-form equality question belongs.
     * <p>
     * Identity is the birth-FQN-derived IkeFoundation mint
     * {@code T5(IKE_FOUNDATION_NAMESPACE, "Equal match relation (IkeFoundation)")}.
     */
    public static final EntityProxy.Concept EQUAL_MATCH_RELATION = EntityProxy.Concept.make(
            "Equal match relation (IkeFoundation)",
            UuidT5Generator.get(DefaultsTemplateTerm.IKE_FOUNDATION_NAMESPACE,
                    "Equal match relation (IkeFoundation)"));

    /**
     * Bindings are collections of static members; instantiation is meaningless.
     */
    private ConstraintTerm() {
    }
}
