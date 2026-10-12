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

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * A source to import: a release archive or file, and the options its importer reads. Options are
 * strings so a Maven goal, a command line, or a properties file can pass them unchanged; each
 * importer documents the ones it reads.
 *
 * @param path    the release: a zip as the publisher distributes it, an unpacked directory, or a file
 * @param options importer options by name
 */
public record ImportSource(Path path, Map<String, String> options) {

    /**
     * Validates and copies.
     *
     * @param path    the release
     * @param options importer options by name
     */
    public ImportSource {
        Objects.requireNonNull(path, "path");
        options = Map.copyOf(Objects.requireNonNull(options, "options"));
    }

    /**
     * A source with no options.
     *
     * @param path the release
     * @return the source
     */
    public static ImportSource of(Path path) {
        return new ImportSource(path, Map.of());
    }

    /**
     * An option's value.
     *
     * @param name the option
     * @return its value, if given
     */
    public Optional<String> option(String name) {
        return Optional.ofNullable(options.get(name)).filter(value -> !value.isBlank());
    }

    /**
     * An option's value read as a UUID.
     *
     * @param name the option
     * @return its value, if given
     * @throws ImportException when the value is not a UUID
     */
    public Optional<UUID> uuidOption(String name) {
        return option(name).map(value -> {
            try {
                return UUID.fromString(value.strip());
            } catch (IllegalArgumentException e) {
                throw new ImportException("Option " + name + " is not a UUID: " + value, e);
            }
        });
    }

    /**
     * Requires the release to exist.
     *
     * @return this source
     * @throws ImportException when the path names nothing
     */
    public ImportSource requireExists() {
        if (!Files.exists(path)) {
            throw new ImportException("The source " + path + " does not exist");
        }
        return this;
    }
}
