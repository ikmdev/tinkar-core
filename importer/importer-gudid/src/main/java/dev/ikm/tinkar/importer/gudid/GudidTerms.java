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
import dev.ikm.tinkar.terms.EntityProxy;

import java.util.Optional;
import java.util.UUID;

/**
 * The concepts and pattern of IKM's GUDID starter data the importer writes with, by the UUIDs of
 * IKM's {@code GudidTerm} binding ({@code ikmdev/gudid-data}, {@code gudid-starterdata}). The
 * starter data carried in this module defines them; the importer only refers to them.
 */
final class GudidTerms {

    /** The GUDID author, every stamp's author. */
    static final EntityProxy.Concept AUTHOR = concept("abcc8d16-6c3a-4d74-a83e-e766dcd6fe3d");
    /** The GUDID module, every stamp's module. */
    static final EntityProxy.Concept MODULE = concept("7d48d128-83bc-4831-a00a-56dbf1d2a812");
    /** Identifier source of an FDA product code. */
    static final EntityProxy.Concept FDA_PRODUCT_CODE = concept("f9e9fadd-1870-4eaf-85f2-a694740ec47a");
    /** Identifier source of a device's public device record key. */
    static final EntityProxy.Concept PUBLIC_DEVICE_RECORD_KEY = concept("4595a20d-22fa-45c6-9197-966ccd4b6a2b");
    /** Identifier source of an FDA premarket submission number. */
    static final EntityProxy.Concept PREMARKET_SUBMISSION_NUMBER = concept("8c0fd617-7cd8-4498-8aca-428c5361890b");
    /** The GMDN terms concept: identifier source of a GMDN code, and every GMDN term's parent. */
    static final EntityProxy.Concept GMDN_TERMS = concept("73257f7e-da60-4f6e-9459-e8cc6e6f8312");
    /** The pattern relating a device to its GMDN terms: one field, a component id set. */
    static final EntityProxy.Pattern GMDN_TERMS_PATTERN =
            EntityProxy.Pattern.make(PublicIds.of(UUID.fromString("5b8fbc02-465b-4b7c-bf12-f11a8e87db4a")));
    /** Identifier source of a UUID, on the starter set (IKM's {@code TinkarTerm.UNIVERSALLY_UNIQUE_IDENTIFIER}). */
    static final EntityProxy.Concept UUID_IDENTIFIER = concept("845274b5-9644-3799-94c6-e0ea37e7d1a4");

    private GudidTerms() {
    }

    static EntityProxy.Concept concept(String uuid) {
        return EntityProxy.Concept.make(PublicIds.of(UUID.fromString(uuid)));
    }

    /**
     * The FDA's medical specialties (review panels' specialty codes in the product classification
     * file), each the parent of the product codes classified under it.
     */
    enum MedicalSpecialty {
        ANESTHESIOLOGY("AN", "eec10bd1-fcd8-4d8c-936c-645f920bcfba"),
        CARDIOVASCULAR("CV", "97cce489-04fb-47ae-abf4-d0cf4185ff36"),
        CLINICAL_CHEMISTRY("CH", "0cc27ea9-b441-4e4c-8baf-888d936f304c"),
        DENTAL("DE", "49509c1f-fccf-426b-bddb-302f30ff87ab"),
        EAR_NOSE_THROAT("EN", "9ccd764f-d64d-408c-8000-ef2d503154bb"),
        GASTROENTEROLOGY_UROLOGY("GU", "eb743737-eb31-4d23-af77-c5fec133273d"),
        GENERAL_HOSPITAL("HO", "822682f2-37e5-4a23-a480-490b71f38104"),
        HEMATOLOGY("HE", "4f4c13df-dc61-420f-bb13-9f59ab26d6a7"),
        IMMUNOLOGY("IM", "86027a36-ac17-462b-b864-d0e3f5bd9a74"),
        MICROBIOLOGY("MI", "04b2404e-5535-4c11-8e5a-5a7201336199"),
        NEUROLOGY("NE", "1f45fa9f-ebcd-41d0-b387-da989066cdbe"),
        OBSTETRICS_GYNECOLOGY("OB", "2f7f306c-0e5a-484c-8e97-59f923bc7f56"),
        OPHTHALMIC("OP", "4f38386d-b5d9-46a2-8e82-27f429e925e0"),
        ORTHOPEDIC("OR", "114c587a-6bda-4036-99bb-ce76a21fce85"),
        PATHOLOGY("PA", "e443fd71-842b-458b-adc4-2c9015e212d5"),
        PHYSICAL_MEDICINE("PM", "08fec815-1598-42c2-99cf-90dd69f0dfdc"),
        RADIOLOGY("RA", "ccc82d08-561a-4e11-9084-083300760957"),
        GENERAL_PLASTIC_SURGERY("SU", "4f79eeba-874a-4b6c-93d4-82f6f49b5815"),
        CLINICAL_TOXICOLOGY("TX", "e6026812-6a34-4d28-abad-3db727ea9cfc"),
        /** A blank or unrecognized specialty; the classification file leaves some blank. */
        UNKNOWN("UNKNOWN", "a2daa52b-7f15-404e-adfe-f30959817d04");

        final String abbreviation;
        final EntityProxy.Concept concept;

        MedicalSpecialty(String abbreviation, String uuid) {
            this.abbreviation = abbreviation;
            this.concept = GudidTerms.concept(uuid);
        }

        /**
         * The specialty an abbreviation names.
         *
         * @param abbreviation the abbreviation, as the classification file has it
         * @return the specialty, if the abbreviation is one
         */
        static Optional<MedicalSpecialty> named(String abbreviation) {
            String code = abbreviation == null ? "" : abbreviation.strip();
            for (MedicalSpecialty specialty : values()) {
                if (specialty.abbreviation.equals(code)) {
                    return Optional.of(specialty);
                }
            }
            return Optional.empty();
        }

        /**
         * The parent a product code classified under an abbreviation is stated under: its
         * specialty, or {@link #UNKNOWN} for a blank or unrecognized one, as IKM's transformer has it.
         *
         * @param abbreviation the abbreviation
         * @return the specialty
         */
        static MedicalSpecialty parentFor(String abbreviation) {
            return named(abbreviation).orElse(UNKNOWN);
        }
    }

    /** The agencies that issue device identifiers, each an identifier source. */
    enum IssuingAgency {
        HIBCC("HIBCC", "5bc619bd-1080-413c-86b7-0c696605532c"),
        ICCBBA("ICCBBA", "8cf247be-cc9e-4a0e-9ab9-77330209a398"),
        GS1("GS1", "bd5c11e4-b0eb-4971-b5bf-7d1ccc778a73"),
        NDC_NHRIC("NDC/NHRIC", "f363ef10-4c50-410f-9aa0-95ceef14c658");

        final String code;
        final EntityProxy.Concept concept;

        IssuingAgency(String code, String uuid) {
            this.code = code;
            this.concept = GudidTerms.concept(uuid);
        }

        /**
         * The agency a code names.
         *
         * @param code the code, as the identifiers file has it
         * @return the agency, if the code is one
         */
        static Optional<IssuingAgency> named(String code) {
            for (IssuingAgency agency : values()) {
                if (agency.code.equals(code)) {
                    return Optional.of(agency);
                }
            }
            return Optional.empty();
        }
    }
}
