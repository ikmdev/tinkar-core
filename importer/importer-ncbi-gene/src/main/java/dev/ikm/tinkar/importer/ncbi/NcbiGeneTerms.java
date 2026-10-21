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
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.PatternFacade;

import java.util.UUID;

/**
 * The identities the NCBI Gene importer mints, and the starter-set concepts it states them
 * under. Every identity is a type 5 UUID: the importer's own concepts and pattern (its module,
 * author, roots, strands, and the genomic location pattern with the concepts that give it and its
 * fields a meaning and a purpose) from the namespace and a {@code shared|} key, a gene from the
 * namespace and its NCBI Gene ID, an organism from the namespace and its NCBI Taxonomy ID, and
 * each of their semantics from the concept's UUID and a word naming the semantic. So a second
 * import of the same origin writes nothing new, and two origins agree on every gene they share.
 */
public final class NcbiGeneTerms {

    /**
     * The default namespace: {@code T5(NameSpace_URL, "https://www.ncbi.nlm.nih.gov/gene")}, the
     * NCBI Gene resource's URL in the URL namespace.
     */
    public static final String DEFAULT_NAMESPACE = "a4f5b128-afb6-5fa2-931b-2099bd605d6e";

    /** The starter set's Module (SOLOR), every module's parent. */
    public static final EntityProxy.Concept MODULE_SOLOR =
            EntityProxy.Concept.make("Module (SOLOR)", PublicIds.of("40d1c869-b509-32f8-b735-836eac577a67"));

    /** The starter set's Acceptable (SOLOR), the acceptability of a name that is not preferred. */
    public static final EntityProxy.Concept ACCEPTABLE =
            EntityProxy.Concept.make("Acceptable (SOLOR)", PublicIds.of("12b9e103-060e-3256-9982-18c1191af60e"));

    /**
     * The starter set's Model concept (SOLOR), what the concepts giving a pattern and its fields
     * their meaning and purpose are stated under.
     */
    public static final EntityProxy.Concept MODEL_CONCEPT =
            EntityProxy.Concept.make("Model concept (SOLOR)", PublicIds.of("7bbd4210-381c-11e7-9598-0800200c9a66"));

    /**
     * The importer's own concepts, each minted from the import's namespace and its key, and
     * stated under a starter-set concept or another of these.
     */
    public enum Shared {
        /** The module every version is written in. */
        MODULE("module", "NCBI Gene module (NCBI)", "NCBI Gene module"),
        /** The author of every version: NCBI, whose data this is. */
        AUTHOR("author", "National Center for Biotechnology Information (NCBI)", "NCBI"),
        /** The root the genes and organisms are stated under. */
        ROOT("root", "NCBI Gene (NCBI)", "NCBI Gene"),
        /** What every gene is a kind of. */
        GENE("gene", "Gene (NCBI)", "Gene"),
        /** What every organism is a kind of. */
        ORGANISM("organism", "Organism (NCBI)", "Organism"),
        /** The role relating a gene to the organism it is annotated on. */
        ORGANISM_ROLE("organism role", "Organism of gene (NCBI)", "Organism of gene"),
        /** Identifier source of an NCBI Gene ID. */
        GENE_ID("ncbi gene id", "NCBI Gene ID (NCBI)", "NCBI Gene ID"),
        /** Identifier source of an NCBI Taxonomy ID. */
        TAXONOMY_ID("ncbi taxonomy id", "NCBI Taxonomy ID (NCBI)", "NCBI Taxonomy ID"),
        /** Identifier source of a RefSeq genomic accession and version. */
        REFSEQ_ACCESSION("refseq accession", "RefSeq accession (NCBI)", "RefSeq accession"),
        /** A strand of a sequence: what the strands, and the unstated strand, are stated under. */
        STRAND("strand", "Strand (NCBI)", "Strand"),
        /** The forward strand: NCBI's {@code plus}. */
        PLUS_STRAND("plus strand", "Plus strand (NCBI)", "Plus strand"),
        /** The reverse strand: NCBI's {@code minus}. */
        MINUS_STRAND("minus strand", "Minus strand (NCBI)", "Minus strand"),
        /** The strand of a genomic location whose report states no orientation. */
        UNSTATED_STRAND("unstated strand", "Unstated strand (NCBI)", "Unstated strand"),
        /** What a genomic location semantic's referenced component is: the gene placed. */
        LOCATED_GENE("located gene", "Located gene (NCBI)", "Located gene"),
        /** Why a genomic location semantic exists: to place a gene on a RefSeq sequence. */
        GENOMIC_PLACEMENT("genomic placement", "Genomic placement (NCBI)", "Genomic placement"),
        /** What a genomic location's first field is: a RefSeq genomic accession and version. */
        REFSEQ_SEQUENCE("refseq sequence", "RefSeq sequence (NCBI)", "RefSeq sequence"),
        /** Why the sequence is recorded: to say which RefSeq sequence the range lies on. */
        SEQUENCE_IDENTITY("sequence identity", "Sequence identity (NCBI)", "Sequence identity"),
        /** What a genomic location's second field is: the range's first position, one-based. */
        RANGE_BEGIN("range begin", "Range begin (NCBI)", "Range begin"),
        /** Why the first position is recorded: to bound the range from below. */
        LOWER_BOUND("lower bound", "Lower bound (NCBI)", "Lower bound"),
        /** What a genomic location's third field is: the range's last position, one-based, inclusive. */
        RANGE_END("range end", "Range end (NCBI)", "Range end"),
        /** Why the last position is recorded: to bound the range from above. */
        UPPER_BOUND("upper bound", "Upper bound (NCBI)", "Upper bound"),
        /** Why the strand is recorded: to say which strand the gene is read from. */
        READING_DIRECTION("reading direction", "Reading direction (NCBI)", "Reading direction");

        private final String key;
        private final String fullyQualifiedName;
        private final String regularName;

        Shared(String key, String fullyQualifiedName, String regularName) {
            this.key = key;
            this.fullyQualifiedName = fullyQualifiedName;
            this.regularName = regularName;
        }

        /**
         * The concept's UUID in a namespace.
         *
         * @param namespace the import's namespace
         * @return the UUID
         */
        public UUID uuid(UUID namespace) {
            return UuidT5Generator.get(namespace, "shared|" + key);
        }

        /**
         * The concept, as a proxy, in a namespace.
         *
         * @param namespace the import's namespace
         * @return the proxy
         */
        public ConceptFacade concept(UUID namespace) {
            return EntityProxy.Concept.make(fullyQualifiedName, PublicIds.of(uuid(namespace)));
        }

        /**
         * The concept's fully qualified name.
         *
         * @return the name
         */
        public String fullyQualifiedName() {
            return fullyQualifiedName;
        }

        /**
         * The concept's regular name.
         *
         * @return the name
         */
        public String regularName() {
            return regularName;
        }

        /**
         * The concept this one is stated under.
         *
         * @param namespace the import's namespace
         * @return the parent's public id
         */
        public PublicId parent(UUID namespace) {
            return switch (this) {
                case MODULE -> MODULE_SOLOR.publicId();
                case AUTHOR -> KernelConcept.USER.publicId();
                case ROOT -> KernelConcept.ROOT_VERTEX.publicId();
                case GENE, ORGANISM, STRAND -> PublicIds.of(ROOT.uuid(namespace));
                case PLUS_STRAND, MINUS_STRAND, UNSTATED_STRAND -> PublicIds.of(STRAND.uuid(namespace));
                case ORGANISM_ROLE -> KernelConcept.ROLE.publicId();
                case GENE_ID, TAXONOMY_ID, REFSEQ_ACCESSION -> KernelConcept.IDENTIFIER_SOURCE.publicId();
                case LOCATED_GENE, GENOMIC_PLACEMENT, REFSEQ_SEQUENCE, SEQUENCE_IDENTITY, RANGE_BEGIN, LOWER_BOUND,
                     RANGE_END, UPPER_BOUND, READING_DIRECTION -> MODEL_CONCEPT.publicId();
            };
        }
    }

    /**
     * The importer's own patterns, each minted from the import's namespace and its key like a
     * {@link Shared} concept.
     */
    public enum SharedPattern {
        /**
         * Where a gene lies on a RefSeq genomic sequence: the sequence's accession and version
         * (string), the first and last one-based positions (integers, the last inclusive), and the
         * strand (a component: {@link Shared#PLUS_STRAND}, {@link Shared#MINUS_STRAND}, or
         * {@link Shared#UNSTATED_STRAND} when the report states none). One semantic per location,
         * about the gene.
         */
        GENOMIC_LOCATION("genomic location", "Genomic location (NCBI)", "Genomic location");

        private final String key;
        private final String fullyQualifiedName;
        private final String regularName;

        SharedPattern(String key, String fullyQualifiedName, String regularName) {
            this.key = key;
            this.fullyQualifiedName = fullyQualifiedName;
            this.regularName = regularName;
        }

        /**
         * The pattern's UUID in a namespace.
         *
         * @param namespace the import's namespace
         * @return the UUID
         */
        public UUID uuid(UUID namespace) {
            return UuidT5Generator.get(namespace, "shared|" + key);
        }

        /**
         * The pattern, as a proxy, in a namespace.
         *
         * @param namespace the import's namespace
         * @return the proxy
         */
        public PatternFacade pattern(UUID namespace) {
            return EntityProxy.Pattern.make(fullyQualifiedName, PublicIds.of(uuid(namespace)));
        }

        /**
         * The pattern's fully qualified name.
         *
         * @return the name
         */
        public String fullyQualifiedName() {
            return fullyQualifiedName;
        }

        /**
         * The pattern's regular name.
         *
         * @return the name
         */
        public String regularName() {
            return regularName;
        }
    }

    private NcbiGeneTerms() {
    }

    /**
     * A gene's concept UUID: {@code T5(namespace, "gene|" + geneId)}.
     *
     * @param namespace the import's namespace
     * @param geneId    the NCBI Gene ID
     * @return the UUID
     */
    public static UUID geneUuid(UUID namespace, String geneId) {
        return UuidT5Generator.get(namespace, "gene|" + geneId);
    }

    /**
     * An organism's concept UUID: {@code T5(namespace, "taxon|" + taxId)}.
     *
     * @param namespace the import's namespace
     * @param taxId     the NCBI Taxonomy ID
     * @return the UUID
     */
    public static UUID organismUuid(UUID namespace, String taxId) {
        return UuidT5Generator.get(namespace, "taxon|" + taxId);
    }

    /**
     * A gene's RefSeq accession identifier semantic:
     * {@code T5(geneUuid, "refseq accession|" + accessionVersion)}.
     *
     * @param geneUuid         the gene's UUID
     * @param accessionVersion the accession and version
     * @return the UUID
     */
    public static UUID refSeqAccessionUuid(UUID geneUuid, String accessionVersion) {
        return UuidT5Generator.get(geneUuid, "refseq accession|" + accessionVersion);
    }

    /**
     * A gene's genomic location semantic:
     * {@code T5(geneUuid, "genomic location|" + accessionVersion + "|" + begin + "|" + end)}.
     *
     * @param geneUuid         the gene's UUID
     * @param accessionVersion the sequence's accession and version
     * @param begin            the range's first position, one-based
     * @param end              the range's last position, one-based, inclusive
     * @return the UUID
     */
    public static UUID genomicLocationUuid(UUID geneUuid, String accessionVersion, long begin, long end) {
        return UuidT5Generator.get(geneUuid, "genomic location|" + accessionVersion + "|" + begin + "|" + end);
    }
}
