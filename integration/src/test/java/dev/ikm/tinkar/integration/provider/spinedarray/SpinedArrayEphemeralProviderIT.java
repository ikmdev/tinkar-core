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
package dev.ikm.tinkar.integration.provider.spinedarray;

import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.common.service.DataServiceController;
import dev.ikm.tinkar.common.service.DataUriOption;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.ServiceExclusionGroup;
import dev.ikm.tinkar.common.service.ServiceLifecycleManager;
import dev.ikm.tinkar.common.service.internal.EntityStore;
import dev.ikm.tinkar.entity.EntityRecordFactory;
import dev.ikm.tinkar.fixtures.TestConstants;
import dev.ikm.tinkar.provider.spinedarray.SpinedArrayProvider;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.atomic.LongAdder;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Stream;
import java.util.zip.ZipFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ephemeral spined array loaded the way Komet and the test helpers load the ephemeral store:
 * the export named as the controller's data option, queued at start and loaded in the DATA_LOAD
 * phase before {@code PrimitiveData.start()} returns (IKE-Network/ike-issues#1264).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SpinedArrayEphemeralProviderIT {

    private final File starterSet = TestConstants.PB_STARTER_DATA_REASONED;

    @BeforeAll
    void startWithTheStarterSetQueued() {
        assertTrue(starterSet.isFile(), "No starter set at " + starterSet);
        CachingService.clearAll();
        DataServiceController<?> controller = PrimitiveData.getControllerOptions().stream()
                .filter(candidate -> SpinedArrayProvider.LoadController.CONTROLLER_NAME.equals(candidate.controllerName()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("No controller named " + SpinedArrayProvider.LoadController.CONTROLLER_NAME));
        controller.setDataUriOption(new DataUriOption(starterSet.getName(), starterSet.toURI()));
        ServiceLifecycleManager.get().selectServiceForGroup(ServiceExclusionGroup.DATA_PROVIDER, controller.getClass());
        PrimitiveData.start();
    }

    @AfterAll
    void stop() {
        PrimitiveData.stop();
    }

    @Test
    void everyEntityOfTheExportIsInTheStore() throws IOException {
        assertEquals("Ephemeral data", PrimitiveData.get().name());
        LongAdder count = new LongAdder();
        EntityStore.current().forEach((bytes, nid) -> count.increment());
        assertEquals(manifestEntities(starterSet), count.sum(), "entities in the store, against the export's manifest");
    }

    @Test
    void theParallelScanVisitsWhatTheSequentialScanDoes() {
        LongAdder sequential = new LongAdder();
        EntityStore.current().forEach((bytes, nid) -> sequential.increment());
        LongAdder parallel = new LongAdder();
        EntityStore.current().forEachParallel((bytes, nid) -> parallel.increment());
        assertEquals(sequential.sum(), parallel.sum());
    }

    @Test
    void everyEntityDecodes() {
        LongAdder versions = new LongAdder();
        EntityStore.current().forEach((bytes, nid) -> versions.add(EntityRecordFactory.make(bytes).versions().size()));
        assertTrue(versions.sum() > 0);
    }

    private static long manifestEntities(File export) throws IOException {
        try (ZipFile zip = new ZipFile(export)) {
            var entry = zip.getEntry("META-INF/MANIFEST.MF");
            assertTrue(entry != null, export + " has no manifest");
            Attributes manifest = new Manifest(zip.getInputStream(entry)).getMainAttributes();
            return Stream.of("Concept-Count", "Semantic-Count", "Pattern-Count", "Stamp-Count")
                    .mapToLong(name -> Long.parseLong(manifest.getValue(name))).sum();
        }
    }
}
