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
package dev.ikm.tinkar.common.service;

import java.io.File;
import java.util.List;

/**
 * Configuration interface for services that need to import changesets on startup.
 * <p>
 * Controllers implementing this interface can configure the DataLoadController
 * with files to import during the DATA_LOAD phase.
 * </p>
 * <p>
 * This interface is discovered via ServiceLoader during the DATA_LOAD phase.
 * The DataLoadController will call {@link #getChangeSetFilesToImport()} on all
 * registered implementations and queue the returned files for loading.
 * </p>
 * <p>
 * Example implementation:
 * <pre>{@code
 * public class MyChangeSetConfig implements ChangeSetImportConfiguration {
 *     @Override
 *     public List<File> getChangeSetFilesToImport() {
 *         String importDir = System.getProperty("my.changesets.dir");
 *         if (importDir != null) {
 *             File dir = new File(importDir);
 *             // Scan directory and return list of .pb.zip files
 *         }
 *         return Collections.emptyList();
 *     }
 * }
 * }</pre>
 * </p>
 */
public interface ChangeSetImportConfiguration {

    /**
     * Returns changeset files to import on startup.
     * Called during DATA_LOAD phase before DataLoadController executes.
     *
     * @return list of changeset files to import, or empty list if none
     */
    List<File> getChangeSetFilesToImport();

    /**
     * Called after changesets are successfully imported.
     * Override this method to perform post-import actions.
     *
     * @param summary summary of imported entities
     */
    default void onImportComplete(EntityCountSummary summary) {
        // Override if needed
    }
}
