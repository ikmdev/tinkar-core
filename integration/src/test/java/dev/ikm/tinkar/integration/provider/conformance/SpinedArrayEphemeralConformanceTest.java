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
package dev.ikm.tinkar.integration.provider.conformance;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.fixtures.PrimitiveDataServiceConformance;
import dev.ikm.tinkar.fixtures.WithKeyValueProvider;
import dev.ikm.tinkar.provider.spinedarray.SpinedArrayProvider;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The provider conformance suite against the spined array in its ephemeral mode: a new, empty
 * store held in memory only (IKE-Network/ike-issues#1264). The fixture names a data path for
 * every store; an ephemeral store must leave nothing there.
 */
@WithKeyValueProvider(controllerClass = SpinedArrayProvider.LoadController.class, cleanOnStart = true)
class SpinedArrayEphemeralConformanceTest extends PrimitiveDataServiceConformance {

    @Test
    void nothingReachesDisk() {
        assertEquals("Ephemeral data", PrimitiveData.get().name());
        File dataPath = new File("target/key-value-store/" + SpinedArrayEphemeralConformanceTest.class.getSimpleName());
        for (String entry : new String[]{"nidToByteArrayMap", "nidToCitingComponentNidMap", "nextNidKeyFile"}) {
            assertFalse(new File(dataPath, entry).exists(), entry + " was written by an ephemeral store");
        }
    }

    /**
     * The reverse of minting: a nid resolves back to the public id it was minted from, from the
     * identity map alone, with no entity written for it. The export paths and the diagnostics
     * name a referenced-but-absent component this way (IKE-Network/ike-issues#1264).
     */
    @Test
    void aNidResolvesBackToThePublicIdItWasMintedFrom() {
        PublicId id = PublicIds.newRandom();
        long nid = EntityService.get().nidForConcept(id);
        assertArrayEquals(id.asUuidArray(), PrimitiveData.get().publicIdForNid(nid).asUuidArray());
    }
}
