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
package dev.ikm.tinkar.importer.rxnorm;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import dev.ikm.tinkar.entity.builder.ActiveStamp;
import dev.ikm.tinkar.entity.builder.InactiveStamp;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.entity.load.LoadEntitiesFromProtobufFile;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.ext.lang.owl.Rf2OwlToLogicAxiomTransformer;
import dev.ikm.tinkar.importer.ImportException;
import dev.ikm.tinkar.importer.ImportReport;
import dev.ikm.tinkar.importer.ImportSource;
import dev.ikm.tinkar.importer.KnowledgeImporter;
import dev.ikm.tinkar.importer.StoreWriter;
import dev.ikm.tinkar.terms.*;
import org.eclipse.collections.api.factory.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Imports RxNorm in OWL functional syntax, the form of NLM's pilot "Defined RxNorm with SNOMED CT
 * classes", after SNOMED CT, whose concepts its axioms use. Ported from IKM's {@code rxnorm-data}
 * transformation plugin; identities are minted as that plugin mints them, so the two agree.
 *
 * <p>Before the classes, the importer loads IKM's RxNorm starter data, carried in this module:
 * the RxNorm module, the identifier sources (RxCUI, VUID, NDC, SNOMED CT) and the patterns RxNorm's
 * annotations are recorded in (qualitative distinction, quantity, schedule, human and veterinary
 * drug, Tall Man synonym). Then, for each class, every write under the file's release date:
 * <ul>
 *   <li>the concept, merged with the SNOMED CT concept it cross-references, if any;</li>
 *   <li>descriptions: RxNorm name (fully qualified), RxNorm and prescribable synonyms, Tall Man
 *   synonyms, or for a class with only a label, the label; each US-English preferred or acceptable.
 *   On a concept merged with SNOMED CT, RxNorm's names are acceptable regular names, SNOMED CT's
 *   fully qualified name and preferred synonym kept;</li>
 *   <li>identifiers: the RxCUI of every RxNorm class (an ingredient with only a label takes its
 *   IRI's), VUID, SNOMED CT id, and NDC codes, an NDC inactive once its end date precedes the
 *   release;</li>
 *   <li>its annotations, in their patterns;</li>
 *   <li>its axiom, as OWL axiom syntax, transformed into an EL++ stated axiom.</li>
 * </ul>
 *
 * <p>Options:
 * <dl>
 *   <dt>{@code releaseDate}</dt><dd>the release's date, {@code yyyy-MM-dd}; by default read from the
 *   file name, and an error if the name has none.</dd>
 *   <dt>{@code namespace}</dt><dd>the namespace identities are minted in (default SNOMED CT's, as
 *   IKM's pipeline has it).</dd>
 *   <dt>{@code path}</dt><dd>the path concept's UUID (default the development path).</dd>
 *   <dt>{@code starterData}</dt><dd>{@code false} to skip loading the RxNorm starter data.</dd>
 *   <dt>{@code owlTransform}</dt><dd>{@code false} to leave axioms as syntax, untransformed.</dd>
 * </dl>
 */
public final class RxnormOwlImporter implements KnowledgeImporter {

    private static final Logger LOG = LoggerFactory.getLogger(RxnormOwlImporter.class);

    private static final String STARTER_DATA = "rxnorm-starter-20250707-pb.zip";
    private static final EntityProxy.Concept RXNORM_MODULE = concept("eaeb4482-4c5b-4c0c-9d41-fa021bd10c30");
    private static final EntityProxy.Concept SNOMED_IDENTIFIER = concept("a7035c97-c728-4e9e-99bb-21d3f5384583");
    private static final EntityProxy.Concept RXCUI_IDENTIFIER = concept("edcf08eb-f5c1-4893-9a51-53a9d8201d8f");
    private static final EntityProxy.Concept NDC_IDENTIFIER = concept("aea76ea1-5ee2-485f-8a4e-5d94610fe84f");
    private static final EntityProxy.Concept VUID_IDENTIFIER = concept("0385c5c5-9749-4625-8c83-b638bfec8fda");
    private static final EntityProxy.Pattern QUALITATIVE_DISTINCTION = pattern("d2a8460f-0676-4f0b-aa0f-b6275b5506a0");
    private static final EntityProxy.Pattern QUANTITY = pattern("05a40a30-1456-42a2-aa27-c902fe4185d8");
    private static final EntityProxy.Pattern SCHEDULE = pattern("e52dd008-06db-4a8d-940c-6988bf42d26a");
    private static final EntityProxy.Pattern HUMAN_DRUG = pattern("3a85992d-820d-4d7a-9ec3-e2af7a7bbfb3");
    private static final EntityProxy.Pattern VET_DRUG = pattern("590fc8df-8cd8-4338-80b0-7ca2bceeb677");
    private static final EntityProxy.Pattern TALLMAN_SYNONYM = pattern("8b5f06c8-4ec5-4918-8a6c-fa11fe41264a");

    private static final Pattern DATE_IN_NAME = Pattern.compile("(\\d{4}-\\d{2}-\\d{2})");

    @Override
    public String name() {
        return "rxnorm-owl";
    }

    @Override
    public String description() {
        return "RxNorm in OWL functional syntax (NLM's Defined RxNorm with SNOMED CT classes)";
    }

    @Override
    public ImportReport importInto(ImportSource source, StoreWriter writer) {
        source.requireExists();
        Instant start = Instant.now();
        UUID namespace = source.uuidOption("namespace").orElse(UuidUtil.SNOMED_NAMESPACE);
        ConceptFacade path = source.uuidOption("path").<ConceptFacade>map(uuid -> EntityProxy.Concept.make(PublicIds.of(uuid)))
                .orElse(KernelConcept.DEVELOPMENT_PATH);
        ConceptFacade author = EntityProxy.Concept.make("RxNorm Author", UuidT5Generator.get(namespace, "RxNorm Author"));
        Map<String, Long> details = new LinkedHashMap<>();
        StoreWriter.Tally before = writer.counts().tally();

        if (source.option("starterData").map(Boolean::parseBoolean).orElse(true)) {
            details.put("starter data components", loadStarterData());
        }
        Owl owl = readOwl(source.path());
        LocalDate releaseDate = source.option("releaseDate").map(LocalDate::parse).orElseGet(() -> dateIn(owl.name()));
        long time = releaseDate.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli();
        Stamp stamp = new ActiveStamp(time, author, RXNORM_MODULE, path);
        Stamp retired = new InactiveStamp(time, author, RXNORM_MODULE, path);
        LOG.info("Importing RxNorm OWL {} ({}) from {}", owl.name(), releaseDate, source.path());

        writeAuthor(writer, stamp, author);
        List<RxnormClass> classes = RxnormClass.read(owl.text());
        Writer classWriter = new Writer(writer, namespace, stamp, retired, releaseDate);
        long axioms = 0;
        for (RxnormClass rxClass : classes) {
            axioms += classWriter.write(rxClass);
        }
        details.put("classes", (long) classes.size());
        details.put("axioms", axioms);

        if (source.option("owlTransform").map(Boolean::parseBoolean).orElse(true) && axioms > 0) {
            LOG.info("Transforming OWL axioms into EL++ stated axioms");
            try {
                // Only the concepts this import wrote axioms for: SNOMED CT's beneath are left alone.
                new Rf2OwlToLogicAxiomTransformer(new Transaction("RxNorm OWL to logic"),
                        KernelPattern.OWL_AXIOM_SYNTAX_PATTERN, KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN,
                        author.nid(), RXNORM_MODULE.nid(), path.nid(), classWriter.axiomConcepts::contains).call();
            } catch (Exception e) {
                throw new ImportException("Transforming RxNorm OWL axioms failed", e);
            }
        }
        return ImportReport.of(name(), owl.name(), writer.counts().tally().minus(before), details,
                Duration.between(start, Instant.now()));
    }

    private static void writeAuthor(StoreWriter writer, Stamp stamp, ConceptFacade author) {
        long authorNid = writer.concept(stamp, author.publicId());
        UUID authorUuid = author.publicId().leastUuid();
        writer.describe(stamp, authorNid, UuidT5Generator.get(authorUuid, "fqn"), "RxNorm Author",
                KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE);
        writer.statedParent(stamp, author.publicId(), KernelConcept.USER.publicId());
    }

    /** The starter data, loaded from this module; components already present are merged. */
    private static long loadStarterData() {
        try (InputStream in = RxnormOwlImporter.class.getResourceAsStream(STARTER_DATA)) {
            if (in == null) {
                throw new ImportException("The RxNorm starter data " + STARTER_DATA + " is missing from the importer");
            }
            Path copy = Files.createTempFile("rxnorm-starter-", ".zip");
            try {
                Files.copy(in, copy, StandardCopyOption.REPLACE_EXISTING);
                long loaded = new LoadEntitiesFromProtobufFile(copy.toFile()).compute().getTotalCount();
                // The load ends the caller's bulk-load bracket (it is not nested); re-enter it
                // for the writes that follow.
                dev.ikm.tinkar.entity.EntityService.get().beginLoadPhase();
                return loaded;
            } finally {
                Files.deleteIfExists(copy);
            }
        } catch (IOException e) {
            throw new ImportException("Loading the RxNorm starter data failed", e);
        }
    }

    private record Owl(String name, String text) {
    }

    /** The OWL file: given directly, or the one {@code .owl} file in a zip or directory. */
    private static Owl readOwl(Path path) {
        try {
            if (Files.isDirectory(path)) {
                try (Stream<Path> walk = Files.walk(path)) {
                    List<Path> owls = walk.filter(p -> p.getFileName().toString().endsWith(".owl")).toList();
                    if (owls.size() != 1) {
                        throw new ImportException("Expected one .owl file in " + path + ", found " + owls);
                    }
                    return new Owl(owls.getFirst().getFileName().toString(), Files.readString(owls.getFirst(), StandardCharsets.UTF_8));
                }
            }
            if (path.getFileName().toString().endsWith(".zip")) {
                try (ZipFile zip = new ZipFile(path.toFile(), StandardCharsets.UTF_8)) {
                    List<? extends ZipEntry> owls = zip.stream().filter(e -> e.getName().endsWith(".owl")).toList();
                    if (owls.size() != 1) {
                        throw new ImportException("Expected one .owl file in " + path + ", found "
                                + owls.stream().map(ZipEntry::getName).toList());
                    }
                    String name = owls.getFirst().getName();
                    try (InputStream in = zip.getInputStream(owls.getFirst())) {
                        return new Owl(name.substring(name.lastIndexOf('/') + 1), new String(in.readAllBytes(), StandardCharsets.UTF_8));
                    }
                }
            }
            return new Owl(path.getFileName().toString(), Files.readString(path, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new ImportException("Cannot read the RxNorm OWL source " + path, e);
        }
    }

    private static LocalDate dateIn(String name) {
        Matcher matcher = DATE_IN_NAME.matcher(name);
        if (!matcher.find()) {
            throw new ImportException("The RxNorm OWL file name " + name
                    + " carries no yyyy-MM-dd date; give the releaseDate option");
        }
        return LocalDate.parse(matcher.group(1));
    }

    private static EntityProxy.Concept concept(String uuid) {
        return EntityProxy.Concept.make(PublicIds.of(UUID.fromString(uuid)));
    }

    private static EntityProxy.Pattern pattern(String uuid) {
        return EntityProxy.Pattern.make(PublicIds.of(UUID.fromString(uuid)));
    }

    /** Writes one class after another under the release's stamps. */
    private static final class Writer {
        private final StoreWriter writer;
        private final UUID namespace;
        private final Stamp stamp;
        private final Stamp retired;
        private final String releaseMonth;

        Writer(StoreWriter writer, UUID namespace, Stamp stamp, Stamp retired, LocalDate releaseDate) {
            this.writer = writer;
            this.namespace = namespace;
            this.stamp = stamp;
            this.retired = retired;
            this.releaseMonth = releaseDate.format(DateTimeFormatter.ofPattern("yyyyMM"));
        }

        /** Writes a class; returns the number of axioms written. */
        long write(RxnormClass rxClass) {
            UUID rxnormUuid = UuidT5Generator.get(namespace, rxClass.id + "rxnorm");
            PublicId conceptId = rxClass.snomedCtId.isEmpty() || rxClass.labelOnly()
                    ? PublicIds.of(rxnormUuid)
                    : PublicIds.of(UuidT5Generator.get(UuidUtil.SNOMED_NAMESPACE, rxClass.snomedCtId), rxnormUuid);
            long conceptNid = writer.concept(stamp, conceptId);
            if (rxClass.labelOnly()) {
                describe(conceptNid, rxnormUuid, rxClass.rdfsLabel, "LDESC", KernelConcept.DESCRIPTION_NOT_CASE_SENSITIVE,
                        KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE, KernelConcept.PREFERRED);
                identifier(conceptNid, rxnormUuid, RXCUI_IDENTIFIER, rxClass.rxCuiOrIri(), "RXID", stamp);
                if (!rxClass.equivalentClasses.isEmpty()) {
                    return axiom(conceptNid, rxnormUuid, rxClass.equivalentClasses, "AXIOM");
                }
                return rxClass.subClassOf.isEmpty() ? 0 : axiom(conceptNid, rxnormUuid, rxClass.subClassOf, "SUBAXIOM");
            }
            // A class merged with a SNOMED CT concept keeps SNOMED CT's fully qualified name and
            // preferred synonym; RxNorm's names join it as acceptable regular names, so one concept
            // never carries two fully qualified names or two preferred synonyms.
            boolean merged = conceptId.uuidCount() > 1;
            describe(conceptNid, rxnormUuid, rxClass.rxnormName, "DESC", KernelConcept.DESCRIPTION_NOT_CASE_SENSITIVE,
                    merged ? KernelConcept.REGULAR_NAME_DESCRIPTION_TYPE : KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE,
                    merged ? ACCEPTABLE : KernelConcept.PREFERRED);
            describe(conceptNid, rxnormUuid, rxClass.rxnormSynonym, "SDESC", KernelConcept.DESCRIPTION_NOT_CASE_SENSITIVE,
                    KernelConcept.REGULAR_NAME_DESCRIPTION_TYPE, merged ? ACCEPTABLE : KernelConcept.PREFERRED);
            describe(conceptNid, rxnormUuid, rxClass.prescribableSynonym, "PSDESC", KernelConcept.DESCRIPTION_NOT_CASE_SENSITIVE,
                    KernelConcept.REGULAR_NAME_DESCRIPTION_TYPE, ACCEPTABLE);
            identifiers(conceptNid, rxnormUuid, rxClass);
            annotations(conceptNid, rxnormUuid, rxClass);
            return rxClass.equivalentClasses.isEmpty() ? 0 : axiom(conceptNid, rxnormUuid, rxClass.equivalentClasses, "AXIOM");
        }

        private static final EntityProxy.Concept ACCEPTABLE =
                EntityProxy.Concept.make(PublicIds.of(UUID.fromString("12b9e103-060e-3256-9982-18c1191af60e")));

        /** Identity as IKM mints it: the concept's UUID, the value, and a suffix naming the kind. */
        private PublicId id(UUID conceptUuid, String value, String suffix) {
            return PublicIds.of(UuidT5Generator.get(namespace, conceptUuid + value + suffix));
        }

        private void describe(long conceptNid, UUID conceptUuid, String text, String suffix,
                              ConceptFacade caseSignificance, ConceptFacade type, ConceptFacade usAcceptability) {
            if (text.isEmpty()) {
                return;
            }
            PublicId descriptionId = id(conceptUuid, text, suffix);
            long descriptionNid = writer.semantic(stamp, descriptionId, KernelPattern.DESCRIPTION_PATTERN, conceptNid,
                    Lists.immutable.of(KernelConcept.ENGLISH_LANGUAGE, text, caseSignificance, type));
            writer.dialect(stamp, descriptionNid, UuidT5Generator.get(descriptionId.leastUuid(), "us-dialect"),
                    KernelPattern.US_DIALECT_PATTERN, usAcceptability);
        }

        private void identifiers(long conceptNid, UUID conceptUuid, RxnormClass rxClass) {
            if (!rxClass.snomedCtId.isEmpty()) {
                writer.semantic(stamp, PublicIds.of(UuidT5Generator.get(UuidUtil.SNOMED_NAMESPACE, rxClass.snomedCtId + "snomed")),
                        KernelPattern.IDENTIFIER_PATTERN, conceptNid, Lists.immutable.of(SNOMED_IDENTIFIER, rxClass.snomedCtId));
            }
            identifier(conceptNid, conceptUuid, RXCUI_IDENTIFIER, rxClass.rxCuiOrIri(), "RXID", stamp);
            identifier(conceptNid, conceptUuid, VUID_IDENTIFIER, rxClass.vuid, "VUID", stamp);
            rxClass.ndcEndDates.forEach((ndc, endDate) -> identifier(conceptNid, conceptUuid, NDC_IDENTIFIER, ndc, "NDCID",
                    endDate.compareTo(releaseMonth) < 0 ? retired : stamp));
        }

        private void identifier(long conceptNid, UUID conceptUuid, ConceptFacade source, String value, String suffix,
                                Stamp under) {
            if (!value.isEmpty()) {
                writer.semantic(under, id(conceptUuid, value, suffix), KernelPattern.IDENTIFIER_PATTERN, conceptNid,
                        Lists.immutable.of(source, value));
            }
        }

        private void annotations(long conceptNid, UUID conceptUuid, RxnormClass rxClass) {
            if (!rxClass.qualitativeDistinction.isEmpty()) {
                writer.semantic(stamp, id(conceptUuid, rxClass.qualitativeDistinction, "QD"), QUALITATIVE_DISTINCTION, conceptNid,
                        Lists.immutable.of(rxClass.qualitativeDistinction, KernelConcept.ENGLISH_LANGUAGE));
            }
            if (!rxClass.quantity.isEmpty()) {
                writer.semantic(stamp, id(conceptUuid, rxClass.quantity, "QUANTITY"), QUANTITY, conceptNid,
                        Lists.immutable.of(rxClass.quantity));
            }
            if (!rxClass.schedule.isEmpty()) {
                writer.semantic(stamp, id(conceptUuid, rxClass.schedule, "SCHEDULE"), SCHEDULE, conceptNid,
                        Lists.immutable.of(rxClass.schedule, KernelConcept.ENGLISH_LANGUAGE));
            }
            drug(conceptNid, conceptUuid, rxClass.humanDrug, "HD", HUMAN_DRUG);
            drug(conceptNid, conceptUuid, rxClass.vetDrug, "VD", VET_DRUG);
            for (String synonym : rxClass.tallmanSynonyms) {
                describe(conceptNid, conceptUuid, synonym, "TSDESC", KernelConcept.DESCRIPTION_CASE_SENSITIVE,
                        KernelConcept.REGULAR_NAME_DESCRIPTION_TYPE, ACCEPTABLE);
                writer.semantic(stamp, id(conceptUuid, synonym, "Tallman"), TALLMAN_SYNONYM, conceptNid,
                        Lists.immutable.of(KernelConcept.PREFERRED));
            }
        }

        private void drug(long conceptNid, UUID conceptUuid, String value, String suffix, PatternFacade pattern) {
            if (value.isEmpty()) {
                return;
            }
            UUID valueUuid = UuidT5Generator.get(namespace, value);
            long valueNid = writer.concept(stamp, PublicIds.of(valueUuid));
            writer.describe(stamp, valueNid, UuidT5Generator.get(valueUuid, "fqn"), value,
                    KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE);
            writer.semantic(stamp, id(conceptUuid, value, suffix), pattern, conceptNid,
                    Lists.immutable.of(EntityProxy.Concept.make(PublicIds.of(valueUuid))));
        }

        /** The concepts this import wrote OWL axioms for: the ones to transform. */
        final java.util.Set<Long> axiomConcepts = new java.util.HashSet<>();

        private long axiom(long conceptNid, UUID conceptUuid, String axiom, String suffix) {
            writer.semantic(stamp, id(conceptUuid, axiom, suffix), KernelPattern.OWL_AXIOM_SYNTAX_PATTERN, conceptNid,
                    Lists.immutable.of(RxnormClass.withPublicIds(namespace, axiom)));
            axiomConcepts.add(conceptNid);
            return 1;
        }
    }
}
