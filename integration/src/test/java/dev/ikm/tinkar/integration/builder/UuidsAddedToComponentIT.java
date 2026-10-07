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

import network.ike.foundation.ike.bindings.IkeTerms;
import dev.ikm.tinkar.terms.KernelTerm;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.IdentityAdvisories;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.builder.ActiveStamp;
import dev.ikm.tinkar.entity.builder.KnowledgeSet;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Public ids match when they share any UUID. When content arrives naming an existing component
 * under a UUID the store does not hold, the store adds that UUID to the existing component, so a
 * later lookup by it finds the same component, and raises an advisory: it is a rare event, worth
 * knowing about.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class UuidsAddedToComponentIT {

    private static final UUID HELD = UUID.fromString("6a1f2b3c-4d5e-5f60-8a7b-9c0d1e2f3a41");
    private static final UUID ADDED = UUID.fromString("6a1f2b3c-4d5e-5f60-8a7b-9c0d1e2f3a42");

    @BeforeAll
    void start() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
    }

    @AfterAll
    void stop() {
        TestHelper.stopDatabase();
    }

    @Test
    @DisplayName("Content naming a component under an extra UUID adds it to the component, with one advisory")
    void extraUuidIsAddedWithAnAdvisory() {
        ActiveStamp stamp = Stamp.active("2020-01-01T00:00:00Z", KernelTerm.USER,
                IkeTerms.DEVELOPMENT_MODULE, KernelTerm.DEVELOPMENT_PATH);

        KnowledgeSet first = KnowledgeSet.of("6a1f2b3c-4d5e-5f60-8a7b-9c0d1e2f3a00");
        first.concept("Shared concept (Test)", PublicIds.of(HELD)).at(stamp).isA(IkeTerms.MODEL_CONCEPT);
        first.write();
        long nid = PrimitiveData.nid(PublicIds.of(HELD));
        long advisoriesBefore = IdentityAdvisories.uuidsAddedCount();

        // Another source names the same component under one more UUID.
        KnowledgeSet second = KnowledgeSet.of("6a1f2b3c-4d5e-5f60-8a7b-9c0d1e2f3a01");
        second.concept("Shared concept (Test)", PublicIds.of(HELD, ADDED)).at(stamp).isA(IkeTerms.MODEL_CONCEPT);
        second.write();

        assertEquals(nid, PrimitiveData.nid(PublicIds.of(ADDED)), "the added UUID finds the existing component");
        assertEquals(Set.of(HELD, ADDED),
                Set.of(EntityHandle.get(nid).expectConcept().publicId().asUuidArray()),
                "the component holds both UUIDs");
        assertEquals(advisoriesBefore + 1, IdentityAdvisories.uuidsAddedCount(), "one advisory");

        second.write();
        assertEquals(advisoriesBefore + 1, IdentityAdvisories.uuidsAddedCount(),
                "writing again adds nothing, and advises nothing");
    }

    @Test
    @DisplayName("Content naming two existing components as one is advised and left for review")
    void componentsNamedAsOneAreAdvisedNotReconciled() {
        UUID x = UUID.fromString("6a1f2b3c-4d5e-5f60-8a7b-9c0d1e2f3a51");
        UUID y = UUID.fromString("6a1f2b3c-4d5e-5f60-8a7b-9c0d1e2f3a52");
        ActiveStamp stamp = Stamp.active("2020-01-01T00:00:00Z", KernelTerm.USER,
                IkeTerms.DEVELOPMENT_MODULE, KernelTerm.DEVELOPMENT_PATH);
        KnowledgeSet separate = KnowledgeSet.of("6a1f2b3c-4d5e-5f60-8a7b-9c0d1e2f3a10");
        separate.concept("Component x (Test)", PublicIds.of(x)).at(stamp).isA(IkeTerms.MODEL_CONCEPT);
        separate.concept("Component y (Test)", PublicIds.of(y)).at(stamp).isA(IkeTerms.MODEL_CONCEPT);
        separate.write();
        long nidX = PrimitiveData.nid(PublicIds.of(x));
        long nidY = PrimitiveData.nid(PublicIds.of(y));
        long advisoriesBefore = IdentityAdvisories.componentsSharingUuidsCount();

        // Another source names them as one component; the store goes on, and says so.
        long resolved = PrimitiveData.nid(PublicIds.of(y, x));

        assertEquals(advisoriesBefore + 1, IdentityAdvisories.componentsSharingUuidsCount(), "one advisory");
        assertEquals(nidX, PrimitiveData.nid(PublicIds.of(x)), "x still names its own component");
        assertEquals(nidY, PrimitiveData.nid(PublicIds.of(y)), "y still names its own component");
        assertEquals(x.compareTo(y) < 0 ? nidX : nidY, resolved, "the shared id resolves by its least known UUID");
    }
}
