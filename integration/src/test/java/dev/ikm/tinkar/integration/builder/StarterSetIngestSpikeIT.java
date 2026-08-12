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

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.ConceptEntity;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The starter-set ingest spike (IKE-Network/ike-issues#868): walks representative
 * chronologies of the UNREASONED starter set and tallies every construct the ledger
 * builders cannot yet express with declared identity — the prioritized capability list
 * for the ingest generator (#869) and the builder extensions (#870). Exploratory by
 * design: the assertions pin the tallies' shape; the report is the deliverable.
 */
class StarterSetIngestSpikeIT {

    private static final Logger LOG = LoggerFactory.getLogger(StarterSetIngestSpikeIT.class);

    /** Representative components: platform concepts and patterns of varied shape. */
    private static final List<EntityFacade> SAMPLE = List.of(
            TinkarTerm.MODEL_CONCEPT, TinkarTerm.MODULE, TinkarTerm.PATH,
            TinkarTerm.USER, TinkarTerm.ENGLISH_LANGUAGE, TinkarTerm.DEVELOPMENT_PATH,
            TinkarTerm.PRIMORDIAL_MODULE, TinkarTerm.ROOT_VERTEX,
            TinkarTerm.DESCRIPTION_PATTERN, TinkarTerm.US_DIALECT_PATTERN);

    private static final Map<String, Integer> GAPS = new TreeMap<>();
    private static final Map<String, Integer> PATTERNS_SEEN = new TreeMap<>();

    @BeforeAll
    static void loadUnreasonedStarterSet() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA);
    }

    @AfterAll
    static void stop() {
        TestHelper.stopDatabase();
    }

    @Test
    @DisplayName("Ten chronologies enumerated: every ledger-expressibility gap tallied and reported")
    void enumerateGaps() {
        for (EntityFacade component : SAMPLE) {
            walk(component);
        }

        StringBuilder report = new StringBuilder("\n═══ Ingest spike report (#868) ═══\n");
        report.append("Semantic patterns encountered on the sample:\n");
        PATTERNS_SEEN.forEach((pattern, count) ->
                report.append(String.format("  %-46s %4d%n", pattern, count)));
        report.append("Capability gaps (construct → occurrences in the sample):\n");
        GAPS.forEach((gap, count) ->
                report.append(String.format("  %-46s %4d%n", gap, count)));
        LOG.info(report.toString());

        assertFalse(GAPS.isEmpty(), "the spike exists to find gaps — none found means it looked away");
        assertTrue(GAPS.containsKey("declared-identity stamps"),
                "store stamps are not tuple-derived; declared-identity stamps are required");
        assertTrue(GAPS.containsKey("declared-identity description semantics"),
                "description semantic ids are store-established; declared identities required");
    }

    private static void walk(EntityFacade component) {
        Entity<?> entity = EntityHandle.get(component.nid()).expectEntity();
        if (entity.publicId().asUuidArray().length > 1) {
            tally(GAPS, "multi-UUID public ids on declarations");
        }
        for (EntityVersion version : entity.versions()) {
            checkStamp(version.stampNid());
        }
        if (entity.versions().size() > 1
                && entity instanceof ConceptEntity<?>) {
            tally(GAPS, "multi-version concept chronologies");
        }

        EntityService.get().forEachSemanticForComponent(component.nid(), semantic ->
                classify(semantic));
    }

    private static void classify(SemanticEntity<SemanticEntityVersion> semantic) {
        String pattern = PrimitiveData.text(semantic.patternNid());
        tally(PATTERNS_SEEN, pattern);
        for (EntityVersion version : semantic.versions()) {
            checkStamp(version.stampNid());
        }

        int patternNid = semantic.patternNid();
        if (patternNid == TinkarTerm.DESCRIPTION_PATTERN.nid()) {
            tally(GAPS, "declared-identity description semantics");
            SemanticEntityVersion latest = semantic.versions().getLast();
            Object language = latest.fieldValues().get(0);
            if (language instanceof EntityFacade languageConcept
                    && languageConcept.nid() != TinkarTerm.ENGLISH_LANGUAGE.nid()) {
                tally(GAPS, "per-description language (non-English)");
            }
            Object caseSignificance = latest.fieldValues().get(2);
            if (caseSignificance instanceof EntityFacade caseConcept
                    && caseConcept.nid() != TinkarTerm.DESCRIPTION_NOT_CASE_SENSITIVE.nid()) {
                tally(GAPS, "per-description case significance");
            }
            Object type = latest.fieldValues().get(3);
            if (type instanceof EntityFacade typeConcept
                    && typeConcept.nid() != TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid()
                    && typeConcept.nid() != TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE.nid()
                    && typeConcept.nid() != TinkarTerm.DEFINITION_DESCRIPTION_TYPE.nid()) {
                tally(GAPS, "description types beyond FQN/regular/definition");
            }
        } else if (patternNid == TinkarTerm.US_DIALECT_PATTERN.nid()
                || patternNid == TinkarTerm.GB_DIALECT_PATTERN.nid()) {
            tally(GAPS, "declared-identity dialect semantics");
            if (patternNid == TinkarTerm.GB_DIALECT_PATTERN.nid()) {
                tally(GAPS, "GB dialect declarations (ledger emits US only)");
            }
        } else if (patternNid == TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid()) {
            tally(GAPS, "declared-identity axiom semantics");
        } else if (patternNid == TinkarTerm.INFERRED_NAVIGATION_PATTERN.nid()
                || patternNid == TinkarTerm.STATED_NAVIGATION_PATTERN.nid()
                || patternNid == TinkarTerm.EL_PLUS_PLUS_INFERRED_AXIOMS_PATTERN.nid()) {
            tally(GAPS, "derived semantics present in source artifact (exclude from ingest)");
        } else {
            tally(GAPS, "no ledger verb for pattern: " + PrimitiveData.text(patternNid));
        }
    }

    /** A stamp whose UUID is not the tuple derivation demands declared-identity stamps. */
    private static void checkStamp(int stampNid) {
        StampEntity<?> stamp = Entity.getStamp(stampNid);
        String canonical = stamp.state().publicId().asUuidArray()[0]
                + "|" + stamp.time()
                + "|" + firstUuid(stamp.authorNid())
                + "|" + firstUuid(stamp.moduleNid())
                + "|" + firstUuid(stamp.pathNid());
        UUID tupleDerived = UuidT5Generator.get(UuidT5Generator.STAMP_NAMESPACE, canonical);
        if (!tupleDerived.equals(stamp.publicId().asUuidArray()[0])) {
            tally(GAPS, "declared-identity stamps");
        }
    }

    private static UUID firstUuid(int nid) {
        PublicId publicId = PrimitiveData.publicId(nid);
        return publicId.asUuidArray()[0];
    }

    private static void tally(Map<String, Integer> tallies, String key) {
        tallies.merge(key, 1, Integer::sum);
    }

}
