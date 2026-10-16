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
package dev.ikm.tinkar.importer.gudid;

import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.ext.lang.owl.Rf2OwlToLogicAxiomTransformer;
import dev.ikm.tinkar.importer.ImportException;
import dev.ikm.tinkar.importer.ImportReport;
import dev.ikm.tinkar.importer.ImportSource;
import dev.ikm.tinkar.importer.KnowledgeImporter;
import dev.ikm.tinkar.importer.StoreWriter;
import dev.ikm.tinkar.importer.gudid.GudidTerms.MedicalSpecialty;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Imports the FDA's Global Unique Device Identification Database: AccessGUDID's delimited release
 * with the FDA's product classification file, on the starter set. Ported from IKM's
 * {@code gudid-data} transformation plugin; concepts, and every semantic IKM gave an identity, are
 * minted as that plugin mints them, so records made against IKM's GUDID resolve here (see
 * {@link ReleaseWriter} for which semantics IKM left random and how they are derived instead).
 *
 * <p>The source is the release wherever its files are: a directory or a zip holding
 * {@code foiclass.txt} and AccessGUDID's {@code device.txt}, {@code productCodes.txt},
 * {@code identifiers.txt}, {@code premarketSubmissions.txt} and {@code gmdnTerms.txt} at any depth
 * (IKM's origin archive lays them out as {@code src/foi/} and {@code src/gudid/}), or a directory
 * or zip holding the two archives as published ({@code AccessGUDID_Delimited_Full_Release_*.zip}
 * and {@code foiclass.zip}). AccessGUDID's XML release is a different format and does not read.
 *
 * <p>Before the release, the importer loads IKM's GUDID starter data, carried in this module: the
 * GUDID module and author, the identifier sources (FDA product code, public device record key,
 * premarket submission number, the issuing agencies, GMDN terms), the medical specialties product
 * codes are stated under, and the device-to-GMDN-term pattern. The carried copies are IKM's exports
 * without their inferred navigation semantics, which assembly derives itself: IKM's listed the
 * legacy user-interface concepts the starter set does not restate as children of Author, references
 * no store can resolve and a Rocks64 store cannot load. Then the files, in IKM's order:
 * <ol>
 *   <li>{@code foiclass.txt}: a concept per FDA product code under its medical specialty;</li>
 *   <li>{@code device.txt}: a concept per device, under its publish date and record status;</li>
 *   <li>{@code productCodes.txt}: each device's OWL axiom, a kind of its product codes;</li>
 *   <li>{@code identifiers.txt}: device identifiers, by issuing agency;</li>
 *   <li>{@code premarketSubmissions.txt}: premarket submission numbers;</li>
 *   <li>{@code gmdnTerms.txt}: a concept per GMDN term, and each device's GMDN terms.</li>
 * </ol>
 * Then the devices' OWL axioms are transformed into EL++ stated axioms.
 *
 * <p>Options:
 * <dl>
 *   <dt>{@code releaseDate}</dt><dd>the release's date, {@code yyyy-MM-dd} or {@code yyyyMMdd}; by
 *   default the first {@code yyyyMMdd} in the release's name or an archive's within it, and an
 *   error if none carries one.</dd>
 *   <dt>{@code namespace}</dt><dd>the namespace identities are minted in (default IKM's GUDID
 *   namespace, {@value #IKM_NAMESPACE}).</dd>
 *   <dt>{@code path}</dt><dd>the path concept's UUID (default the development path).</dd>
 *   <dt>{@code starterData}</dt><dd>{@code false} to skip loading the GUDID starter data, which must
 *   then already be in the store.</dd>
 *   <dt>{@code medicalSpecialties}</dt><dd>specialty abbreviations, comma separated ({@code CV,HE}),
 *   to import only their product codes and devices; absent, or {@code ALL}, imports the full
 *   release.</dd>
 *   <dt>{@code owlTransform}</dt><dd>{@code false} to leave the devices' axioms as OWL syntax.</dd>
 * </dl>
 */
public final class GudidImporter implements KnowledgeImporter {

    /** IKM's GUDID namespace ({@code origin.namespace} in {@code gudid-data}'s root POM). */
    public static final String IKM_NAMESPACE = "3094dbd1-60cf-44a6-92e3-0bb32ca4d3de";

    private static final Logger LOG = LoggerFactory.getLogger(GudidImporter.class);

    /** IKM's starter data, loaded in this order: the base, then its incremental (which adds the pattern). */
    private static final List<String> STARTER_DATA = List.of(
            "gudid-starter-1-52-pb.zip", "gudid-starter-incremental-20250723-pb.zip");

    /**
     * Creates the importer; {@link java.util.ServiceLoader} calls this.
     */
    public GudidImporter() {
    }

    @Override
    public String name() {
        return "gudid";
    }

    @Override
    public String description() {
        return "GUDID: AccessGUDID's delimited release with the FDA product classification file";
    }

    @Override
    public ImportReport importInto(ImportSource source, StoreWriter writer) {
        source.requireExists();
        Instant start = Instant.now();
        UUID namespace = source.uuidOption("namespace").orElse(UUID.fromString(IKM_NAMESPACE));
        ConceptFacade path = source.uuidOption("path").<ConceptFacade>map(uuid -> EntityProxy.Concept.make(PublicIds.of(uuid)))
                .orElse(KernelConcept.DEVELOPMENT_PATH);
        Map<String, Long> details = new LinkedHashMap<>();
        StoreWriter.Tally before = writer.counts().tally();

        if (source.option("starterData").map(Boolean::parseBoolean).orElse(true)) {
            details.put("starter data components", loadStarterData());
        }
        if (!PrimitiveData.get().hasPublicId(GudidTerms.AUTHOR.publicId())
                || !PrimitiveData.get().hasPublicId(GudidTerms.GMDN_TERMS_PATTERN.publicId())) {
            throw new ImportException("The GUDID starter data is not in the store; load it, or leave starterData unset");
        }

        try (GudidRelease release = GudidRelease.open(source.path())) {
            LocalDate releaseDate = source.option("releaseDate").map(GudidImporter::parseDate)
                    .or(release::dateInNames)
                    .orElseThrow(() -> new ImportException("The GUDID release " + source.path()
                            + " carries no yyyyMMdd date in its names; give the releaseDate option"));
            EnumSet<MedicalSpecialty> specialties = specialties(source);
            LOG.info("Importing GUDID {} ({}) from {}, {}", release.name(), releaseDate, source.path(),
                    specialties.isEmpty() ? "full release" : "medical specialties " + specialties);

            ReleaseWriter releaseWriter = new ReleaseWriter(writer, release, SpecialtyFilter.of(specialties, release),
                    namespace, path, releaseDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli());
            releaseWriter.foiClass();
            releaseWriter.devices();
            releaseWriter.productCodes();
            releaseWriter.identifiers();
            releaseWriter.premarketSubmissions();
            releaseWriter.gmdnTerms();
            details.putAll(releaseWriter.details());

            if (source.option("owlTransform").map(Boolean::parseBoolean).orElse(true)
                    && !releaseWriter.axiomConcepts.isEmpty()) {
                LOG.info("Transforming {} device OWL axioms into EL++ stated axioms", releaseWriter.axiomConcepts.size());
                try {
                    // Only the devices this import wrote axioms for.
                    new Rf2OwlToLogicAxiomTransformer(new Transaction("GUDID OWL to logic"),
                            KernelPattern.OWL_AXIOM_SYNTAX_PATTERN, KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN,
                            GudidTerms.AUTHOR.nid(), GudidTerms.MODULE.nid(), path.nid(),
                            releaseWriter.axiomConcepts::contains).call();
                } catch (Exception e) {
                    throw new ImportException("Transforming GUDID OWL axioms failed", e);
                }
            }
            return ImportReport.of(name(), release.name(), writer.counts().tally().minus(before), details,
                    Duration.between(start, Instant.now()));
        }
    }

    /** The specialties the {@code medicalSpecialties} option names; none, or {@code ALL}, is the full release. */
    private static EnumSet<MedicalSpecialty> specialties(ImportSource source) {
        EnumSet<MedicalSpecialty> specialties = EnumSet.noneOf(MedicalSpecialty.class);
        String option = source.option("medicalSpecialties").orElse("").strip();
        if (option.isEmpty() || option.equalsIgnoreCase("ALL")) {
            return specialties;
        }
        for (String abbreviation : option.split("\\s*,\\s*")) {
            specialties.add(MedicalSpecialty.named(abbreviation).filter(s -> s != MedicalSpecialty.UNKNOWN)
                    .orElseThrow(() -> new ImportException("Option medicalSpecialties names " + abbreviation
                            + ", which is not an FDA medical specialty abbreviation")));
        }
        return specialties;
    }

    private static LocalDate parseDate(String value) {
        String date = value.strip();
        try {
            return date.length() == 8 ? LocalDate.parse(date, DateTimeFormatter.BASIC_ISO_DATE) : LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new ImportException("Option releaseDate is not yyyy-MM-dd or yyyyMMdd: " + value, e);
        }
    }

    /** The starter data, loaded from this module; components already present are merged. */
    private static long loadStarterData() {
        long loaded = 0;
        for (String resource : STARTER_DATA) {
            try (InputStream in = GudidImporter.class.getResourceAsStream(resource)) {
                if (in == null) {
                    throw new ImportException("The GUDID starter data " + resource + " is missing from the importer");
                }
                Path copy = Files.createTempFile("gudid-starter-", ".zip");
                try {
                    Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
                    loaded += new LoadEntitiesFromProtobufFile(copy.toFile()).compute().getTotalCount();
                    // The load ends the caller's bulk-load bracket (it is not nested); re-enter it
                    // for the writes that follow.
                    EntityService.get().beginLoadPhase();
                } finally {
                    Files.deleteIfExists(copy);
                }
            } catch (IOException e) {
                throw new ImportException("Loading the GUDID starter data " + resource + " failed", e);
            }
        }
        return loaded;
    }
}
