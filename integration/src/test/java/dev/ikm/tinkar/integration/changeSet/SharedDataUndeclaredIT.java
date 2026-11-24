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
package dev.ikm.tinkar.integration.changeSet;

import dev.ikm.tinkar.entity.scope.SharedFolder;
import dev.ikm.tinkar.importer.registry.ChangeSetLoad;
import dev.ikm.tinkar.integration.helper.DataStore;
import dev.ikm.tinkar.integration.helper.TestHelper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * A knowledge base whose starter set does not have the Shared module reads no shared data, which it would
 * take for knowledge (IKE-Network/ike-issues#1313).
 */
class SharedDataUndeclaredIT {

    @TempDir
    Path folder;

    @BeforeAll
    static void start() {
        TestHelper.startDataBase(DataStore.EPHEMERAL_STORE);
    }

    @AfterAll
    static void stop() {
        TestHelper.stopDatabase();
    }

    @Test
    void theSharedFolderIsNotRead() throws Exception {
        Path shared = Files.createDirectories(SharedFolder.of(folder));
        Files.write(shared.resolve("a.adoc [00000000-0000-4000-8000-000000000001] Author 20261123T100000PST abc ike-cs.zip"), new byte[] {1});
        assertFalse(SharedFolder.declared());
        assertEquals(0, ChangeSetLoad.loadStore(folder, ChangeSetLoad.Progress.NONE).results().size());
        assertEquals(0, ChangeSetLoad.loadShared(folder, ChangeSetLoad.Progress.NONE).results().size());
    }
}
