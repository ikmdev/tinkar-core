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
package dev.ikm.tinkar.fhir.transformers;

import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.UUID;

public class FhirStaticData {
    private static final Logger LOG = LoggerFactory.getLogger(FhirStaticData.class);
    private static final String CODE_SYSTEM_ID = "snomedctVAExtension";
    private static final String EXTENSION_URL = "http://hl7.org/fhir/StructureDefinition/structuredefinition-wg";
    private static final String SNOMED_VA_EXTENSION = "http://snomed.info/sctVAExtension";
    private static final String VALUE_CODE = "fhir";
    private static final String IDENTIFIER_SYSTEM = "urn:ietf:rfc:3986";
    private static final String IDENTIFIER_VALUE = "urn:oid:2.16.840.1.113883.6.96";
    private static final String SNOMED_CT = "SNOMED_CT";
    private static final String SNOMED_CT_ALL_VERSIONS = "SNOMED CT (all versions)";
    private static final String CODE_SYSTEM_PUBLISHER = "IHTSDO";
    private static final String CODE_SYSTEM_VALUE = "http://ihtsdo.org";
    private static final String CODE_SYSTEM_DESCRIPTION = "SNOMED CT is the most comprehensive and precise clinical health terminology product in the world, owned and distributed around the world by The International Health Terminology Standards Development Organisation (IHTSDO).";
    private static final String CODE_SYSTEM_COPYRIGHT = "© 2002-2016 International Health Terminology Standards Development Organisation (IHTSDO). All rights reserved. SNOMED CT®, was originally created by The College of American Pathologists. \\\"SNOMED\\\" and \\\"SNOMED CT\\\" are registered trademarks of the IHTSDO http://www.ihtsdo.org/snomed-ct/get-snomed-ct";
    private static final String CODE_SYSTEM_FILTER_CODE = "concept";
    private static final String CODE_SYSTEM_FILTER_CODE2 = "expression";
    private static final String CODE_SYSTEM_FILTER_CODE3 = "expressions";
    private static final String CODE_SYSTEM_FILTER_DESCRIPTION = "Filter that includes concepts based on their logical definition. e.g. [concept] [is-a] [x] - include all concepts with an is-a relationship to concept x, or [concept] [in] [x]- include all concepts in the reference set identified by concept x";
    private static final String CODE_SYSTEM_FILTER_DESCRIPTION2 = "The result of the filter is the result of executing the given SNOMED CT Expression Constraint";
    private static final String CODE_SYSTEM_FILTER_DESCRIPTION3 = "Whether post-coordinated expressions are included in the value set";
    private static final String ENUMERATION_ISA = "is-a";
    private static final String ENUMERATION_IN = "in";
    private static final String ENUMERATION_EQUAL = "=";
    private static final String CODE_SYSTEM_FILTER_VALUE = "A SNOMED CT code";
    private static final String CODE_SYSTEM_FILTER_VALUE2 = "A SNOMED CT ECL expression (see http://snomed.org/ecl)";
    private static final String CODE_SYSTEM_FILTER_VALUE3 = "true or false";
    private static final String PROPERTY_CODE = "Priority";
    private static final String PROPERTY_URI = "http://snomed.info/id/260870009";
    private static final String PROPERTY_CODE2 = "Access";
    private static final String PROPERTY_URI2 = "http://snomed.info/id/260507000";
    private static final String PROPERTY_CODE3 = "Procedure site";
    private static final String PROPERTY_URI3 = "http://snomed.info/id/363704007";
    private static final String IS_A = "Is a";
    private static final String PROPERTY_URI4 = "http://snomed.info/id/116680003";
    private static final String PROPERTY_CODE5 = "Status";
    private static final String PROPERTY_URI5 = "Status value: [10b873e2-8247-5ab5-9dec-4edef37fc219]";
    private static final String PROPERTY_CODE6 = "OWL Stated Axiom";

    public static CodeSystem generateCodeSystemPropertyContent(CodeSystem codeSystem) {
        CodeSystem.PropertyComponent propertyComponent = new CodeSystem.PropertyComponent();
        propertyComponent.setCode(PROPERTY_CODE);
        propertyComponent.setUri(PROPERTY_URI);
        propertyComponent.setType(CodeSystem.PropertyType.CODE);

        List<CodeSystem.PropertyComponent> types = codeSystem.getProperty();


        CodeSystem.PropertyComponent propertyComponent2 = new CodeSystem.PropertyComponent();
        propertyComponent2.setCode(PROPERTY_CODE2);
        propertyComponent2.setUri(PROPERTY_URI2);
        propertyComponent2.setType(CodeSystem.PropertyType.CODE);

        CodeSystem.PropertyComponent propertyComponent3 = new CodeSystem.PropertyComponent();
        propertyComponent3.setCode(PROPERTY_CODE3);
        propertyComponent3.setUri(PROPERTY_URI3);
        propertyComponent3.setType(CodeSystem.PropertyType.CODE);

        CodeSystem.PropertyComponent propertyComponent4 = new CodeSystem.PropertyComponent();
        propertyComponent4.setCode(IS_A);
        propertyComponent4.setUri(PROPERTY_URI4);
        propertyComponent4.setType(CodeSystem.PropertyType.CODE);

        CodeSystem.PropertyComponent propertyComponent5 = new CodeSystem.PropertyComponent();
        propertyComponent5.setCode(PROPERTY_CODE5);
        propertyComponent5.setUri(PROPERTY_URI5);
        propertyComponent5.setType(CodeSystem.PropertyType.CODE);

        CodeSystem.PropertyComponent propertyComponent6 = new CodeSystem.PropertyComponent();
        propertyComponent6.setCode(PROPERTY_CODE6);
        propertyComponent6.setType(CodeSystem.PropertyType.STRING);

        codeSystem.addProperty(propertyComponent);
        codeSystem.addProperty(propertyComponent2);
        codeSystem.addProperty(propertyComponent3);
        codeSystem.addProperty(propertyComponent4);
        codeSystem.addProperty(propertyComponent5);
        codeSystem.addProperty(propertyComponent6);

        LOG.debug(codeSystem.toString());
        return codeSystem;
    }

    public static CodeSystem generateCodeSystemFilterContent(CodeSystem codeSystem) {
        CodeSystem.CodeSystemFilterComponent codeSystemFilterComponent = new CodeSystem.CodeSystemFilterComponent();

        CodeSystem.FilterOperatorEnumFactory filterOperatorEnumFactory = new CodeSystem.FilterOperatorEnumFactory();

        List<CodeSystem.CodeSystemFilterComponent> filters = codeSystem.getFilter();

        codeSystemFilterComponent.setCode(CODE_SYSTEM_FILTER_CODE);
        codeSystemFilterComponent.setDescription(CODE_SYSTEM_FILTER_DESCRIPTION);

        List<Enumeration<CodeSystem.FilterOperator>> operators = codeSystemFilterComponent.getOperator();
        Enumeration<CodeSystem.FilterOperator> enumerationIsA = new Enumeration<>(filterOperatorEnumFactory, ENUMERATION_ISA);
        Enumeration<CodeSystem.FilterOperator> enumerationIn = new Enumeration<>(filterOperatorEnumFactory, ENUMERATION_IN);
        operators.add(enumerationIsA);
        operators.add(enumerationIn);
        codeSystemFilterComponent.setOperator(operators);
        codeSystemFilterComponent.setValue(CODE_SYSTEM_FILTER_VALUE);

        CodeSystem.CodeSystemFilterComponent codeSystemFilterComponent2 = new CodeSystem.CodeSystemFilterComponent();
        codeSystemFilterComponent2.setCode(CODE_SYSTEM_FILTER_CODE2);
        codeSystemFilterComponent2.setDescription(CODE_SYSTEM_FILTER_DESCRIPTION2);

        List<Enumeration<CodeSystem.FilterOperator>> operators2 = codeSystemFilterComponent2.getOperator();
        Enumeration<CodeSystem.FilterOperator> enumerationEqual = new Enumeration<>(filterOperatorEnumFactory, ENUMERATION_EQUAL);
        operators2.add(enumerationEqual);
        codeSystemFilterComponent2.setOperator(operators2);
        codeSystemFilterComponent2.setValue(CODE_SYSTEM_FILTER_VALUE2);

        CodeSystem.CodeSystemFilterComponent codeSystemFilterComponent3 = new CodeSystem.CodeSystemFilterComponent();
        codeSystemFilterComponent3.setCode(CODE_SYSTEM_FILTER_CODE3);
        codeSystemFilterComponent3.setDescription(CODE_SYSTEM_FILTER_DESCRIPTION3);

        List<Enumeration<CodeSystem.FilterOperator>> operators3 = codeSystemFilterComponent3.getOperator();
        Enumeration<CodeSystem.FilterOperator> enumerationEqual2 = new Enumeration<>(filterOperatorEnumFactory, ENUMERATION_EQUAL);
        operators3.add(enumerationEqual2);
        codeSystemFilterComponent3.setOperator(operators3);
        codeSystemFilterComponent3.setValue(CODE_SYSTEM_FILTER_VALUE3);

        codeSystem.addFilter(codeSystemFilterComponent);
        codeSystem.addFilter(codeSystemFilterComponent2);
        codeSystem.addFilter(codeSystemFilterComponent3);

        LOG.debug(codeSystem.toString());
        return codeSystem;
    }

    public static CodeSystem generateCodeSystemContactContent(CodeSystem codeSystem) {
        ContactPoint contactPoint = new ContactPoint();
        contactPoint.setSystem(ContactPoint.ContactPointSystem.URL);
        contactPoint.setValue(CODE_SYSTEM_VALUE);

        List<ContactPoint> contactPoints = new ArrayList<>();
        contactPoints.add(contactPoint);

        ContactDetail contactDetail = new ContactDetail();
        contactDetail.setTelecom(contactPoints);

        codeSystem.setDescription(CODE_SYSTEM_DESCRIPTION);
        codeSystem.setCopyright(CODE_SYSTEM_COPYRIGHT);
        codeSystem.setCaseSensitive(false);
        codeSystem.setHierarchyMeaning(CodeSystem.CodeSystemHierarchyMeaning.ISA);
        codeSystem.setCompositional(true);
        codeSystem.setVersionNeeded(false);
        codeSystem.setContent(CodeSystem.CodeSystemContentMode.FRAGMENT);

        codeSystem.addContact(contactDetail);

        LOG.debug(codeSystem.toString());
        return codeSystem;
    }

    public static CodeSystem generateCodeSystemIdentifierContent(CodeSystem codeSystem) {
        Identifier identifier = new Identifier();
        identifier.setSystem(IDENTIFIER_SYSTEM);
        identifier.setValue(IDENTIFIER_VALUE);

        codeSystem.setName(SNOMED_CT);
        codeSystem.setTitle(SNOMED_CT_ALL_VERSIONS);
        codeSystem.setStatus(Enumerations.PublicationStatus.ACTIVE);

        codeSystem.setExperimental(false);
        codeSystem.setPublisher(CODE_SYSTEM_PUBLISHER);
        codeSystem.addIdentifier(identifier);

        LOG.debug(codeSystem.toString());
        return codeSystem;
    }

    public static CodeSystem generateCodeSystemExtensionContent(CodeSystem codeSystem) {
        //TODO this has to be dynamic and further logic TBD based on requirements.
        codeSystem.setId(String.valueOf(UUID.randomUUID()));
        Meta meta = new Meta();
        meta.setLastUpdated(new Date());
        codeSystem.setMeta(meta);

        Extension extension = new Extension();
        extension.setUrl(EXTENSION_URL);

        CodeType codeType = new CodeType(VALUE_CODE);
        extension.setValue(codeType);

        List<Extension> extensions = codeSystem.getExtension();
        codeSystem.setExtension(extensions);

        codeSystem.setUrl(SNOMED_VA_EXTENSION);

        codeSystem.addExtension(extension);

        LOG.debug(codeSystem.toString());
        return codeSystem;
    }
}
