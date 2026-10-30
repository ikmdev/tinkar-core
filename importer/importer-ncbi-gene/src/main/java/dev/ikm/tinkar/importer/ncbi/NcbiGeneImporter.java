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
package dev.ikm.tinkar.importer.ncbi;

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
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Imports an NCBI Gene origin ({@link NcbiGeneOrigin}): the genes of some organisms, as NCBI
 * Datasets reported them ({@link NcbiGeneOriginBuilder}) or as NCBI Gene's bulk files hold them
 * ({@link NcbiGeneBulkOriginBuilder}); both read into the same genes. Genes are
 * written as NCBI holds them, per reference strain: an influenza A gene is PB2 of
 * A/Puerto Rico/8/1934(H1N1), not PB2 of influenza A. See {@link GeneWriter} for what each gene
 * becomes and {@link NcbiGeneTerms} for how identities are minted.
 *
 * <p>Options:
 * <dl>
 *   <dt>{@code taxa}</dt><dd>NCBI Taxonomy IDs, comma separated, to import only the genes the
 *   origin found under those of its scoped taxa; absent imports the whole origin.</dd>
 *   <dt>{@code releaseDate}</dt><dd>the date every version is written at, {@code yyyy-MM-dd} or
 *   {@code yyyyMMdd}; by default the origin manifest's release date, which is its retrieval
 *   date.</dd>
 *   <dt>{@code namespace}</dt><dd>the namespace identities are minted in (default
 *   {@value NcbiGeneTerms#DEFAULT_NAMESPACE}).</dd>
 *   <dt>{@code path}</dt><dd>the path concept's UUID (default the development path).</dd>
 * </dl>
 */
public final class NcbiGeneImporter implements KnowledgeImporter {

    private static final Logger LOG = LoggerFactory.getLogger(NcbiGeneImporter.class);

    /**
     * Creates the importer; {@link java.util.ServiceLoader} calls this.
     */
    public NcbiGeneImporter() {
    }

    @Override
    public String name() {
        return "ncbi-gene";
    }

    @Override
    public String description() {
        return "NCBI Gene: the genes of scoped organisms, from NCBI Datasets' reports or NCBI Gene's bulk files,"
                + " with their RefSeq genomic accessions and locations";
    }

    @Override
    public ImportReport importInto(ImportSource source, StoreWriter writer) {
        source.requireExists();
        Instant start = Instant.now();
        UUID namespace = source.uuidOption("namespace").orElse(UUID.fromString(NcbiGeneTerms.DEFAULT_NAMESPACE));
        ConceptFacade path = source.uuidOption("path").<ConceptFacade>map(uuid -> EntityProxy.Concept.make(PublicIds.of(uuid)))
                .orElse(KernelConcept.DEVELOPMENT_PATH);
        StoreWriter.Tally before = writer.counts().tally();

        try (NcbiGeneOrigin origin = NcbiGeneOrigin.open(source.path())) {
            OriginManifest manifest = origin.manifest();
            LocalDate releaseDate = source.option("releaseDate").map(NcbiGeneImporter::parseDate)
                    .orElse(manifest.releaseDate());
            Optional<Set<String>> inScope = taxa(source).map(manifest::geneIdsOf);
            LOG.info("Importing NCBI Gene origin {} ({}) from {}, {}", origin.name(), releaseDate, source.path(),
                    inScope.map(ids -> "taxa " + source.option("taxa").orElse("") + ", " + ids.size() + " genes")
                            .orElse("whole origin"));

            GeneWriter geneWriter = new GeneWriter(writer, namespace, path,
                    releaseDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
            geneWriter.shared();
            long skipped = 0;
            try (Stream<GeneReport> genes = origin.genes()) {
                Iterator<GeneReport> iterator = genes.iterator();
                while (iterator.hasNext()) {
                    GeneReport gene = iterator.next();
                    if (inScope.isPresent() && !inScope.get().contains(gene.geneId())) {
                        skipped++;
                        continue;
                    }
                    geneWriter.gene(gene);
                }
            }
            Map<String, Long> details = new LinkedHashMap<>(geneWriter.details());
            details.put("genes out of scope", skipped);
            return ImportReport.of(name(), origin.name(), writer.counts().tally().minus(before), details,
                    Duration.between(start, Instant.now()))
                    .withReleaseDate(releaseDate.atStartOfDay(ZoneOffset.UTC).toInstant());
        }
    }

    /** The taxa the {@code taxa} option names; none is the whole origin. */
    private static Optional<List<String>> taxa(ImportSource source) {
        String option = source.option("taxa").orElse("").strip();
        if (option.isEmpty()) {
            return Optional.empty();
        }
        List<String> taxa = new ArrayList<>();
        for (String taxon : option.split("\\s*,\\s*")) {
            if (!taxon.chars().allMatch(Character::isDigit)) {
                throw new ImportException("Option taxa names " + taxon + ", which is not an NCBI Taxonomy ID");
            }
            taxa.add(taxon);
        }
        return Optional.of(taxa);
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
