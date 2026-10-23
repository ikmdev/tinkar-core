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

import dev.ikm.tinkar.coordinate.Calculators;
import dev.ikm.tinkar.coordinate.stamp.StampCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StampPositionRecord;
import dev.ikm.tinkar.coordinate.stamp.StateSet;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.State;
import network.ike.foundation.ike.bindings.IkeConcept;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StampCalculatorWithCache} memoizes whether a stamp lies on its route, against the
 * reasoned IKE starter set in a real ephemeral store. The answer depends on the stamp's path,
 * module and time, so the memo is keyed by the stamp's nid; it was once read by stamp nid but
 * written by path nid, so it never answered and every call recomputed.
 *
 * <p>A stamp's time changes once: commit replaces an uncommitted stamp's {@code Long.MAX_VALUE}
 * with the commit time. An answer given while the stamp was uncommitted must not be reused
 * after commit, or a calculator positioned after the commit would keep reporting the stamp off
 * its route.
 *
 * <p>The memo is private, so the first test reads it reflectively, as
 * {@code SpinedArrayAbsentComponentIdentityIT} reads the provider's absent-UUID map: a working
 * memo returns the same answers as no memo, so only its contents show whether it holds them.
 */
class StampCalculatorOnRouteMemoIT {

    @BeforeAll
    static void loadReasonedStarterSet() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
        TestHelper.loadDataFile(TestConstants.PB_STARTER_DATA_REASONED);
    }

    @AfterAll
    static void stop() {
        TestHelper.stopDatabase();
    }

    @Test
    @DisplayName("A committed stamp's answer is memoized under the stamp's own nid")
    void committedStampMemoizedUnderStampNid() throws ReflectiveOperationException {
        StampCalculatorWithCache calculator = Calculators.Stamp.DevelopmentLatest();
        Latest<EntityVersion> latest = calculator.latest(IkeConcept.MODEL_CONCEPT.nid());
        assertTrue(latest.isPresent(), "the probe concept has a version on the development route");
        StampEntity<?> stamp = latest.get().stamp();

        assertTrue(calculator.onRoute(stamp), "the stamp of a latest version lies on the route");

        Map<Long, Boolean> memo = onRouteMemo(calculator);
        assertEquals(Boolean.TRUE, memo.get(stamp.nid()),
                "the answer is held under the stamp's nid, where onRoute reads it");
        assertFalse(memo.containsKey(stamp.pathNid()),
                "nothing is held under the path's nid: one path holds stamps with different answers");
    }

    @Test
    @DisplayName("An answer given while a stamp was uncommitted is not reused after commit")
    void uncommittedAnswerNotReusedAfterCommit() {
        long positionTime = System.currentTimeMillis() + Duration.ofDays(1).toMillis();
        StampCalculatorWithCache calculator = StampCalculatorWithCache.getCalculator(
                StampCoordinateRecord.make(StateSet.ACTIVE_AND_INACTIVE,
                        StampPositionRecord.make(positionTime, KernelConcept.DEVELOPMENT_PATH.nid())));

        Transaction transaction = Transaction.make("onRoute memo probe");
        StampEntity<?> uncommitted = transaction.getStamp(State.ACTIVE, KernelConcept.USER.nid(),
                IkeConcept.DEVELOPMENT_MODULE.nid(), KernelConcept.DEVELOPMENT_PATH.nid());
        assertFalse(calculator.onRoute(uncommitted),
                "an uncommitted stamp's time, Long.MAX_VALUE, lies after the position");

        transaction.commit();
        StampEntity<?> committed = Entity.getStamp(uncommitted.nid());
        assertTrue(committed.time() < positionTime, "commit gave the stamp a time before the position");

        assertTrue(calculator.onRoute(uncommitted.nid()),
                "the committed stamp lies on the route; the answer from before commit is not reused");
    }

    @SuppressWarnings("unchecked")
    private static Map<Long, Boolean> onRouteMemo(StampCalculatorWithCache calculator)
            throws ReflectiveOperationException {
        Field field = StampCalculatorWithCache.class.getDeclaredField("stampOnRoute");
        field.setAccessible(true);
        return (Map<Long, Boolean>) field.get(calculator);
    }
}
