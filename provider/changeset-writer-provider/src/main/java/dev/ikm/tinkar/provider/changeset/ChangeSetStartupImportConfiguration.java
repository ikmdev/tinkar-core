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
package dev.ikm.tinkar.provider.changeset;

import dev.ikm.tinkar.common.service.ChangeSetImportConfiguration;
import dev.ikm.tinkar.common.service.EntityCountSummary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Configuration for importing changesets on application startup.
 * <p>
 * This service checks for system properties pointing to changeset files or directories
 * to import during the DATA_LOAD phase.
 * </p>
 * <p>
 * System property usage:
 * <pre>
 * # Import all .pb.zip files from a directory:
 * -Dchangeset.import.dir=/path/to/changesets
 *
 * # Import specific files (comma-separated):
 * -Dchangeset.import.files=/path/to/file1.pb.zip,/path/to/file2.pb.zip
 * </pre>
 * </p>
 * <p>
 * Files from the directory are sorted alphabetically before being queued.
 * Files specified via the files property are queued in the order listed.
 * </p>
 */
public class ChangeSetStartupImportConfiguration implements ChangeSetImportConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger(ChangeSetStartupImportConfiguration.class);
    private static final String IMPORT_DIR_PROPERTY = "changeset.import.dir";
    private static final String IMPORT_FILES_PROPERTY = "changeset.import.files";

    @Override
    public List<File> getChangeSetFilesToImport() {
        List<File> files = new ArrayList<>();

        // Check for import directory
        String importDir = System.getProperty(IMPORT_DIR_PROPERTY);
        if (importDir != null) {
            File dir = new File(importDir);
            if (dir.exists() && dir.isDirectory()) {
                LOG.info("Scanning changeset import directory: {}", dir.getAbsolutePath());
                File[] dirFiles = dir.listFiles((d, name) ->
                        name.endsWith(".pb.zip") || (name.endsWith(".zip") && name.contains("tink")));
                if (dirFiles != null && dirFiles.length > 0) {
                    // Sort files alphabetically for consistent ordering
                    Arrays.sort(dirFiles);
                    for (File f : dirFiles) {
                        files.add(f);
                        LOG.info("  Found changeset: {}", f.getName());
                    }
                } else {
                    LOG.warn("No changeset files found in directory: {}", dir.getAbsolutePath());
                }
            } else {
                LOG.warn("Import directory does not exist or is not a directory: {}", importDir);
            }
        }

        // Check for explicit file list
        String importFilesList = System.getProperty(IMPORT_FILES_PROPERTY);
        if (importFilesList != null) {
            String[] filePaths = importFilesList.split(",");
            for (String path : filePaths) {
                File f = new File(path.trim());
                if (f.exists()) {
                    files.add(f);
                    LOG.info("  Queued changeset file: {}", f.getName());
                } else {
                    LOG.warn("Changeset file does not exist: {}", path.trim());
                }
            }
        }

        if (files.isEmpty()) {
            LOG.debug("No startup changesets configured via {} or {}",
                    IMPORT_DIR_PROPERTY, IMPORT_FILES_PROPERTY);
        } else {
            LOG.info("Found {} changeset file(s) to import on startup", files.size());
        }

        return files;
    }

    @Override
    public void onImportComplete(EntityCountSummary summary) {
        LOG.info("Startup changeset import completed: {}", summary);
    }
}
