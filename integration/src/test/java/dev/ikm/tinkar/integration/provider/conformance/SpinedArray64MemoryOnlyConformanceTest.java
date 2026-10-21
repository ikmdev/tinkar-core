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
import dev.ikm.tinkar.provider.spinedarray64.SpinedArray64Provider;
import org.junit.jupiter.api.Test;

import java.io.File;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The provider conformance suite against the 64-bit spined array held in memory only
 * (IKE-Network/ike-issues#1313). The fixture names a data path for every store; a memory-only
 * store must leave nothing there.
 */
@WithKeyValueProvider(controllerClass = SpinedArray64Provider.LoadController.class, cleanOnStart = true)
class SpinedArray64MemoryOnlyConformanceTest extends PrimitiveDataServiceConformance {

    @Test
    void nothingReachesDisk() {
        assertEquals("Memory-only data (64-bit)", PrimitiveData.get().name());
        File dataPath = new File("target/key-value-store/" + SpinedArray64MemoryOnlyConformanceTest.class.getSimpleName());
        ((SpinedArray64Provider) PrimitiveData.get()).save();
        for (String entry : new String[]{"patterns", "store-format.properties", "counters.properties"}) {
            assertFalse(new File(dataPath, entry).exists(), entry + " was written by a memory-only store");
        }
    }

    @Test
    void aNidResolvesBackToThePublicIdItWasMintedFrom() {
        PublicId id = PublicIds.newRandom();
        long nid = EntityService.get().nidForConcept(id);
        assertArrayEquals(id.asUuidArray(), PrimitiveData.get().publicIdForNid(nid).asUuidArray());
    }
}
