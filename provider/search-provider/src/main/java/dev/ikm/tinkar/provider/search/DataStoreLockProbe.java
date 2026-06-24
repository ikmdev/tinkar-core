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

import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.Lock;
import org.apache.lucene.store.LockObtainFailedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Non-destructive probe for "is this data store already open in another
 * process?".
 *
 * <p>Every persistent Komet data store (SpinedArray, MVStore, RocksDB) embeds
 * its Lucene search index at {@code <root>/lucene}, and Lucene is the only
 * component that takes a real OS-level exclusive lock on the store — the data
 * providers themselves do not (which is exactly why an already-open store
 * failed deep in startup rather than at selection; see ike-issues#722). This
 * probe therefore tests the Lucene write lock as a universal stand-in for
 * "store is open".
 *
 * <p><b>Why not just check for the lock file?</b> Lucene's {@code write.lock}
 * (like RocksDB's {@code LOCK}) is an empty marker that persists on disk
 * whether or not a process holds it — a crashed process leaves it behind with
 * no holder. Presence of the file says nothing about whether the store is open.
 * The only reliable test is to <em>attempt to acquire</em> the lock and release
 * it immediately, which is what this class does.
 *
 * <p>The probe is advisory: it is subject to a time-of-check/time-of-use race
 * (another process can open the store between probe and real open). The
 * authoritative guard remains the open-time
 * {@link dev.ikm.tinkar.common.service.DataStoreAlreadyOpenException} path.
 *
 * @see dev.ikm.tinkar.common.service.DataStoreAlreadyOpenException
 */
public final class DataStoreLockProbe {

    private static final Logger LOG = LoggerFactory.getLogger(DataStoreLockProbe.class);

    private DataStoreLockProbe() {
    }

    /**
     * Tests whether the data store rooted at {@code datastoreRoot} appears to be
     * open in another process, by attempting to acquire (and immediately
     * release) the Lucene write lock under {@code <root>/lucene}.
     *
     * <p>Acquiring and releasing the lock is non-destructive: no index content
     * is written, and the lock is held only for the duration of the probe. If
     * another process holds it, acquisition fails immediately — there is no
     * contention or blocking.
     *
     * @param datastoreRoot the data store root directory (the directory that
     *                     contains the {@code lucene} subdirectory); may be
     *                     {@code null}
     * @return a short, human-readable reason if the store is held by another
     *         process; empty if it is free, has no index yet (a new/empty
     *         target), or the state could not be determined
     */
    public static Optional<String> openConflict(File datastoreRoot) {
        if (datastoreRoot == null) {
            return Optional.empty();
        }
        Path lucenePath = datastoreRoot.toPath().resolve("lucene");
        if (!Files.isDirectory(lucenePath)) {
            // No index yet — not an existing, open store.
            return Optional.empty();
        }
        try (Directory directory = FSDirectory.open(lucenePath)) {
            // Attempt the same lock IndexWriter would take. try-with-resources
            // releases it immediately on success.
            try (Lock lock = directory.obtainLock(IndexWriter.WRITE_LOCK_NAME)) {
                // Acquired it ourselves — nobody else holds it.
                return Optional.empty();
            } catch (LockObtainFailedException held) {
                return Optional.of("Open in another process");
            }
        } catch (IOException e) {
            // Can't tell — don't block the user; the open-time check is authoritative.
            LOG.debug("Could not probe data store lock at {}: {}", lucenePath, e.toString());
            return Optional.empty();
        }
    }

    /**
     * Convenience boolean form of {@link #openConflict(File)}.
     *
     * @param datastoreRoot the data store root directory; may be {@code null}
     * @return {@code true} if the store appears open in another process
     */
    public static boolean isOpenElsewhere(File datastoreRoot) {
        return openConflict(datastoreRoot).isPresent();
    }
}
