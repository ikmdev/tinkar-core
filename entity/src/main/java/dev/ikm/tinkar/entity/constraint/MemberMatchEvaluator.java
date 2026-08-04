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
package dev.ikm.tinkar.entity.constraint;

import dev.ikm.tinkar.component.FieldDataType;
import dev.ikm.tinkar.terms.EntityProxy;

/**
 * The evaluator side of a member-match relation (IKE-Network/ike-issues#889): the
 * value-set field constraint's rule for how a constrained field's value must match an
 * enumerated member — satisfied when <em>some</em> active member matches. The relation
 * itself is a concept in the knowledge base (a child of
 * {@link dev.ikm.tinkar.terms.ConstraintTerm#MEMBER_MATCH_RELATION}); this interface is
 * the code-sovereign dispatch for it, discovered via
 * {@link dev.ikm.tinkar.common.service.PluggableService}.
 *
 * <p><b>Code points at knowledge, never knowledge at code.</b> Each implementation
 * declares which relation concept it evaluates ({@link #relation()}) as a compiled
 * constant — the same declared-identity arrow as
 * {@link dev.ikm.tinkar.terms.DefaultsTemplateTerm}. The knowledge base never carries
 * an operative pointer to a function, so imported content can never redirect what
 * constraint checking executes. The admission gate is the bijection test where the
 * relation content lives (IKE-Network/ike-issues#890): relation concepts one-to-one
 * with service-loaded evaluators.
 *
 * <p>Type conformance is relation-relative: what "the constrained field's type must
 * agree with the member field's type" means depends on the relation (equality wants the
 * same type; a subgraph relation wants both graph-typed; substring relations want
 * String on both sides), so each evaluator states its own discipline
 * ({@link #operandsConform}) for the authoring-time well-formedness check.
 */
public interface MemberMatchEvaluator {

    /**
     * The relation concept this evaluator implements — declared in code, by identity
     * (a {@code ConstraintTerm}-style constant), never configured from content.
     *
     * @return the member-match relation concept; never null
     */
    EntityProxy.Concept relation();

    /**
     * Whether a constraint using this relation is well-formed for the given operand
     * types — the relation-relative type discipline the authoring-time checker
     * consults.
     *
     * @param constrainedType the constrained field's declared data type
     * @param memberType      the value-set field's declared data type, or {@code null}
     *                        when the value-set field names the source pattern's
     *                        referenced-component meaning (referenced components carry
     *                        no declared data type — the member is entity-valued)
     * @return {@code true} when a constraint pairing these operand types is coherent
     *         under this relation
     */
    boolean operandsConform(FieldDataType constrainedType, FieldDataType memberType);

    /**
     * Whether a constrained field's value matches one enumerated member, both in their
     * stored field-value forms (the forms {@code SemanticEntityVersion.fieldValues()}
     * yields — entity references as facades, scalars as their Java types, byte arrays,
     * object arrays, id lists and sets, graph entities).
     *
     * @param value  the constrained field's value
     * @param member the enumerated member value (one active member semantic's value at
     *               the named value-set field, or its referenced component)
     * @return {@code true} when {@code value} matches {@code member} under this
     *         relation
     */
    boolean matches(Object value, Object member);
}
