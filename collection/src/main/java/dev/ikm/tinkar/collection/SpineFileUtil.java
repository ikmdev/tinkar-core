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
package dev.ikm.tinkar.collection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * The spine count of a spined store directory is recorded as the name of an empty marker file,
 * {@code spineCount-<count>}. Exactly one marker is meant to exist; a save replaces it.
 *
 * <p>A replaced marker can survive: on Windows a file another process holds open — antivirus, the
 * Search indexer, a sync client — cannot be deleted. A stale marker must not stop a store from
 * opening ({@code IKE-Network/ike-issues#1294}), so the count read is the highest marker present
 * (spines only grow, so the highest is the newest), and a save writes the new marker before it
 * removes the old ones, reporting any it cannot remove.
 */
public class SpineFileUtil {
    private static final Logger LOG = LoggerFactory.getLogger(SpineFileUtil.class);

    public static final String SPINE_COUNT_PREFIX = "spineCount-";
    public static final String SPINE_PREFIX = "spine-";

    /**
     * The spine count recorded in a directory.
     *
     * @param directory the spined store directory
     * @return the count of the highest {@code spineCount-} marker, or 0 when the directory or a
     *         marker is absent; leftover markers are reported, not fatal
     */
    public static int readSpineCount(File directory) {
        if (!directory.exists()) {
            return 0;
        }
        File[] spineCountFiles = directory.listFiles(file -> file.getName().startsWith(SPINE_COUNT_PREFIX));
        if (spineCountFiles == null || spineCountFiles.length == 0) {
            return 0;
        }
        int count = -1;
        List<String> markers = new ArrayList<>();
        for (File spineCountFile : spineCountFiles) {
            String name = spineCountFile.getName();
            markers.add(name);
            try {
                count = Math.max(count, Integer.parseInt(name.substring(SPINE_COUNT_PREFIX.length())));
            } catch (NumberFormatException notACount) {
                LOG.warn("Ignoring {} in {}: not a spine count marker", name, directory);
            }
        }
        if (count < 0) {
            throw new IllegalStateException("No readable spine count marker in " + directory + ": " + markers);
        }
        if (spineCountFiles.length > 1) {
            LOG.warn("{} holds {} spine count markers {}; using the highest, {}. A save that could not "
                    + "remove the older marker leaves one behind (ike-issues#1294).",
                    directory, spineCountFiles.length, markers, count);
        }
        return count;
    }

    /**
     * Records a directory's spine count: the new marker is written first, then every other marker
     * is removed. A marker that cannot be removed is reported and left, which
     * {@link #readSpineCount} tolerates.
     *
     * @param directory the spined store directory
     * @param count     the spine count to record
     * @throws IOException if the new marker cannot be written
     */
    public static void writeSpineCount(File directory, int count) throws IOException {
        String newCountFileName = SPINE_COUNT_PREFIX + count;
        File newCountFile = new File(directory, newCountFileName);
        if (!newCountFile.exists()) {
            Files.createFile(newCountFile.toPath());
        }
        File[] spineCountFiles = directory.listFiles(file -> file.getName().startsWith(SPINE_COUNT_PREFIX)
                && !file.getName().equals(newCountFileName));
        if (spineCountFiles == null) {
            return;
        }
        for (File countFile : spineCountFiles) {
            try {
                Files.delete(countFile.toPath());
            } catch (IOException held) {
                LOG.warn("Could not remove the old spine count marker {} after writing {}: {}",
                        countFile, newCountFileName, held.toString());
            }
        }
    }

    public static File getSpineDirectory(File parentDirectory, int patternNid) {
        File spinedMapDirectory = new File(parentDirectory, Integer.toUnsignedString(patternNid));

        spinedMapDirectory.mkdirs();
        return spinedMapDirectory;
    }
}
