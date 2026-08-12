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
package dev.ikm.tinkar.integration.coordinate;

import dev.ikm.tinkar.coordinate.PathService;
import dev.ikm.tinkar.coordinate.stamp.StampCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StampPositionRecord;
import dev.ikm.tinkar.coordinate.stamp.StateSet;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticRecord;
import dev.ikm.tinkar.entity.SemanticVersionRecord;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.StampRecord;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.State;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.set.ImmutableSet;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Regression test for IKE-Network/ike-issues#895: a path-origins semantic whose
 * chronology carries more than one version must resolve to its latest version instead
 * of throwing {@code UnsupportedOperationException: Can't handle more than one version
 * yet...}. The multi-version shape mirrors what the ike ledger ingest produces — a
 * baseline version from starter data plus a later authored version stamped on the
 * development path.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TestPathProviderMultiVersion {

    @BeforeAll
    static void beforeAll() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
    }

    @Test
    void multiVersionPathOriginsResolveToLatestVersion() {
        int developmentPathNid = TinkarTerm.DEVELOPMENT_PATH.nid();
        int[] originSemanticNids = EntityService.get().semanticNidsForComponentOfPattern(
                developmentPathNid, TinkarTerm.PATH_ORIGINS_PATTERN.nid());
        assertEquals(1, originSemanticNids.length,
                "Starter data declares exactly one origins semantic for the development path");
        SemanticRecord originSemantic = (SemanticRecord) EntityHandle.getSemanticOrThrow(originSemanticNids[0]);
        assertEquals(1, originSemantic.versions().size(),
                "Starter data ships the origins semantic with a single baseline version");

        ImmutableSet<StampPositionRecord> baselineOrigins = PathService.get().getPathOrigins(developmentPathNid);
        assertEquals(1, baselineOrigins.size());
        assertEquals(TinkarTerm.SANDBOX_PATH.nid(), baselineOrigins.getOnly().getPathForPositionNid(),
                "Baseline resolves the development path origin to the sandbox path");

        // Append a second, later version stamped on the development path, moving the
        // origin instant to a distinct value so the assertion below can only pass if
        // the latest version's fields win.
        Instant revisedOriginInstant = Instant.parse("2026-01-01T00:00:00Z");
        long inceptionTime = Instant.parse("2026-07-12T00:00:00Z").toEpochMilli();
        StampEntity inceptionStamp = StampRecord.make(UUID.randomUUID(), State.ACTIVE, inceptionTime,
                TinkarTerm.USER, TinkarTerm.SOLOR_OVERLAY_MODULE, TinkarTerm.DEVELOPMENT_PATH);
        EntityService.get().putEntity(inceptionStamp);
        SemanticVersionRecord baselineVersion = originSemantic.versions().get(0);
        ImmutableList<Object> revisedFields = Lists.immutable.of(
                baselineVersion.fieldValues().get(0), revisedOriginInstant);
        SemanticRecord withInception = originSemantic
                .with(new SemanticVersionRecord(originSemantic, inceptionStamp.nid(), revisedFields))
                .build();
        EntityService.get().putEntity(withInception);

        SemanticEntity reloaded = EntityHandle.getSemanticOrThrow(originSemanticNids[0]);
        assertEquals(2, reloaded.versions().size(), "The origins semantic now carries two versions");

        // Before the fix this threw: UnsupportedOperationException: Can't handle more than one version yet...
        ImmutableSet<StampPositionRecord> resolvedOrigins = PathService.get().getPathOrigins(developmentPathNid);
        assertEquals(1, resolvedOrigins.size());
        StampPositionRecord resolvedOrigin = resolvedOrigins.getOnly();
        assertEquals(TinkarTerm.SANDBOX_PATH.nid(), resolvedOrigin.getPathForPositionNid());
        assertEquals(revisedOriginInstant.toEpochMilli(), resolvedOrigin.time(),
                "The origin instant comes from the latest version, not the baseline");

        // The failing production stack: constructing a calculator positioned on the
        // development path walks path origins in setupPathNidSegmentMap.
        StampCoordinateRecord developmentPosition = StampCoordinateRecord.make(StateSet.ACTIVE_AND_INACTIVE,
                StampPositionRecord.make(Long.MAX_VALUE, developmentPathNid));
        assertNotNull(StampCalculatorWithCache.getCalculator(developmentPosition));

        // getPaths sweeps every declared path and must tolerate the multi-version chronology.
        assertFalse(PathService.get().getPaths().isEmpty());
    }
}
