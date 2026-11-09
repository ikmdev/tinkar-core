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

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.KernelConcept;

import java.util.Optional;
import java.util.UUID;

/**
 * The identities the GSRS importer mints, and the starter-set concepts it states them under.
 * Every identity is a type 5 UUID: the importer's own concepts (its module, author, roots, the
 * substance classes, and its identifier sources) from the namespace and a {@code shared|} key, a
 * substance from the namespace and the UUID GSRS gives it, and each of a substance's semantics
 * from the substance's UUID and a word naming the semantic. So a second import of the same origin
 * writes nothing new, and two releases agree on every substance they share: GSRS keeps a
 * substance's UUID from release to release.
 */
public final class GsrsTerms {

    /**
     * The default namespace: {@code T5(NameSpace_URL, "https://gsrs.ncats.nih.gov/")}, the GSRS
     * site's URL in the URL namespace.
     */
    public static final String DEFAULT_NAMESPACE = "8fc37ff0-4a95-5c5b-80c5-e7c03c29b39c";

    /** The starter set's Module (SOLOR), every module's parent. */
    public static final EntityProxy.Concept MODULE_SOLOR =
            EntityProxy.Concept.make("Module (SOLOR)", PublicIds.of("40d1c869-b509-32f8-b735-836eac577a67"));

    /**
     * The importer's own concepts, each minted from the import's namespace and its key, and
     * stated under a starter-set concept or another of these.
     */
    public enum Shared {
        /** The module every version is written in. */
        MODULE("module", "GSRS module (GSRS)", "GSRS module"),
        /** The author of every version: the FDA's substance registration system, whose data this is. */
        AUTHOR("author", "FDA Global Substance Registration System (GSRS)", "GSRS"),
        /** The root the substances are stated under. */
        ROOT("root", "Global Substance Registration System (GSRS)", "GSRS"),
        /** What every substance is a kind of, through its class. */
        SUBSTANCE("substance", "Substance (GSRS)", "Substance"),
        /** GSRS's {@code chemical}: a substance defined by a structure. */
        CHEMICAL("chemical", "Chemical substance (GSRS)", "Chemical substance"),
        /** GSRS's {@code protein}: a substance defined by its subunits' sequences. */
        PROTEIN("protein", "Protein substance (GSRS)", "Protein substance"),
        /** GSRS's {@code nucleicAcid}: a substance defined by its subunits' sequences. */
        NUCLEIC_ACID("nucleic acid", "Nucleic acid substance (GSRS)", "Nucleic acid substance"),
        /** GSRS's {@code polymer}: a substance defined by its monomers and structural repeating units. */
        POLYMER("polymer", "Polymer substance (GSRS)", "Polymer substance"),
        /** GSRS's {@code mixture}: a substance defined by its components. */
        MIXTURE("mixture", "Mixture substance (GSRS)", "Mixture substance"),
        /** GSRS's {@code structurallyDiverse}: a substance defined by its source material, as an extract. */
        STRUCTURALLY_DIVERSE("structurally diverse", "Structurally diverse substance (GSRS)",
                "Structurally diverse substance"),
        /** GSRS's {@code specifiedSubstanceG1}: a group 1 specified substance, defined by its constituents. */
        SPECIFIED_SUBSTANCE_G1("specified substance group 1", "Specified substance group 1 (GSRS)",
                "Specified substance group 1"),
        /** GSRS's {@code concept}: a substance named but not yet defined. */
        CONCEPT("concept", "Substance concept (GSRS)", "Substance concept"),
        /** Identifier source of a UNII, the identifier the FDA gives an approved substance. */
        UNII("unii", "Unique Ingredient Identifier (GSRS)", "UNII"),
        /** Identifier source of the UUID GSRS gives every substance, approved or not. */
        GSRS_UUID("gsrs uuid", "GSRS substance UUID (GSRS)", "GSRS substance UUID");

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
                case SUBSTANCE -> PublicIds.of(ROOT.uuid(namespace));
                case CHEMICAL, PROTEIN, NUCLEIC_ACID, POLYMER, MIXTURE, STRUCTURALLY_DIVERSE, SPECIFIED_SUBSTANCE_G1,
                     CONCEPT -> PublicIds.of(SUBSTANCE.uuid(namespace));
                case UNII, GSRS_UUID -> KernelConcept.IDENTIFIER_SOURCE.publicId();
            };
        }

        /**
         * The class concept for a substance class as GSRS names it ({@code chemical},
         * {@code nucleicAcid}, ...).
         *
         * @param substanceClass the record's {@code substanceClass}
         * @return the class concept, if GSRS's name is one this importer knows
         */
        public static Optional<Shared> ofSubstanceClass(String substanceClass) {
            return Optional.ofNullable(switch (substanceClass) {
                case "chemical" -> CHEMICAL;
                case "protein" -> PROTEIN;
                case "nucleicAcid" -> NUCLEIC_ACID;
                case "polymer" -> POLYMER;
                case "mixture" -> MIXTURE;
                case "structurallyDiverse" -> STRUCTURALLY_DIVERSE;
                case "specifiedSubstanceG1" -> SPECIFIED_SUBSTANCE_G1;
                case "concept" -> CONCEPT;
                default -> null;
            });
        }
    }

    private GsrsTerms() {
    }

    /**
     * A substance's concept UUID: {@code T5(namespace, "substance|" + gsrsUuid)}.
     *
     * @param namespace the import's namespace
     * @param gsrsUuid  the UUID GSRS gives the substance
     * @return the UUID
     */
    public static UUID substanceUuid(UUID namespace, UUID gsrsUuid) {
        return UuidT5Generator.get(namespace, "substance|" + gsrsUuid);
    }
}
