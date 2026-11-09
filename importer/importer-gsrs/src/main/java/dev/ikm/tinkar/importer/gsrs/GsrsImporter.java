/*
 * Copyright © 2026 IKE Network (support@ike.network)
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
package dev.ikm.tinkar.importer.gsrs;

import com.fasterxml.jackson.databind.JsonNode;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.importer.ImportException;
import dev.ikm.tinkar.importer.ImportReport;
import dev.ikm.tinkar.importer.ImportSource;
import dev.ikm.tinkar.importer.KnowledgeImporter;
import dev.ikm.tinkar.importer.StoreWriter;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.KernelConcept;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Imports a GSRS origin ({@link GsrsOrigin}): the FDA Global Substance Registration System's
 * public data, as GSRS publishes it. The origin is checked against its manifest before anything
 * is written. Every primary definition is written as a substance; an alternative definition,
 * which defines another substance a second way, is counted and left out. See
 * {@link SubstanceWriter} for what each substance becomes and {@link GsrsTerms} for how identities
 * are minted.
 *
 * <p>Options:
 * <dl>
 *   <dt>{@code releaseDate}</dt><dd>the date every version is written at, {@code yyyy-MM-dd} or
 *   {@code yyyyMMdd}; by default the origin manifest's release date, the date in the dump's
 *   name.</dd>
 *   <dt>{@code namespace}</dt><dd>the namespace identities are minted in (default
 *   {@value GsrsTerms#DEFAULT_NAMESPACE}).</dd>
 *   <dt>{@code path}</dt><dd>the path concept's UUID (default the development path).</dd>
 * </dl>
 */
public final class GsrsImporter implements KnowledgeImporter {

    private static final Logger LOG = LoggerFactory.getLogger(GsrsImporter.class);

    /**
     * Creates the importer; {@link java.util.ServiceLoader} calls this.
     */
    public GsrsImporter() {
    }

    @Override
    public String name() {
        return "gsrs";
    }

    @Override
    public String description() {
        return "GSRS: the FDA Global Substance Registration System's public data, each substance named and"
                + " identified by its UNII";
    }

    @Override
    public ImportReport importInto(ImportSource source, StoreWriter writer) {
        source.requireExists();
        Instant start = Instant.now();
        UUID namespace = source.uuidOption("namespace").orElse(UUID.fromString(GsrsTerms.DEFAULT_NAMESPACE));
        ConceptFacade path = source.uuidOption("path").<ConceptFacade>map(uuid -> EntityProxy.Concept.make(PublicIds.of(uuid)))
                .orElse(KernelConcept.DEVELOPMENT_PATH);
        StoreWriter.Tally before = writer.counts().tally();

        try (GsrsOrigin origin = GsrsOrigin.open(source.path())) {
            OriginManifest manifest = origin.manifest();
            LocalDate releaseDate = source.option("releaseDate").map(GsrsImporter::parseDate)
                    .orElse(manifest.releaseDate());
            LOG.info("Importing GSRS origin {} ({}, {} records) from {}", origin.name(), releaseDate,
                    manifest.records(), source.path());
            origin.verify();

            SubstanceWriter substanceWriter = new SubstanceWriter(writer, namespace, path,
                    releaseDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
            substanceWriter.shared();
            long alternatives = 0;
            try (Stream<JsonNode> records = origin.records()) {
                Iterator<JsonNode> iterator = records.iterator();
                while (iterator.hasNext()) {
                    Substance substance = Substance.of(iterator.next());
                    if (substance.primary()) {
                        substanceWriter.substance(substance);
                    } else {
                        alternatives++;
                    }
                }
            }
            Map<String, Long> details = new LinkedHashMap<>(substanceWriter.details());
            details.put("alternative definitions left out", alternatives);
            return ImportReport.of(name(), manifest.fileName(), writer.counts().tally().minus(before), details,
                    Duration.between(start, Instant.now()))
                    .withReleaseDate(releaseDate.atStartOfDay(ZoneOffset.UTC).toInstant());
        }
    }

    private static LocalDate parseDate(String value) {
        String date = value.strip();
        try {
            return date.length() == 8 ? LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE) : LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new ImportException("Option releaseDate is not yyyy-MM-dd or yyyyMMdd: " + value, e);
        }
    }
}
