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
import org.apache.lucene.store.LockObtainFailedException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertThrows;

class IndexerTest {

    /**
     * When the Lucene write lock for a directory is already held — the in-JVM
     * stand-in for "this data store is already open in another process" —
     * opening an {@link Indexer} on it must surface a
     * {@link LockObtainFailedException}.
     * <p>{@code SearchProvider} keys on exactly this exception type to raise the
     * typed, non-retryable {@code DataStoreAlreadyOpenException}. Pinning the
     * contract here means a future Lucene upgrade that changed the thrown type
     * would fail this test rather than silently routing the lock failure back
     * through the generic retry path.
     */
    @Test
    void openingLockedDirectoryThrowsLockObtainFailed(@TempDir Path tempDir) throws IOException {
        try (Directory directory = FSDirectory.open(tempDir);
             IndexWriter lockHolder =
                     new IndexWriter(directory, new IndexWriterConfig(new StandardAnalyzer()))) {
            // lockHolder now owns tempDir/write.lock for this JVM.
            assertThrows(LockObtainFailedException.class, () -> new Indexer(tempDir));
        }
    }
}
