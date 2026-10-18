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
package dev.ikm.tinkar.importer;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * What an import did.
 *
 * @param importer      the importer's name
 * @param sourceVersion the release the source says it is, as the publisher names it
 * @param written       components created for the first time
 * @param unchanged     components found the same, left untouched
 * @param versioned     components given a new version
 * @param retired       semantics retired
 * @param details       importer-specific counts in the order they were reported (rows by file, ...)
 * @param elapsed       how long the import took
 * @param releaseDate   the date the publisher gave the release, when the importer knows it; the
 *                      release registration is stamped at it (IKE-Network/ike-issues#1304)
 */
public record ImportReport(String importer, String sourceVersion, long written, long unchanged, long versioned,
                           long retired, Map<String, Long> details, Duration elapsed,
                           java.util.Optional<Instant> releaseDate) {

    /**
     * Validates and copies, keeping the details' order.
     */
    public ImportReport {
        Objects.requireNonNull(importer, "importer");
        Objects.requireNonNull(sourceVersion, "sourceVersion");
        Objects.requireNonNull(elapsed, "elapsed");
        Objects.requireNonNull(releaseDate, "releaseDate");
        details = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(Objects.requireNonNull(details, "details")));
    }

    /**
     * A report with no release date.
     *
     * @param importer      the importer's name
     * @param sourceVersion the release
     * @param written       components created for the first time
     * @param unchanged     components found the same, left untouched
     * @param versioned     components given a new version
     * @param retired       semantics retired
     * @param details       importer-specific counts
     * @param elapsed       how long the import took
     */
    public ImportReport(String importer, String sourceVersion, long written, long unchanged, long versioned,
                        long retired, Map<String, Long> details, Duration elapsed) {
        this(importer, sourceVersion, written, unchanged, versioned, retired, details, elapsed,
                java.util.Optional.empty());
    }

    /**
     * This report, with the date the publisher gave the release.
     *
     * @param date the release date
     * @return the report
     */
    public ImportReport withReleaseDate(Instant date) {
        return new ImportReport(importer, sourceVersion, written, unchanged, versioned, retired, details, elapsed,
                java.util.Optional.of(date));
    }

    /**
     * A report from a writer's counts.
     *
     * @param importer      the importer's name
     * @param sourceVersion the release
     * @param counts        what the writer did during this import: its tally after less its tally before
     * @param details       importer-specific counts
     * @param elapsed       how long it took
     * @return the report
     */
    public static ImportReport of(String importer, String sourceVersion, StoreWriter.Tally counts,
                                  Map<String, Long> details, Duration elapsed) {
        return new ImportReport(importer, sourceVersion, counts.written(), counts.unchanged(), counts.versioned(),
                counts.retired(), details, elapsed);
    }

    @Override
    public String toString() {
        return importer + " " + sourceVersion + ": written " + written + ", unchanged " + unchanged
                + ", versioned " + versioned + ", retired " + retired + " in " + elapsed + " " + details;
    }
}
