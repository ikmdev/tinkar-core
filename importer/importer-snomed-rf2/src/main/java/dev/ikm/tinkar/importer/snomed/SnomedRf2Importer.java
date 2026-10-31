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
package dev.ikm.tinkar.importer.snomed;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.time.DateTimeUtil;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import dev.ikm.tinkar.entity.builder.ActiveStamp;
import dev.ikm.tinkar.entity.builder.InactiveStamp;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.ext.lang.owl.Rf2OwlToLogicAxiomTransformer;
import dev.ikm.tinkar.importer.ImportException;
import dev.ikm.tinkar.importer.ImportReport;
import dev.ikm.tinkar.importer.ImportSource;
import dev.ikm.tinkar.importer.KnowledgeImporter;
import dev.ikm.tinkar.importer.StoreWriter;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import org.eclipse.collections.api.factory.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.LongAdder;

/**
 * Imports a SNOMED CT RF2 release: the International edition, or an extension such as the LOINC
 * Ontology (the SNOMED CT LOINC extension) or a national edition, which is imported after the
 * edition it extends. Ported from IKM's {@code snomed-ct-data} and {@code snomed-ct-loinc-data}
 * transformation plugins; the two differed only in author and identifier handling, so one
 * importer reads both.
 *
 * <p>What it writes, every row under the stamp its effective time, active flag and module say:
 * <ul>
 *   <li>concepts, each with its SCTID as an identifier semantic;</li>
 *   <li>descriptions and text definitions;</li>
 *   <li>language reference set members, as US or GB dialect semantics;</li>
 *   <li>alternate identifiers (the LOINC Ontology's LOINC codes), as identifier semantics;</li>
 *   <li>OWL axioms, as OWL axiom syntax semantics, then transformed into EL++ stated axioms the
 *   reasoner reads.</li>
 * </ul>
 * The inferred relationship file is not read: classification infers it.
 *
 * <p>Options:
 * <dl>
 *   <dt>{@code release}</dt><dd>{@code full} (the default; the whole history) or {@code snapshot}.</dd>
 *   <dt>{@code author}</dt><dd>the author concept's name, minted in the namespace (default
 *   {@code IHTSDO SNOMED CT Author}; the LOINC Ontology's is {@code IHTSDO SNOMED CT LOINC Collaboration Author}).</dd>
 *   <dt>{@code path}</dt><dd>the path concept's UUID (default the development path).</dd>
 *   <dt>{@code namespace}</dt><dd>the namespace SCTIDs are minted in (default SNOMED's).</dd>
 *   <dt>{@code rootParent}</dt><dd>a concept UUID the edition's root (138875005) is stated a kind of,
 *   placing SNOMED CT in the starter set's hierarchy; none by default.</dd>
 *   <dt>{@code owlTransform}</dt><dd>{@code false} to leave OWL axioms as syntax, untransformed.</dd>
 * </dl>
 */
public final class SnomedRf2Importer implements KnowledgeImporter {

    private static final Logger LOG = LoggerFactory.getLogger(SnomedRf2Importer.class);

    /** The concept every SNOMED CT concept descends from. */
    static final String SNOMED_ROOT = "138875005";
    /** The module the author concept is written in. */
    static final String CORE_MODULE = "900000000000207008";

    // Column positions shared by RF2 component and reference set files.
    private static final int ID = 0;
    private static final int EFFECTIVE_TIME = 1;
    private static final int ACTIVE = 2;
    private static final int MODULE_ID = 3;
    // Descriptions and text definitions.
    private static final int CONCEPT_ID = 4;
    private static final int LANGUAGE_CODE = 5;
    private static final int TYPE_ID = 6;
    private static final int TERM = 7;
    private static final int CASE_SIGNIFICANCE_ID = 8;
    // Reference sets.
    private static final int REFSET_ID = 4;
    private static final int REFERENCED_COMPONENT_ID = 5;
    private static final int ACCEPTABILITY_ID = 6;
    private static final int OWL_EXPRESSION = 6;
    // Identifiers: alternateIdentifier, effectiveTime, active, moduleId, identifierSchemeId, referencedComponentId.
    private static final int IDENTIFIER_SCHEME_ID = 4;

    @Override
    public String name() {
        return "snomed-rf2";
    }

    @Override
    public String description() {
        return "SNOMED CT RF2 release (International edition or an extension), full or snapshot";
    }

    @Override
    public ImportReport importInto(ImportSource source, StoreWriter writer) {
        source.requireExists();
        Instant start = Instant.now();
        Rf2Release.Type type = source.option("release").map(Rf2Release.Type::of).orElse(Rf2Release.Type.FULL);
        SnomedIds ids = new SnomedIds(source.uuidOption("namespace").orElse(UuidUtil.SNOMED_NAMESPACE));
        String authorName = source.option("author").orElse("IHTSDO SNOMED CT Author");
        ConceptFacade author = EntityProxy.Concept.make(authorName, UuidT5Generator.get(ids.namespace(), authorName));
        ConceptFacade path = source.uuidOption("path").<ConceptFacade>map(uuid -> EntityProxy.Concept.make(PublicIds.of(uuid)))
                .orElse(KernelConcept.DEVELOPMENT_PATH);
        Map<String, Long> details = new LinkedHashMap<>();
        StoreWriter.Tally before = writer.counts().tally();

        try (Rf2Release release = Rf2Release.open(source.path(), type)) {
            String version = release.version();
            LOG.info("Importing SNOMED CT {} ({}) from {}", version, type, source.path());
            Stamps stamps = new Stamps(ids, author, path);

            writeAuthor(writer, stamps, ids, author, release);
            for (Rf2Release.File file : release.files(Rf2Release.Kind.CONCEPT)) {
                details.put(file.name(), release.rows(file, row -> concept(writer, stamps, ids, row)));
            }
            for (Rf2Release.Kind kind : new Rf2Release.Kind[]{Rf2Release.Kind.DESCRIPTION, Rf2Release.Kind.TEXT_DEFINITION}) {
                for (Rf2Release.File file : release.files(kind)) {
                    details.put(file.name(), release.rows(file, row -> description(writer, stamps, ids, row)));
                }
            }
            LongAdder skippedLanguage = new LongAdder();
            for (Rf2Release.File file : release.files(Rf2Release.Kind.LANGUAGE)) {
                details.put(file.name(), release.rows(file, row -> language(writer, stamps, ids, row, skippedLanguage)));
            }
            for (Rf2Release.File file : release.files(Rf2Release.Kind.IDENTIFIER)) {
                details.put(file.name(), release.rows(file, row -> identifier(writer, stamps, ids, row)));
            }
            LongAdder axioms = new LongAdder();
            java.util.Set<Long> axiomConcepts = java.util.concurrent.ConcurrentHashMap.newKeySet();
            for (Rf2Release.File file : release.files(Rf2Release.Kind.OWL_EXPRESSION)) {
                details.put(file.name(), release.rows(file, row -> owlAxiom(writer, stamps, ids, row, axioms, axiomConcepts)));
            }
            details.put("language rows skipped (no kernel dialect pattern)", skippedLanguage.sum());
            details.put("OWL axioms", axioms.sum());

            source.uuidOption("rootParent").ifPresent(parent -> {
                writer.statedParent(stamps.authorStamp(), ids.publicId(SNOMED_ROOT), PublicIds.of(parent));
                details.put("root stated under " + parent, 1L);
            });

            if (source.option("owlTransform").map(Boolean::parseBoolean).orElse(true) && axioms.sum() > 0) {
                transformOwl(stamps, ids, axiomConcepts);
            }
            ImportReport report = ImportReport.of(name(), version, writer.counts().tally().minus(before), details,
                    Duration.between(start, Instant.now()));
            return release.releaseDate().map(report::withReleaseDate).orElse(report);
        }
    }

    private static void writeAuthor(StoreWriter writer, Stamps stamps, SnomedIds ids, ConceptFacade author,
                                    Rf2Release release) {
        Stamp stamp = stamps.authorStamp();
        long authorNid = writer.concept(stamp, author.publicId());
        UUID authorUuid = author.publicId().leastUuid();
        writer.describe(stamp, authorNid, UuidT5Generator.get(authorUuid, "fqn"), author.description(),
                KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE);
        writer.describe(stamp, authorNid, UuidT5Generator.get(authorUuid, "regular name"),
                author.description().replace("IHTSDO ", ""), KernelConcept.REGULAR_NAME_DESCRIPTION_TYPE);
        writer.statedParent(stamp, author.publicId(), KernelConcept.USER.publicId());
    }

    private static void concept(StoreWriter writer, Stamps stamps, SnomedIds ids, String[] row) {
        PublicId conceptId = ids.publicId(row[ID]);
        writer.recordConcept(stamps.of(row), conceptId);
        writer.semantic(stamps.activeAt(row), sctidSemantic(row[ID]), KernelPattern.IDENTIFIER_PATTERN,
                PrimitiveData.nid(conceptId), Lists.immutable.of(KernelConcept.SCTID, row[ID]));
    }

    /**
     * The identity of the semantic carrying a component's SCTID, as IKM's pipeline mints it, so
     * the two agree.
     */
    private static PublicId sctidSemantic(String sctid) {
        return PublicIds.of(UuidT5Generator.singleSemanticUuid(KernelPattern.IDENTIFIER_PATTERN.publicId(),
                PublicIds.of(UuidT5Generator.get(sctid))));
    }

    private static void description(StoreWriter writer, Stamps stamps, SnomedIds ids, String[] row) {
        ConceptFacade language = SnomedIds.language(row[LANGUAGE_CODE])
                .orElseThrow(() -> new ImportException("No kernel language concept for RF2 language code "
                        + row[LANGUAGE_CODE] + " (description " + row[ID] + ")"));
        writer.recordSemantic(stamps.of(row), ids.publicId(row[ID]), KernelPattern.DESCRIPTION_PATTERN,
                PrimitiveData.nid(ids.publicId(row[CONCEPT_ID])),
                Lists.immutable.of(language, row[TERM], ids.concept(row[CASE_SIGNIFICANCE_ID]), ids.concept(row[TYPE_ID])));
    }

    private static void language(StoreWriter writer, Stamps stamps, SnomedIds ids, String[] row, LongAdder skipped) {
        SnomedIds.dialect(row[REFSET_ID]).ifPresentOrElse(
                dialect -> writer.recordSemantic(stamps.of(row), PublicIds.of(UUID.fromString(row[ID])), dialect,
                        PrimitiveData.nid(ids.publicId(row[REFERENCED_COMPONENT_ID])),
                        Lists.immutable.of(ids.concept(row[ACCEPTABILITY_ID]))),
                skipped::increment);
    }

    private static void identifier(StoreWriter writer, Stamps stamps, SnomedIds ids, String[] row) {
        String scheme = row[IDENTIFIER_SCHEME_ID];
        PublicId semanticId = PublicIds.of(UuidT5Generator.get(ids.namespace(), "identifier " + scheme + " " + row[ID]));
        writer.recordSemantic(stamps.of(row), semanticId, KernelPattern.IDENTIFIER_PATTERN,
                PrimitiveData.nid(ids.publicId(row[REFERENCED_COMPONENT_ID])),
                Lists.immutable.of(ids.concept(scheme), row[ID]));
    }

    private static void owlAxiom(StoreWriter writer, Stamps stamps, SnomedIds ids, String[] row, LongAdder axioms,
                                 java.util.Set<Long> axiomConcepts) {
        if (!SnomedIds.OWL_AXIOM_REFSET.equals(row[REFSET_ID])) {
            return; // the OWL ontology reference set: the ontology's header, not an axiom
        }
        long conceptNid = PrimitiveData.nid(ids.publicId(row[REFERENCED_COMPONENT_ID]));
        writer.recordSemantic(stamps.of(row), PublicIds.of(UUID.fromString(row[ID])), KernelPattern.OWL_AXIOM_SYNTAX_PATTERN,
                conceptNid, Lists.immutable.of(ids.owlWithPublicIds(row[OWL_EXPRESSION])));
        axiomConcepts.add(conceptNid);
        axioms.increment();
    }

    /**
     * Transforms the axioms of the concepts this import wrote axioms for, and no others, and of
     * those only the OWL versions this import wrote: an extension's axiom on a concept of the
     * edition beneath leaves the edition's history, already transformed by the edition's own
     * import, as it is, rather than restating it under the extension's author at the edition's
     * own times.
     */
    private static void transformOwl(Stamps stamps, SnomedIds ids, java.util.Set<Long> axiomConcepts) {
        LOG.info("Transforming OWL axioms of {} concepts into EL++ stated axioms", axiomConcepts.size());
        long authorNid = stamps.author().nid();
        try {
            new Rf2OwlToLogicAxiomTransformer(new Transaction("SNOMED CT OWL to logic"),
                    KernelPattern.OWL_AXIOM_SYNTAX_PATTERN, KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN,
                    authorNid, PrimitiveData.nid(ids.publicId(CORE_MODULE)), stamps.path().nid(),
                    axiomConcepts::contains, owlStamp -> owlStamp.authorNid() == authorNid).call();
        } catch (Exception e) {
            throw new ImportException("Transforming SNOMED CT OWL axioms failed", e);
        }
    }

    /** Stamps by effective time, state and module, made once each. */
    static final class Stamps {
        private final SnomedIds ids;
        private final ConceptFacade author;
        private final ConceptFacade path;
        private final Map<String, Stamp> made = new HashMap<>();

        Stamps(SnomedIds ids, ConceptFacade author, ConceptFacade path) {
            this.ids = ids;
            this.author = author;
            this.path = path;
        }

        ConceptFacade author() {
            return author;
        }

        ConceptFacade path() {
            return path;
        }

        /** The stamp a row's effective time, active flag and module say. */
        Stamp of(String[] row) {
            return make("1".equals(row[ACTIVE]), row[EFFECTIVE_TIME], row[MODULE_ID]);
        }

        /** An active stamp at a row's effective time and module, whatever its active flag. */
        Stamp activeAt(String[] row) {
            return make(true, row[EFFECTIVE_TIME], row[MODULE_ID]);
        }

        /** The stamp the author concept is written under: the core module at SNOMED CT's first release. */
        Stamp authorStamp() {
            return make(true, "20020131", CORE_MODULE);
        }

        private Stamp make(boolean active, String effectiveTime, String module) {
            return made.computeIfAbsent((active ? "A" : "I") + effectiveTime + module, key -> {
                long time = DateTimeUtil.compressedDateParse(effectiveTime);
                ConceptFacade moduleConcept = ids.concept(module);
                return active ? new ActiveStamp(time, author, moduleConcept, path)
                        : new InactiveStamp(time, author, moduleConcept, path);
            });
        }
    }
}
