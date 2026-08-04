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

import dev.ikm.tinkar.common.id.IntIdList;
import dev.ikm.tinkar.common.id.IntIdSet;
import dev.ikm.tinkar.component.FieldDataType;
import dev.ikm.tinkar.terms.ConstraintTerm;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.EntityProxy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;

/**
 * The workhorse member-match relation:
 * {@link ConstraintTerm#EQUAL_MATCH_RELATION Equal} — a value matches a member when
 * they are equal by the member datatype's natural equality
 * (IKE-Network/ike-issues#889):
 * <ul>
 *   <li>entity references by <em>identity</em> (nid), whatever facade form carries
 *       them;</li>
 *   <li>String, Boolean, Integer, Long, and Instant by value;</li>
 *   <li>Float by IEEE comparison — {@code NaN} matches nothing, including itself,
 *       which keeps the loud-defaults coherence: an unrevised {@code NaN} default
 *       fails its constraint until someone chooses a real value;</li>
 *   <li>Decimal by <em>numeric</em> equality ({@code compareTo == 0}, so {@code 2.5}
 *       admits {@code 2.50}) — an identical-representation rule would be its own
 *       future relation, not an ambiguity inside this one;</li>
 *   <li>byte arrays by content; object arrays elementwise, recursively;</li>
 *   <li>id lists by size and order-sensitive element identity; id sets by
 *       membership.</li>
 * </ul>
 * Graph types are outside this relation's operand conformance: graph enumerations
 * await the isomorphic relations (over
 * {@code dev.ikm.tinkar.entity.graph.isomorphic}), which is where the canonical-form
 * equality question belongs — deferred, not silently decided here.
 */
public final class EqualMatchEvaluator implements MemberMatchEvaluator {

    /**
     * Creates the evaluator — instantiated by the service loader.
     */
    public EqualMatchEvaluator() {
    }

    @Override
    public EntityProxy.Concept relation() {
        return ConstraintTerm.EQUAL_MATCH_RELATION;
    }

    /**
     * Equality's type discipline: the operand types must be the same, with two
     * refinements — the entity family ({@code IDENTIFIED_THING}, {@code CONCEPT},
     * {@code SEMANTIC}) conforms across its members, since identity equality is
     * type-agnostic and what the set actually contains is the curator's concern; and a
     * {@code null} member type (a referenced-component-sourced set, which carries no
     * declared data type) conforms exactly when the constrained field is
     * entity-valued. Graph types never conform under equality.
     *
     * @param constrainedType the constrained field's declared data type
     * @param memberType      the value-set field's declared data type, or {@code null}
     *                        for a referenced-component-sourced set
     * @return whether the pairing is coherent under equality
     */
    @Override
    public boolean operandsConform(FieldDataType constrainedType, FieldDataType memberType) {
        if (constrainedType == null || isGraph(constrainedType) || (memberType != null && isGraph(memberType))) {
            return false;
        }
        if (memberType == null) {
            return isEntityValued(constrainedType);
        }
        if (isEntityValued(constrainedType) && isEntityValued(memberType)) {
            return true;
        }
        return constrainedType == memberType;
    }

    @Override
    public boolean matches(Object value, Object member) {
        if (value == null || member == null) {
            return false;
        }
        if (value instanceof EntityFacade valueEntity && member instanceof EntityFacade memberEntity) {
            return valueEntity.nid() == memberEntity.nid();
        }
        if (value instanceof Float valueFloat && member instanceof Float memberFloat) {
            // IEEE comparison, not Float.equals: NaN must match nothing, itself included.
            return valueFloat.floatValue() == memberFloat.floatValue();
        }
        if (value instanceof BigDecimal valueDecimal && member instanceof BigDecimal memberDecimal) {
            return valueDecimal.compareTo(memberDecimal) == 0;
        }
        if (value instanceof byte[] valueBytes && member instanceof byte[] memberBytes) {
            return Arrays.equals(valueBytes, memberBytes);
        }
        if (value instanceof Object[] valueArray && member instanceof Object[] memberArray) {
            if (valueArray.length != memberArray.length) {
                return false;
            }
            for (int index = 0; index < valueArray.length; index++) {
                if (!matches(valueArray[index], memberArray[index])) {
                    return false;
                }
            }
            return true;
        }
        if (value instanceof IntIdList valueList && member instanceof IntIdList memberList) {
            return Arrays.equals(valueList.toArray(), memberList.toArray());
        }
        if (value instanceof IntIdSet valueSet && member instanceof IntIdSet memberSet) {
            int[] valueMembers = valueSet.toArray();
            int[] memberMembers = memberSet.toArray();
            Arrays.sort(valueMembers);
            Arrays.sort(memberMembers);
            return Arrays.equals(valueMembers, memberMembers);
        }
        if (value instanceof String || value instanceof Boolean || value instanceof Integer
                || value instanceof Long || value instanceof Instant) {
            return value.equals(member);
        }
        // Graph types (outside this relation's conformance) and mismatched forms.
        return false;
    }

    private static boolean isEntityValued(FieldDataType dataType) {
        return dataType == FieldDataType.IDENTIFIED_THING || dataType == FieldDataType.CONCEPT
                || dataType == FieldDataType.SEMANTIC;
    }

    private static boolean isGraph(FieldDataType dataType) {
        return dataType == FieldDataType.DITREE || dataType == FieldDataType.DIGRAPH;
    }
}
