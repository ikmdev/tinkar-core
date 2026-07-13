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
import dev.ikm.tinkar.common.util.time.DateTimeUtil;
import dev.ikm.tinkar.entity.builder.ActiveStamp;
import dev.ikm.tinkar.entity.builder.InactiveStamp;
import dev.ikm.tinkar.entity.builder.KnowledgeSet;
import dev.ikm.tinkar.entity.builder.KonceptExtractor;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link KonceptExtractor}'s {@code since:}/{@code comments:}/{@code retiredComments:}
 * fields (IKE-Network/ike-issues#877), against a small hand-composed ledger — the real
 * starter set has no {@link TinkarTerm#COMMENT_PATTERN} content yet to exercise this
 * against, so this test builds its own: one concept, two comments (one that stays active,
 * one later retired), read back after a session write.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class KonceptExtractorHistoryIT {

    private static final KnowledgeSet TEST_SET =
            KnowledgeSet.of("5e1a9c3d-7b2f-5a6e-8c4d-1f9b3e7a2c60");

    private static final UUID RETIRED_COMMENT_ID =
            UUID.fromString("8a2d4f6c-1b3e-5c7a-9d2f-4e6b8c1a3d5f");
    private static final UUID ACTIVE_COMMENT_ID =
            UUID.fromString("2f4b6d8a-3c5e-5f7b-1a3d-6c8e2b4f7a9d");

    private static ActiveStamp birth;
    private static InactiveStamp retirement;

    @BeforeAll
    static void composeAndWrite() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);

        birth = Stamp.active("2020-01-01T00:00:00Z",
                TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH);

        TEST_SET.concept("History probe concept (Test)").at(birth)
                .definition("A concept authored only to exercise since/comments/retiredComments extraction.")
                .isA(TinkarTerm.MODEL_CONCEPT)
                .semantic(TinkarTerm.COMMENT_PATTERN, PublicIds.of(RETIRED_COMMENT_ID), "Original comment text")
                .semantic(TinkarTerm.COMMENT_PATTERN, PublicIds.of(ACTIVE_COMMENT_ID), "Still active comment text");

        retirement = Stamp.inactive("2020-06-01T00:00:00Z",
                TinkarTerm.USER, TinkarTerm.DEVELOPMENT_MODULE, TinkarTerm.DEVELOPMENT_PATH);

        // Empty field values restate the prior text verbatim under the new inactive stamp --
        // ConceptBuilder.RetireScope#retireSemantic's documented behavior.
        TEST_SET.concept("History probe concept (Test)").at(retirement)
                .retireSemantic(TinkarTerm.COMMENT_PATTERN, PublicIds.of(RETIRED_COMMENT_ID));

        TEST_SET.write();
    }

    @AfterAll
    static void afterAll() {
        TestHelper.stopDatabase();
    }

    @Test
    @DisplayName("since is the concept's own earliest stamp time; active and retired comments separate correctly")
    void sinceAndCommentsExtract() {
        String yaml = KonceptExtractor.extractYaml();
        String block = entryBlock(yaml, "HistoryProbeConcept");

        assertTrue(block.contains("  since: \"" + DateTimeUtil.format(birth.time()) + "\"\n"),
                "since must be the concept's own (only) version time:\n" + block);

        assertTrue(block.contains("    - \"Still active comment text\"\n"),
                "the never-retired comment must appear under comments:\n" + block);
        assertFalse(block.contains("    - \"Original comment text\"\n"),
                "a retired comment's text must not appear as a bare comments: entry:\n" + block);

        assertTrue(block.contains("    - text: \"Original comment text\"\n"),
                "the retired comment's prior text must appear under retiredComments:\n" + block);
        assertTrue(block.contains("      retiredAt: \"" + DateTimeUtil.format(retirement.time()) + "\"\n"),
                "retiredAt must be the retirement stamp's own time:\n" + block);
    }

    /** The text from an entry's identifier header to the next blank line. */
    private static String entryBlock(String yaml, String identifier) {
        int start = yaml.indexOf(identifier + ":\n");
        assertTrue(start >= 0, identifier + " was not extracted at all:\n" + yaml);
        int end = yaml.indexOf("\n\n", start);
        return yaml.substring(start, end < 0 ? yaml.length() : end);
    }
}
