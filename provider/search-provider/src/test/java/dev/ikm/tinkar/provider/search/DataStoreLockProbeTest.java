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
package dev.ikm.tinkar.provider.search;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the selection-time "is this data store already open?" probe behaves
 * correctly across the cases the selection UI relies on: lock held, lock free
 * but file present, no index yet, and a null root.
 */
class DataStoreLockProbeTest {

    @Test
    void reportsConflictWhenLuceneLockHeld(@TempDir Path root) throws IOException {
        Path lucene = root.resolve("lucene");
        Files.createDirectories(lucene);
        try (Directory directory = FSDirectory.open(lucene);
             IndexWriter holder =
                     new IndexWriter(directory, new IndexWriterConfig(new StandardAnalyzer()))) {
            // holder owns the write lock for the duration of this block
            assertTrue(DataStoreLockProbe.openConflict(root.toFile()).isPresent(),
                    "A held write lock must be reported as a conflict");
            assertTrue(DataStoreLockProbe.isOpenElsewhere(root.toFile()));
        }
    }

    @Test
    void noConflictWhenLockFreeButFilePresent(@TempDir Path root) throws IOException {
        Path lucene = root.resolve("lucene");
        Files.createDirectories(lucene);
        // Create and close an index so write.lock exists on disk but is not held.
        try (Directory directory = FSDirectory.open(lucene);
             IndexWriter writer =
                     new IndexWriter(directory, new IndexWriterConfig(new StandardAnalyzer()))) {
            // open then close — leaves the marker file, releases the lock
        }
        assertFalse(DataStoreLockProbe.isOpenElsewhere(root.toFile()),
                "A leftover write.lock with no holder must NOT be reported as a conflict");
    }

    @Test
    void noConflictWhenNoIndexDirectory(@TempDir Path root) {
        // A new/empty target has no lucene/ subdirectory.
        assertFalse(DataStoreLockProbe.isOpenElsewhere(root.toFile()));
    }

    @Test
    void noConflictForNullRoot() {
        assertTrue(DataStoreLockProbe.openConflict(null).isEmpty());
        assertFalse(DataStoreLockProbe.isOpenElsewhere(null));
    }
}
