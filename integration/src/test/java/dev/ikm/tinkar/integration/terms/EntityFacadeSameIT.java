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
package dev.ikm.tinkar.integration.terms;

import dev.ikm.tinkar.entity.ConceptRecord;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.StampEntity;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.State;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One concept reaches code in three forms: an {@link EntityProxy}, a binding enum
 * ({@link KernelConcept}) and the stored {@link ConceptRecord}. {@link EntityFacade#same} says they
 * are one component whichever form each operand has, and a record's {@code equals} and
 * {@code hashCode} follow the proxy's contract (by nid), so a record and a proxy agree in either
 * direction and in a hashed collection. The enum's {@code equals} is {@code Enum.equals},
 * identity, which no record or proxy can change: that is why comparisons go through the helper.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class EntityFacadeSameIT {

    private ConceptRecord englishRecord;
    private EntityProxy.Concept englishProxy;
    private final KernelConcept englishEnum = KernelConcept.ENGLISH_LANGUAGE;

    @BeforeAll
    void startStore() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);

        Transaction transaction = Transaction.make("The English language concept, as a record");
        StampEntity<?> stamp = transaction.getStamp(State.ACTIVE, System.currentTimeMillis(),
                KernelConcept.USER.nid(), KernelConcept.SOLOR_OVERLAY_MODULE.nid(), KernelConcept.DEVELOPMENT_PATH.nid());
        englishRecord = ConceptRecord.build(englishEnum.publicId(), stamp.versions().get(0));
        EntityService.get().putEntity(englishRecord);
        transaction.addComponent(englishRecord);
        transaction.commit();
        englishProxy = EntityProxy.Concept.make("English language", englishEnum.publicId());
    }

    @AfterAll
    void stopStore() {
        TestHelper.stopDatabase();
    }

    @Test
    void everyFormOfOneConceptIsTheSameComponent() {
        List<ConceptFacade> forms = List.of(englishProxy, englishEnum, englishRecord);
        for (ConceptFacade a : forms) {
            for (ConceptFacade b : forms) {
                assertTrue(EntityFacade.same(a, b), () -> a.getClass().getSimpleName() + " vs " + b.getClass().getSimpleName());
            }
        }
    }

    @Test
    void differentConceptsAreNotTheSame() {
        for (ConceptFacade form : List.of(englishProxy, englishEnum, englishRecord)) {
            assertFalse(EntityFacade.same(form, KernelConcept.SPANISH_LANGUAGE), form.getClass().getSimpleName());
            assertFalse(EntityFacade.same(form, null), form.getClass().getSimpleName() + " vs null");
            assertFalse(EntityFacade.same(null, form), "null vs " + form.getClass().getSimpleName());
        }
        assertTrue(EntityFacade.same(null, null));
    }

    @Test
    void aRecordEqualsAProxyAndAnEnumOfItsConcept() {
        assertEquals(englishRecord, englishProxy);
        assertEquals(englishProxy, englishRecord);
        assertTrue(englishRecord.equals(englishEnum), "a record accepts the enum by nid");
        assertEquals(englishProxy.hashCode(), englishRecord.hashCode(), "proxy and record hash alike");
    }

    @Test
    @SuppressWarnings("facade-check") // shows the hazard the check reports
    void anEnumReceiverEqualsOnlyItself() {
        // Enum.equals is final identity: why `KernelConcept.X.equals(facade)` must not be written
        assertFalse(englishEnum.equals(englishRecord));
        assertFalse(englishEnum.equals(englishProxy));
    }
}
