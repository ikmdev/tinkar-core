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
package dev.ikm.tinkar.integration.helper;

import dev.ikm.tinkar.entity.changeset.ChangeSetFormat;
import dev.ikm.tinkar.terms.EntityProxy;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipFile;

/**
 * What a changeset says it holds, read from its manifest: the counts its exporter wrote, by
 * kind and, for semantics, by pattern. A test that loads a changeset checks what it finds in
 * the store against these, rather than against numbers pinned in the test, so the check
 * follows the data as it grows and still has an oracle independent of the store
 * (IKE-Network/ike-issues#1295).
 *
 * @param concepts           the concepts the changeset holds
 * @param semantics          the semantics it holds, of every pattern
 * @param patterns           the patterns it holds
 * @param stamps             the stamps it holds
 * @param semanticsByPattern the semantics of each pattern, keyed by the pattern UUID its
 *                           record entry is named for
 */
public record ChangeSetManifest(long concepts, long semantics, long patterns, long stamps,
                                Map<UUID, Long> semanticsByPattern) {

    /** A format-3 record entry of semantics: {@code records/0004-<pattern uuid>.pb.gz}. */
    private static final Pattern PATTERN_ENTRY = Pattern.compile(
            "^" + ChangeSetFormat.RECORDS_PREFIX + "\\d+-([0-9a-fA-F-]{36})\\.pb\\.gz$");

    /** Reads the manifest of {@code changeSet}, failing if it has none or names no counts. */
    public static ChangeSetManifest of(File changeSet) {
        try (ZipFile zip = new ZipFile(changeSet)) {
            Manifest manifest = ChangeSetFormat.manifest(zip)
                    .orElseThrow(() -> new IllegalStateException(changeSet + " has no manifest"));
            Attributes main = manifest.getMainAttributes();
            Map<UUID, Long> byPattern = new HashMap<>();
            for (ChangeSetFormat.RecordEntry entry : ChangeSetFormat.recordEntries(zip, manifest)) {
                Matcher matcher = PATTERN_ENTRY.matcher(entry.entry().getName());
                if (matcher.matches()) {
                    byPattern.merge(UUID.fromString(matcher.group(1)), entry.count(), Long::sum);
                }
            }
            return new ChangeSetManifest(count(main, "Concept-Count", changeSet),
                    count(main, "Semantic-Count", changeSet), count(main, "Pattern-Count", changeSet),
                    count(main, "Stamp-Count", changeSet), Map.copyOf(byPattern));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** The semantics of {@code pattern} the changeset holds: zero when it holds none. */
    public long semanticsOf(EntityProxy.Pattern pattern) {
        return Arrays.stream(pattern.publicId().asUuidArray())
                .mapToLong(uuid -> semanticsByPattern.getOrDefault(uuid, 0L))
                .sum();
    }

    private static long count(Attributes main, String attribute, File changeSet) {
        String value = main.getValue(attribute);
        if (value == null) {
            throw new IllegalStateException(changeSet + "'s manifest has no " + attribute);
        }
        return Long.parseLong(value.strip());
    }
}
