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

import dev.ikm.tinkar.common.id.IntIds;
import dev.ikm.tinkar.common.service.PluggableService;
import dev.ikm.tinkar.component.FieldDataType;
import dev.ikm.tinkar.terms.ConstraintTerm;
import dev.ikm.tinkar.terms.EntityProxy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.ServiceLoader;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The member-match evaluator seam (IKE-Network/ike-issues#889): service discovery, the
 * code-declared relation identity, equality's relation-relative type discipline, and
 * the Equal relation's matching behavior across the stored-value space — including the
 * deliberately ruled edges: IEEE {@code NaN} matches nothing (loud-defaults
 * coherence), Decimal matches numerically, and graph types are outside equality's
 * conformance entirely.
 */
class MemberMatchEvaluatorTest {

    private final EqualMatchEvaluator equal = new EqualMatchEvaluator();

    @Test
    @DisplayName("the Equal evaluator is service-discoverable and declares its relation in code")
    void serviceDiscoveryDeclaresTheRelation() {
        List<MemberMatchEvaluator> evaluators = PluggableService.load(MemberMatchEvaluator.class)
                .stream().map(ServiceLoader.Provider::get).toList();

        assertEquals(1, evaluators.size(),
                "exactly the Equal evaluator ships today — the bijection gate counts these");
        assertTrue(ConstraintTerm.EQUAL_MATCH_RELATION.publicId()
                        .equals(evaluators.get(0).relation().publicId()),
                "the evaluator must declare the Equal relation's identity, in code");
    }

    @Test
    @DisplayName("equality's type discipline: same type; entity family across; no graphs")
    void operandConformance() {
        assertTrue(equal.operandsConform(FieldDataType.STRING, FieldDataType.STRING));
        assertFalse(equal.operandsConform(FieldDataType.INTEGER, FieldDataType.STRING),
                "an Integer field constrained to an enumeration of Strings is malformed");
        assertTrue(equal.operandsConform(FieldDataType.CONCEPT, FieldDataType.IDENTIFIED_THING),
                "identity equality is type-agnostic across the entity family");
        assertTrue(equal.operandsConform(FieldDataType.CONCEPT, null),
                "a referenced-component-sourced set (no declared data type) serves entity-valued fields");
        assertFalse(equal.operandsConform(FieldDataType.STRING, null),
                "a referenced-component-sourced set is entity-valued — scalar fields do not conform");
        assertFalse(equal.operandsConform(FieldDataType.DITREE, FieldDataType.DITREE),
                "graph enumerations await the isomorphic relations — equality never conforms");
        assertFalse(equal.operandsConform(FieldDataType.DIGRAPH, FieldDataType.DIGRAPH));
    }

    @Test
    @DisplayName("entity references match by identity, whatever facade form carries them")
    void entityIdentityMatching() {
        assertTrue(equal.matches(EntityProxy.Concept.make(-2147483000),
                EntityProxy.make(-2147483000)));
        assertFalse(equal.matches(EntityProxy.Concept.make(-2147483000),
                EntityProxy.Concept.make(-2147483001)));
    }

    @Test
    @DisplayName("scalars match by value; Float by IEEE comparison — NaN matches nothing")
    void scalarMatching() {
        assertTrue(equal.matches("allowed", "allowed"));
        assertFalse(equal.matches("allowed", "Allowed"));
        assertTrue(equal.matches(Boolean.FALSE, Boolean.FALSE));
        assertTrue(equal.matches(777_777_777, 777_777_777));
        assertFalse(equal.matches(777_777_777, 777_777_777L),
                "an Integer never matches a Long — conformance forbids the pairing, matching agrees");
        assertTrue(equal.matches(Instant.parse("2026-07-18T00:00:00Z"),
                Instant.parse("2026-07-18T00:00:00Z")));

        assertTrue(equal.matches(1.5f, 1.5f));
        assertFalse(equal.matches(Float.NaN, Float.NaN),
                "NaN matches nothing, itself included — the unrevised loud default must fail"
                        + " its constraint until revised");
    }

    @Test
    @DisplayName("Decimal matches numerically: 2.5 admits 2.50")
    void decimalNumericMatching() {
        assertTrue(equal.matches(new BigDecimal("2.5"), new BigDecimal("2.50")),
                "numeric equality, not representation equality — an identical-representation"
                        + " rule would be its own relation");
        assertFalse(equal.matches(new BigDecimal("2.5"), new BigDecimal("2.51")));
    }

    @Test
    @DisplayName("byte arrays match by content; object arrays elementwise; id collections by shape")
    void compositeMatching() {
        assertTrue(equal.matches("UNINITIALIZED".getBytes(StandardCharsets.UTF_8),
                "UNINITIALIZED".getBytes(StandardCharsets.UTF_8)));
        assertFalse(equal.matches(new byte[]{1}, new byte[]{2}));

        assertTrue(equal.matches(new Object[]{"a", 1}, new Object[]{"a", 1}));
        assertFalse(equal.matches(new Object[]{"a", 1}, new Object[]{"a"}));

        assertTrue(equal.matches(IntIds.list.of(1, 2), IntIds.list.of(1, 2)));
        assertFalse(equal.matches(IntIds.list.of(1, 2), IntIds.list.of(2, 1)),
                "id lists are ordered — order-sensitive element identity");
        assertTrue(equal.matches(IntIds.set.of(1, 2), IntIds.set.of(2, 1)),
                "id sets are unordered — membership equality");
        assertFalse(equal.matches(IntIds.set.of(1, 2), IntIds.set.of(1, 3)));
    }

    @Test
    @DisplayName("null and mismatched forms never match")
    void mismatchedFormsNeverMatch() {
        assertFalse(equal.matches(null, "allowed"));
        assertFalse(equal.matches("allowed", null));
        assertFalse(equal.matches("777", 777),
                "a String never matches an Integer — no cross-type coercion under equality");
    }
}
