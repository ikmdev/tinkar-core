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
package dev.ikm.tinkar.importer.loinc;

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.util.uuid.UuidT5Generator;
import dev.ikm.tinkar.entity.builder.Stamp;
import dev.ikm.tinkar.importer.StoreWriter;
import dev.ikm.tinkar.importer.StoreWriter.FieldDefinition;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.KernelConcept;
import dev.ikm.tinkar.terms.KernelPattern;
import org.eclipse.collections.api.factory.Lists;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static dev.ikm.tinkar.importer.loinc.LoincTerms.*;

/**
 * The concepts and patterns LOINC's codes are written in terms of: the six axes of a LOINC term
 * and the attributes beside them, the status values, the module, the LOINC number as an identifier
 * source, and the patterns of the class, example units, status and test-membership annotations.
 * Ported from IKM's {@code loinc-starterdata} plugin, which writes them as a starter data set; the
 * importer writes them itself, version-aware, so a store that has them already gains nothing.
 */
final class LoincStarterData {

    private final StoreWriter writer;
    private final Stamp stamp;
    private final LoincTerms terms;
    private final Set<Long> axiomConcepts = new HashSet<>();

    private LoincStarterData(StoreWriter writer, Stamp stamp, LoincTerms terms) {
        this.writer = writer;
        this.stamp = stamp;
        this.terms = terms;
    }

    /**
     * Writes the starter data.
     *
     * @param writer the writer
     * @param stamp  the stamp everything is written under
     * @param terms  the identities
     * @return the nids of the concepts whose definition is an OWL axiom, still to be transformed
     */
    static Set<Long> write(StoreWriter writer, Stamp stamp, LoincTerms terms) {
        LoincStarterData data = new LoincStarterData(writer, stamp, terms);
        data.concepts();
        data.patterns();
        return data.axiomConcepts;
    }

    private void concepts() {
        PublicId phenomenon = KernelConcept.PHENOMENON.publicId();
        PublicId attribute = terms.attributeId();
        PublicId orderVsObservation = terms.id(ORDER_VS_OBSERVATION);

        concept(OBSERVABLE_ENTITY, null, null, phenomenon);
        concept(LOINC_NUMBER, "LOINC Code", "The unique LOINC Code is a string in the format of nnnnnnnn-n.",
                KernelConcept.IDENTIFIER_SOURCE.publicId());

        concept(COMPONENT, null, "First major axis-component or analyte", attribute);
        concept(PROPERTY, null, "Second major axis-property observed (e.g., mass vs. substance)", attribute);
        concept(TIME_ASPECT, null, "Third major axis-timing of the measurement (e.g., point in time vs 24 hours)", attribute);
        concept(SYSTEM, null, "Fourth major axis-type of specimen or system (e.g., serum vs urine)", attribute);
        concept(SCALE, null, "Fifth major axis-scale of measurement (e.g., qualitative vs. quantitative)", attribute);
        concept(METHOD, null, "Sixth major axis-method of measurement", attribute);

        concept(TEST_ORDERABLE, null, "Defines term as order only. We have defined them "
                + "only to make it easier to maintain panels or other sets within the LOINC construct. This field reflects "
                + "our best approximation of the terms intended use; it is not to be considered normative or a binding "
                + "resolution.", orderVsObservation);
        concept(TEST_REPORTABLE, null, "Defines term as observation only. We have defined "
                + "them only to make it easier to maintain panels or other sets within the LOINC construct. This field "
                + "reflects our best approximation of the terms intended use; it is not to be considered normative or a "
                + "binding resolution.", orderVsObservation);
        concept(TEST_SUBSET, null, "Subset, is used for terms that are subsets of a "
                + "panel but do not represent a package that is known to be orderable. We have defined them only to make "
                + "it easier to maintain panels or other sets within the LOINC construct. This field reflects our best "
                + "approximation of the terms intended use; it is not to be considered normative or a binding resolution.",
                orderVsObservation);
        concept(ORDER_VS_OBSERVATION, null, "Defines term as order only, observation only, or both. A fourth category, "
                + "Subset, is used for terms that are subsets of a panel but do not represent a package that is known to "
                + "be orderable. We have defined them only to make it easier to maintain panels or other sets within the "
                + "LOINC construct. This field reflects our best approximation of the terms intended use; it is not to be "
                + "considered normative or a binding resolution.", attribute);

        concept(LOINC_CLASS, null, "An arbitrary classification of the terms for grouping related observations together.",
                attribute);
        concept(LOINC_CLASS_TYPE, null, "1=Laboratory class; 2=Clinical class; 3=Claims attachments; 4=Surveys", attribute);
        concept(ATTRIBUTE, null, null, phenomenon);

        concept(TRIAL_STATUS, null, "Concept is experimental in nature. Use with caution as the concept "
                + "and associated attributes may change.", STATUS_VALUE.publicId());
        concept(DISCOURAGED_STATUS, null, "Concept is not recommended for current use. New mappings "
                + "to this concept are discouraged; although existing may mappings may continue to be valid in context.",
                STATUS_VALUE.publicId());

        concept(LOINC_MODULE, "LOINC Core Module", "Module responsible for LOINC", MODULE.publicId());
        concept(EXAMPLE_UNITS, null, "The Unified Code for Units of Measure (UCUM) is a code system intended to include "
                + "all units of measures being contemporarily used in international science, engineering, and business. "
                + "(www.unitsofmeasure.org) This field contains example units of measures for this term expressed as "
                + "UCUM units.", phenomenon);
    }

    /**
     * One concept: its names, its identifier as a UUID, and what it is a kind of. A concept under
     * Attribute is an object property, stated as an OWL sub-property axiom; any other is stated
     * a kind of its parent.
     */
    private void concept(String name, String synonym, String definition, PublicId parent) {
        PublicId id = name.equals(ATTRIBUTE) ? terms.attributeId() : terms.id(name);
        UUID uuid = terms.uuid(name);
        long nid = writer.concept(stamp, id);
        writer.describe(stamp, nid, UuidT5Generator.get(uuid, "fqn"), name, KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE);
        if (synonym != null) {
            writer.describe(stamp, nid, UuidT5Generator.get(uuid, "synonym"), synonym, KernelConcept.REGULAR_NAME_DESCRIPTION_TYPE);
        }
        if (definition != null) {
            writer.describe(stamp, nid, UuidT5Generator.get(uuid, "definition"), definition, KernelConcept.DEFINITION_DESCRIPTION_TYPE);
        }
        writer.semantic(stamp, PublicIds.of(UuidT5Generator.get(uuid, "uuid identifier")), KernelPattern.IDENTIFIER_PATTERN, nid,
                Lists.immutable.of(UNIVERSALLY_UNIQUE_IDENTIFIER, uuid.toString()));
        if (parent.contains(ATTRIBUTE_UUID)) {
            // The parent is Attribute, named by the UUID that says so.
            writer.semantic(stamp, PublicIds.of(UuidT5Generator.get(uuid, "owl axiom")), KernelPattern.OWL_AXIOM_SYNTAX_PATTERN, nid,
                    Lists.immutable.of(String.format("SubObjectPropertyOf(:[%s] :[%s])", uuid, ATTRIBUTE_UUID)));
            axiomConcepts.add(nid);
        } else {
            writer.statedParent(stamp, id, parent);
        }
    }

    private void patterns() {
        ConceptFacade classConcept = terms.concept(LOINC_CLASS);
        ConceptFacade classTypeConcept = terms.concept(LOINC_CLASS_TYPE);
        ConceptFacade unitsConcept = terms.concept(EXAMPLE_UNITS);

        pattern(LOINC_TRIAL_STATUS_PATTERN, "Trial Status Pattern", terms.concept(TRIAL_STATUS), STATUS_VALUE, List.of());
        pattern(LOINC_DISCOURAGED_STATUS_PATTERN, "Discouraged Status Pattern", terms.concept(DISCOURAGED_STATUS), STATUS_VALUE,
                List.of());
        pattern(LOINC_CLASS_PATTERN, "LOINC ClassType Pattern", classConcept, classConcept, List.of(
                new FieldDefinition(classConcept, classConcept, KernelConcept.COMPONENT_FIELD),
                new FieldDefinition(classTypeConcept, classTypeConcept, KernelConcept.INTEGER_FIELD)));
        pattern(EXAMPLE_UCUM_UNITS_PATTERN, "Example Units (UCUM) Pattern", unitsConcept, unitsConcept,
                List.of(new FieldDefinition(unitsConcept, unitsConcept, KernelConcept.STRING)));
        pattern(TEST_ORDERABLE_MEMBERSHIP_PATTERN, "Test Orderable Pattern", terms.concept(TEST_ORDERABLE), MEMBERSHIP_SEMANTIC,
                List.of());
        pattern(TEST_REPORTABLE_MEMBERSHIP_PATTERN, "Test Reportable Pattern", terms.concept(TEST_REPORTABLE), MEMBERSHIP_SEMANTIC,
                List.of());
        pattern(TEST_SUBSET_MEMBERSHIP_PATTERN, "Test Subset Membership Pattern", terms.concept(TEST_SUBSET), MEMBERSHIP_SEMANTIC,
                List.of());
    }

    private void pattern(String name, String text, ConceptFacade meaning, ConceptFacade purpose, List<FieldDefinition> fields) {
        PublicId id = terms.id(name);
        long nid = writer.pattern(stamp, id, meaning, purpose, fields);
        UUID uuid = terms.uuid(name);
        writer.describe(stamp, nid, UuidT5Generator.get(uuid, "fqn"), text, KernelConcept.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE);
        writer.describe(stamp, nid, UuidT5Generator.get(uuid, "synonym"), text, KernelConcept.REGULAR_NAME_DESCRIPTION_TYPE);
    }
}
