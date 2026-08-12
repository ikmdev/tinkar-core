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
package dev.ikm.tinkar.integration.builder;

import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.entity.builder.ActiveStamp;
import dev.ikm.tinkar.entity.builder.KnowledgeSet;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Store-level regression for the referential-integrity closure gate
 * (IKE-Network/ike-issues#937): {@code KnowledgeSet.write()} sweeps every reference the
 * written store carries and reports each one that does not resolve to a present entity.
 * The ledger below is deliberately <em>open</em> — in this bare ephemeral store, every
 * {@code TinkarTerm} it cites is a forward-minted nid with no entity — and it is shaped
 * to exercise each swept surface: stamp dimensions (status, author, module, path),
 * component-id list field members, logical-expression vertices (meaning, property key,
 * component-valued property), description field values, and the semantic's pattern.
 * <p>
 * Surefire runs this class by its {@code Test*} name (failsafe is dormant in this build
 * — the {@code TestPathProviderMultiVersion} precedent); methods are ordered because the
 * one store lifecycle this JVM gets is shared by both, and the default-detection check
 * must run before the property flips enforcement on.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestReferentialClosure {

    /** The property {@code KnowledgeSet.write()} consults for closure fatality. */
    private static final String ENFORCE_CLOSURE_PROPERTY = "knowledgeSet.enforceClosure";

    private static final KnowledgeSet OPEN_SET =
            KnowledgeSet.of("2f0a7d5e-9c31-5b46-8a2d-6e1c4f9b7a53");

    /** An identity nothing ever declares — the component-id list member that must dangle. */
    private static final UUID ABSENT_MEMBER =
            UUID.fromString("7d3b9e1c-4a86-4f25-b7d0-9c5e2a8f6b14");

    @BeforeAll
    static void composeOpenLedger() {
        System.clearProperty(ENFORCE_CLOSURE_PROPERTY);
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);

        // Stamp dimensions dangle: USER / DEVELOPMENT_MODULE / DEVELOPMENT_PATH and the
        // Active status concept have no entities in a bare store.
        ActiveStamp birth = Stamp.active("2026-08-11T00:00:00Z",
                TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH);

        // The roster pattern is declared, so the pattern itself is present — only its
        // meaning/purpose/field-definition references (TinkarTerm) dangle.
        OPEN_SET.pattern("Roster pattern (ClosureTest)").at(birth)
                .meaning(TinkarTerm.MODEL_CONCEPT).purpose(TinkarTerm.USER)
                .field(TinkarTerm.MODEL_CONCEPT, TinkarTerm.USER, TinkarTerm.COMPONENT_ID_LIST_FIELD)
                .synonym("Roster pattern");

        // The concept's stated axiom cites MODEL_CONCEPT — a graph vertex property value
        // with no entity, under vertex meanings (definition root, necessary set, and,
        // concept axiom) and a property key (concept reference) that dangle too. Its
        // roster semantic carries the one component-id list member that must dangle.
        OPEN_SET.concept("Rooted kind (ClosureTest)").at(birth)
                .synonym("Rooted kind")
                .isA(TinkarTerm.MODEL_CONCEPT)
                .semantic(OPEN_SET.patternRef("Roster pattern (ClosureTest)"),
                        PublicIds.of(OPEN_SET.uuidFor("Roster (ClosureTest)")),
                        PublicIds.list.of(PublicIds.of(ABSENT_MEMBER)));
    }

    @AfterAll
    static void afterAll() {
        System.clearProperty(ENFORCE_CLOSURE_PROPERTY);
        TestHelper.stopDatabase();
    }

    @Test
    @Order(1)
    @DisplayName("Detection is universal but fatality is opt-in: an open set writes without throwing")
    void detectionOnlyByDefault() {
        assertDoesNotThrow(OPEN_SET::write,
                "Without -DknowledgeSet.enforceClosure=true a closure violation must only be logged");
    }

    @Test
    @Order(2)
    @DisplayName("Enforcement names every swept surface: stamp dimensions, id-list members, and axiom vertices")
    void enforcementNamesEverySurface() {
        System.setProperty(ENFORCE_CLOSURE_PROPERTY, "true");
        try {
            IllegalStateException violation = assertThrows(IllegalStateException.class, OPEN_SET::write);
            String message = violation.getMessage();

            // The newly hardened surfaces (IKE-Network/ike-issues#937 filed scope).
            assertTrue(message.contains("stamp status"), message);
            assertTrue(message.contains("stamp author"), message);
            assertTrue(message.contains("stamp module"), message);
            assertTrue(message.contains("stamp path"), message);
            assertTrue(message.contains("component-id field member"), message);
            assertTrue(message.contains(ABSENT_MEMBER.toString()), message);
            assertTrue(message.contains("graph vertex meaning"), message);
            assertTrue(message.contains("graph vertex property key"), message);
            assertTrue(message.contains("graph vertex property value"), message);
            assertTrue(message.contains(TinkarTerm.MODEL_CONCEPT.publicId().asUuidArray()[0].toString()),
                    message);

            // The original surfaces still report: description fields and their pattern.
            assertTrue(message.contains("field value"), message);
            assertTrue(message.contains("absent pattern"), message);

            // Precision: the declared roster pattern is present, so it appears only as a
            // referencing source (its meaning/purpose/field definitions dangle), never as
            // an absent target.
            String rosterPatternUuid = OPEN_SET.uuidFor("Roster pattern (ClosureTest)").toString();
            assertFalse(message.contains("absent pattern [\"" + rosterPatternUuid + "\"]"), message);
            assertTrue(message.contains("PatternRecord [\"" + rosterPatternUuid + "\"]"), message);
        } finally {
            System.clearProperty(ENFORCE_CLOSURE_PROPERTY);
        }
    }
}
