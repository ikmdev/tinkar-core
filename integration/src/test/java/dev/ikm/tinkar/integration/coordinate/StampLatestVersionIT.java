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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link StampCalculatorWithCache} reads a STAMP's latest version, against the reasoned IKE
 * starter set in a real ephemeral store. A STAMP minted uncommitted keeps that version when it is
 * committed, so a committed STAMP has two versions, and each names the STAMP itself as its stamp.
 * Ordered by stamp, as the versions of other chronologies are, the two sat at one position: the
 * calculator counted and logged malformed data ("EQUAL indicates that data is malformed") and
 * kept whichever version it read first. Komet asks for a STAMP's latest version when it titles a
 * pending comment on that STAMP, which the commit narrator writes after the first journal on a
 * new knowledge base seeds the pattern-definition semantics.
 *
 * <p>Whether a read counted malformed data shows only in the calculator's private error count,
 * so that test reads it reflectively, as {@code StampCalculatorOnRouteMemoIT} reads the on-route
 * memo.
 */
class StampLatestVersionIT {

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
    @DisplayName("A committed STAMP's latest version is its committed version")
    void committedStampLatestIsCommittedVersion() {
        StampEntity<?> stamp = committedStamp("committed STAMP latest probe");
        assertEquals(2, stamp.versions().size(),
                "commit keeps the version the STAMP was minted with beside the committed one");

        Latest<EntityVersion> latest = Calculators.Stamp.DevelopmentLatest().latest(stamp.nid());

        assertTrue(latest.isPresent(), "the committed STAMP lies on the development route");
        assertFalse(latest.get().uncommitted(), "the latest version is not the uncommitted one");
        assertEquals(stamp.time(), latest.get().time(), "the latest version carries the commit time");
    }

    @Test
    @DisplayName("Reading a committed STAMP's latest version counts no malformed data")
    void committedStampLatestCountsNoMalformedData() throws ReflectiveOperationException {
        StampEntity<?> stamp = committedStamp("malformed-data count probe");
        StampCalculatorWithCache calculator = Calculators.Stamp.DevelopmentLatest();
        int before = errorCount(calculator);

        calculator.latestNoCache(stamp.nid());

        assertEquals(before, errorCount(calculator),
                "a STAMP's two versions are its lifecycle, not two versions at one position");
    }

    @Test
    @DisplayName("A canceled STAMP has no latest version")
    void canceledStampHasNoLatestVersion() {
        Transaction transaction = Transaction.make("canceled STAMP latest probe");
        StampEntity<?> uncommitted = transaction.getStamp(State.ACTIVE, KernelConcept.USER.nid(),
                IkeConcept.DEVELOPMENT_MODULE.nid(), KernelConcept.DEVELOPMENT_PATH.nid());
        transaction.cancel();

        Latest<EntityVersion> latest = Calculators.Stamp.DevelopmentLatest().latestNoCache(uncommitted.nid());

        assertFalse(latest.isPresent(), "a canceled STAMP lies on no route");
    }

    /** A STAMP minted uncommitted by a transaction of its own, then committed, as Komet's writers make them. */
    private static StampEntity<?> committedStamp(String transactionName) {
        Transaction transaction = Transaction.make(transactionName);
        StampEntity<?> uncommitted = transaction.getStamp(State.ACTIVE, KernelConcept.USER.nid(),
                IkeConcept.DEVELOPMENT_MODULE.nid(), KernelConcept.DEVELOPMENT_PATH.nid());
        transaction.commit();
        StampEntity<?> committed = Entity.getStamp(uncommitted.nid());
        assertTrue(committed.time() < Long.MAX_VALUE, "commit gave the STAMP its time");
        return committed;
    }

    private static int errorCount(StampCalculatorWithCache calculator) throws ReflectiveOperationException {
        Field field = StampCalculatorWithCache.class.getDeclaredField("errorCount");
        field.setAccessible(true);
        return field.getInt(calculator);
    }
}
