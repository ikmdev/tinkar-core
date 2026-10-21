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

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.builder.ActiveStamp;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpressionBuilder;
import dev.ikm.tinkar.importer.ImportException;
import dev.ikm.tinkar.importer.StoreWriter;
import dev.ikm.tinkar.importer.StoreWriter.FieldDefinition;
import dev.ikm.tinkar.importer.ncbi.GeneReport.Annotation;
import dev.ikm.tinkar.importer.ncbi.GeneReport.GenomicLocation;
import dev.ikm.tinkar.importer.ncbi.NcbiGeneTerms.Shared;
import dev.ikm.tinkar.importer.ncbi.NcbiGeneTerms.SharedPattern;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import dev.ikm.tinkar.terms.PatternFacade;
import org.eclipse.collections.api.factory.Lists;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Writes gene reports into the store, one gene at a time, through the {@link StoreWriter}.
 *
 * <p>Before the first gene, the importer's own concepts ({@link Shared}): its module under the
 * starter set's Module, its author under Author, its root under the root vertex, the Gene,
 * Organism and Strand roots under it, the plus, minus and unstated strands under Strand, the organism role
 * under Role, the three identifier sources under Identifier Source, and under Model concept the
 * concepts that give the genomic location pattern and its fields a meaning and a purpose. Then
 * the pattern itself ({@link SharedPattern#GENOMIC_LOCATION}). Each is named by a fully qualified
 * and a regular name.
 *
 * <p>Per organism, the first time a gene names it: a concept under Organism, named by NCBI
 * Taxonomy's name, identified by its NCBI Taxonomy ID. Per gene: a concept, named in full by
 * its symbol, the RefSeq sequence it is annotated on and its organism
 * ({@code N gene on NC_045512.2 (Severe acute respiratory syndrome coronavirus 2)}: the sequence
 * tells apart the same gene of two reference genomes NCBI files under one taxon, as RSV's), by
 * its symbol (preferred) and by NCBI's description (acceptable), defined by NCBI's summary when
 * it has one; identified by its NCBI Gene ID and by each RefSeq genomic accession.version its
 * annotations place it on; placed by one genomic location semantic per range its annotations
 * state (sequence, first and last position, strand); and stated a kind of Gene with the organism
 * as the filler of the organism role.
 *
 * <p>Every version is written under one stamp: the release date, NCBI's author and module, the
 * import's path.
 */
final class GeneWriter {

    private static final Logger LOG = LoggerFactory.getLogger(GeneWriter.class);

    private final StoreWriter writer;
    private final UUID namespace;
    private final Stamp stamp;
    private final ConceptFacade geneRoot;
    private final ConceptFacade organismRole;
    private final PatternFacade genomicLocation;
    private final Map<String, Long> organisms = new HashMap<>();
    private final Map<String, Long> details = new LinkedHashMap<>();
    private long genes;
    private long names;
    private long definitions;
    private long geneIdentifiers;
    private long refSeqIdentifiers;
    private long genomicLocations;
    private boolean sharedWritten;

    /**
     * A writer for one import.
     *
     * @param writer      the store writer every write goes through
     * @param namespace   the namespace identities are minted in
     * @param path        the path every stamp is on
     * @param releaseTime the release date, epoch milliseconds: the time of every stamp
     */
    GeneWriter(StoreWriter writer, UUID namespace, ConceptFacade path, long releaseTime) {
        this.writer = writer;
        this.namespace = namespace;
        this.stamp = new ActiveStamp(releaseTime, Shared.AUTHOR.concept(namespace), Shared.MODULE.concept(namespace), path);
        this.geneRoot = Shared.GENE.concept(namespace);
        this.organismRole = Shared.ORGANISM_ROLE.concept(namespace);
        this.genomicLocation = SharedPattern.GENOMIC_LOCATION.pattern(namespace);
    }

    /**
     * What was written, in the order reported.
     *
     * @return the counts
     */
    Map<String, Long> details() {
        details.put("shared concepts", (long) Shared.values().length);
        details.put("organisms", (long) organisms.size());
        details.put("genes", genes);
        details.put("gene names", names);
        details.put("gene definitions", definitions);
        details.put("NCBI Gene identifiers", geneIdentifiers);
        details.put("RefSeq accession identifiers", refSeqIdentifiers);
        details.put("genomic locations", genomicLocations);
        return details;
    }

    /**
     * Writes the importer's own concepts and then its pattern, once.
     */
    void shared() {
        if (sharedWritten) {
            return;
        }
        sharedWritten = true;
        for (Shared shared : Shared.values()) {
            UUID uuid = shared.uuid(namespace);
            long nid = writer.concept(stamp, PublicIds.of(uuid));
            describe(nid, uuid, shared.fullyQualifiedName(), shared.regularName());
            writer.statedParent(stamp, PublicIds.of(uuid), shared.parent(namespace));
        }
        UUID patternUuid = SharedPattern.GENOMIC_LOCATION.uuid(namespace);
        long patternNid = writer.pattern(stamp, PublicIds.of(patternUuid),
                Shared.LOCATED_GENE.concept(namespace), Shared.GENOMIC_PLACEMENT.concept(namespace), List.of(
                        new FieldDefinition(Shared.REFSEQ_SEQUENCE.concept(namespace),
                                Shared.SEQUENCE_IDENTITY.concept(namespace), KernelConcept.STRING),
                        new FieldDefinition(Shared.RANGE_BEGIN.concept(namespace),
                                Shared.LOWER_BOUND.concept(namespace), KernelConcept.INTEGER_FIELD),
                        new FieldDefinition(Shared.RANGE_END.concept(namespace),
                                Shared.UPPER_BOUND.concept(namespace), KernelConcept.INTEGER_FIELD),
                        new FieldDefinition(Shared.STRAND.concept(namespace),
                                Shared.READING_DIRECTION.concept(namespace), KernelConcept.COMPONENT_FIELD)));
        describe(patternNid, patternUuid, SharedPattern.GENOMIC_LOCATION.fullyQualifiedName(),
                SharedPattern.GENOMIC_LOCATION.regularName());
    }

    /**
     * Writes a gene, and its organism the first time it is named.
     *
     * @param gene the report
     */
    void gene(GeneReport gene) {
        shared();
        long organismNid = organisms.computeIfAbsent(gene.taxId(), taxId -> organism(taxId, gene.taxname()));
        UUID uuid = NcbiGeneTerms.geneUuid(namespace, gene.geneId());
        PublicId conceptId = PublicIds.of(uuid);
        long nid = writer.concept(stamp, conceptId);
        writer.describe(stamp, nid, UuidT5Generator.get(uuid, "fqn"), fullyQualifiedName(gene),
                KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE);
        writer.describe(stamp, nid, UuidT5Generator.get(uuid, "name"), gene.symbol(),
                KernelConcept.REGULAR_NAME_DESCRIPTION_TYPE);
        names++;
        if (!gene.description().isBlank() && !gene.description().equals(gene.symbol())) {
            writer.describe(stamp, nid, UuidT5Generator.get(uuid, "description"), gene.description(),
                    KernelConcept.REGULAR_NAME_DESCRIPTION_TYPE, NcbiGeneTerms.ACCEPTABLE);
            names++;
        }
        if (gene.summary().isPresent()) {
            writer.describe(stamp, nid, UuidT5Generator.get(uuid, "definition"), gene.summary().get(),
                    KernelConcept.DEFINITION_DESCRIPTION_TYPE);
            definitions++;
        }
        writer.semantic(stamp, PublicIds.of(UuidT5Generator.get(uuid, "ncbi gene id")), KernelPattern.IDENTIFIER_PATTERN,
                nid, Lists.immutable.of(Shared.GENE_ID.concept(namespace), gene.geneId()));
        geneIdentifiers++;
        for (String accession : gene.refSeqAccessions()) {
            writer.semantic(stamp, PublicIds.of(NcbiGeneTerms.refSeqAccessionUuid(uuid, accession)),
                    KernelPattern.IDENTIFIER_PATTERN, nid,
                    Lists.immutable.of(Shared.REFSEQ_ACCESSION.concept(namespace), accession));
            refSeqIdentifiers++;
        }
        for (Annotation annotation : gene.annotations()) {
            for (GenomicLocation location : annotation.genomicLocations()) {
                genomicLocation(nid, uuid, gene.geneId(), location);
            }
        }
        statedDefinition(nid, uuid, EntityProxy.Concept.make(organismNid));
        if (++genes % 100 == 0) {
            LOG.info("genes: {}", genes);
        }
    }

    /**
     * A gene's fully qualified name: its symbol, the RefSeq sequences it is annotated on, and its
     * organism as the semantic tag.
     *
     * @param gene the report
     * @return the name
     */
    static String fullyQualifiedName(GeneReport gene) {
        StringBuilder name = new StringBuilder(gene.symbol()).append(" gene");
        if (!gene.refSeqAccessions().isEmpty()) {
            name.append(" on ").append(String.join(", ", gene.refSeqAccessions()));
        }
        return name.append(" (").append(gene.taxname()).append(')').toString();
    }

    /**
     * Places a gene on a sequence: one genomic location semantic about the gene, its strand the
     * one the report states, or the unstated strand when it states none.
     *
     * @throws ImportException when a position exceeds the integer field
     */
    private void genomicLocation(long geneNid, UUID geneUuid, String geneId, GenomicLocation location) {
        UUID uuid = NcbiGeneTerms.genomicLocationUuid(geneUuid, location.accessionVersion(), location.begin(),
                location.end());
        Shared strand = location.orientation().map(orientation -> switch (orientation) {
            case PLUS -> Shared.PLUS_STRAND;
            case MINUS -> Shared.MINUS_STRAND;
        }).orElse(Shared.UNSTATED_STRAND);
        writer.semantic(stamp, PublicIds.of(uuid), genomicLocation, geneNid, Lists.immutable.of(
                location.accessionVersion(),
                position(geneId, location, location.begin()),
                position(geneId, location, location.end()),
                strand.concept(namespace)));
        genomicLocations++;
    }

    /**
     * A one-based position as the integer field holds it.
     *
     * @throws ImportException when the position exceeds the integer field
     */
    private static int position(String geneId, GenomicLocation location, long position) {
        if (position > Integer.MAX_VALUE) {
            throw new ImportException("Gene " + geneId + " lies beyond the integer field's range on "
                    + location.accessionVersion() + ": " + position);
        }
        return (int) position;
    }

    private long organism(String taxId, String taxname) {
        UUID uuid = NcbiGeneTerms.organismUuid(namespace, taxId);
        long nid = writer.concept(stamp, PublicIds.of(uuid));
        describe(nid, uuid, taxname + " (NCBI organism)", taxname);
        writer.semantic(stamp, PublicIds.of(UuidT5Generator.get(uuid, "ncbi taxonomy id")), KernelPattern.IDENTIFIER_PATTERN,
                nid, Lists.immutable.of(Shared.TAXONOMY_ID.concept(namespace), taxId));
        writer.statedParent(stamp, PublicIds.of(uuid), PublicIds.of(Shared.ORGANISM.uuid(namespace)));
        return nid;
    }

    /** A gene's stated definition: a kind of Gene, with its organism as the organism role's filler. */
    private void statedDefinition(long conceptNid, UUID conceptUuid, ConceptFacade organism) {
        UUID axiomUuid = UuidT5Generator.get(conceptUuid, "stated axioms");
        int[] ordinal = {0};
        LogicalExpressionBuilder builder = new LogicalExpressionBuilder(
                UuidT5Generator.get(axiomUuid, "definition root"),
                () -> UuidT5Generator.get(axiomUuid, "axiom vertex " + ordinal[0]++));
        builder.NecessarySet(builder.And(
                builder.ConceptAxiom(geneRoot),
                builder.SomeRole(organismRole, builder.ConceptAxiom(organism))));
        DiTreeEntity tree = (DiTreeEntity) builder.build().sourceGraph();
        writer.semantic(stamp, PublicIds.of(axiomUuid), KernelPattern.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN, conceptNid,
                Lists.immutable.of(tree));
    }

    private void describe(long nid, UUID uuid, String fullyQualifiedName, String regularName) {
        writer.describe(stamp, nid, UuidT5Generator.get(uuid, "fqn"), fullyQualifiedName,
                KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE);
        writer.describe(stamp, nid, UuidT5Generator.get(uuid, "name"), regularName,
                KernelConcept.REGULAR_NAME_DESCRIPTION_TYPE);
    }
}
