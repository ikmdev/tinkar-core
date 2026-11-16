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
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.KernelConcept;

import java.util.UUID;

/**
 * The identities LOINC's concepts and patterns carry, and the OWL axioms that define them. Every
 * identity is minted as IKM's {@code loinc-data} plugins mint it, a version 5 UUID of the name in
 * the namespace, so the two agree on what a concept is.
 *
 * @param namespace the namespace identities are minted in
 */
record LoincTerms(UUID namespace) {

    /** The namespace of IKM's LOINC pipeline ({@code origin.namespace} in {@code loinc-data}). */
    static final UUID DEFAULT_NAMESPACE = UUID.fromString("7b880c4c-8e54-5625-863c-c8504fa78243");

    /** The concept model's Attribute, whose UUID IKM's starter data forces. */
    static final UUID ATTRIBUTE_UUID = UUID.fromString("3af1c784-8a62-59e5-82e7-767de930843b");

    // Concepts the Tinkar kernel of old named and the importer's kernel does not; the store has
    // them from the starter set.
    static final EntityProxy.Concept STATUS_VALUE = proxy("10b873e2-8247-5ab5-9dec-4edef37fc219");
    static final EntityProxy.Concept MODULE = proxy("40d1c869-b509-32f8-b735-836eac577a67");
    static final EntityProxy.Concept MEMBERSHIP_SEMANTIC = proxy("4fa29287-a80e-5f83-abab-4b587973e7b7");
    static final EntityProxy.Concept UNIVERSALLY_UNIQUE_IDENTIFIER = proxy("845274b5-9644-3799-94c6-e0ea37e7d1a4");

    static final String AUTHOR = "Regenstrief Institute, Inc. Author";
    static final String STARTER_AUTHOR = "LOINC Starter Data Author";
    static final String LOINC_MODULE = "LOINC Module";
    static final String LOINC_NUMBER = "LOINC Number";
    static final String OBSERVABLE_ENTITY = "Observable Entity";
    static final String ATTRIBUTE = "Attribute";
    static final String COMPONENT = "Component";
    static final String PROPERTY = "Property";
    static final String TIME_ASPECT = "Time Aspect";
    static final String SYSTEM = "System";
    static final String SCALE = "Scale";
    static final String METHOD = "Method";
    static final String ORDER_VS_OBSERVATION = "Order Vs Observation";
    static final String TEST_ORDERABLE = "Test Orderable";
    static final String TEST_REPORTABLE = "Test Reportable";
    static final String TEST_SUBSET = "Test Subset";
    static final String LOINC_CLASS = "LOINC Class";
    static final String LOINC_CLASS_TYPE = "LOINC ClassType";
    static final String TRIAL_STATUS = "Trial Status";
    static final String DISCOURAGED_STATUS = "Discouraged Status";
    static final String EXAMPLE_UNITS = "Example Units (UCUM)";

    static final String LOINC_TRIAL_STATUS_PATTERN = "LOINC Trial Status Pattern";
    static final String LOINC_DISCOURAGED_STATUS_PATTERN = "LOINC Discouraged Status Pattern";
    static final String LOINC_CLASS_PATTERN = "LOINC Class Pattern";
    static final String EXAMPLE_UCUM_UNITS_PATTERN = "Example UCUM Units Pattern";
    static final String TEST_REPORTABLE_MEMBERSHIP_PATTERN = "Test Reportable Membership Pattern";
    static final String TEST_SUBSET_MEMBERSHIP_PATTERN = "Test Subset Membership Pattern";
    static final String TEST_ORDERABLE_MEMBERSHIP_PATTERN = "Test Orderable Membership Pattern";

    private static EntityProxy.Concept proxy(String uuid) {
        return EntityProxy.Concept.make(PublicIds.of(UUID.fromString(uuid)));
    }

    /** The UUID a name is minted as. */
    UUID uuid(String name) {
        return UuidT5Generator.get(namespace, name);
    }

    /** The public id of the concept a name (or a LOINC code or part number) names. */
    PublicId id(String name) {
        return PublicIds.of(uuid(name));
    }

    /** The concept a name names. */
    EntityProxy.Concept concept(String name) {
        return EntityProxy.Concept.make(name, uuid(name));
    }

    /** The pattern a name names. */
    EntityProxy.Pattern pattern(String name) {
        return EntityProxy.Pattern.make(name, uuid(name));
    }

    /** The attribute concepts' parent, with the UUID the concept model forces on it. */
    PublicId attributeId() {
        return PublicIds.of(ATTRIBUTE_UUID, uuid(ATTRIBUTE));
    }

    /** The concept LOINC part types hang from, by the part type's name in Part.csv. */
    PublicId parentForPartType(String partType) {
        return switch (partType) {
            case "CLASS" -> id(LOINC_CLASS);
            case "COMPONENT" -> id(COMPONENT);
            case "PROPERTY" -> id(PROPERTY);
            case "TIME" -> id(TIME_ASPECT);
            case "SYSTEM" -> id(SYSTEM);
            case "SCALE" -> id(SCALE);
            case "METHOD" -> id(METHOD);
            default -> null;
        };
    }

    private static String ref(UUID uuid) {
        return ":[" + uuid + "]";
    }

    private String roleGroupSome(String attribute, PublicId value) {
        return " ObjectSomeValuesFrom(" + ref(KernelConcept.ROLE_GROUP.publicId().leastUuid())
                + " ObjectSomeValuesFrom(" + ref(uuid(attribute)) + " " + ref(value.leastUuid()) + "))";
    }

    /**
     * What defines the observable entity of a component: an observable entity with the component
     * in a role group.
     *
     * @param observableEntityOfComponent the concept defined
     * @param code                        the component's concept
     * @return the OWL axiom, its concepts by public id
     */
    String componentAxiom(PublicId observableEntityOfComponent, PublicId code) {
        return "EquivalentClasses(" + ref(observableEntityOfComponent.leastUuid())
                + " ObjectIntersectionOf(" + ref(uuid(OBSERVABLE_ENTITY))
                + roleGroupSome(COMPONENT, code) + "))";
    }

    /**
     * What defines a LOINC code: an observable entity with each of its six axes, the ones the
     * code has, in a role group of their own.
     *
     * @param loincNum    the LOINC code
     * @param axes        the part numbers of component, property, time aspect, system, scale and
     *                    method, in that order; null or empty for an axis the code lacks
     * @return the OWL axiom, its concepts by public id
     */
    String codeAxiom(String loincNum, String[] axes) {
        StringBuilder axiom = new StringBuilder("EquivalentClasses(" + ref(uuid(loincNum))
                + " ObjectIntersectionOf(" + ref(uuid(OBSERVABLE_ENTITY)));
        String[] attributes = {COMPONENT, PROPERTY, TIME_ASPECT, SYSTEM, SCALE, METHOD};
        for (int i = 0; i < attributes.length; i++) {
            if (axes[i] != null && !axes[i].isEmpty()) {
                axiom.append(roleGroupSome(attributes[i], id(axes[i])));
            }
        }
        return axiom.append("))").toString();
    }
}
