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

import dev.ikm.tinkar.terms.EntityProxy;

import java.util.UUID;

public class FhirConstants {
    public static final String DESCRIPTION_NOT_CASE_SENSITIVE_SNOMEDID = "900000000000448009";
    public static final String DESCRIPTION_CASE_SENSITIVE_SNOMEDID = "900000000000017005";
    public static final String DESCRIPTION_INITIAL_CHARACTER_CASE_SENSITIVE_SNOMEDID = "900000000000020002";
    public static final String PREFERRED_SNOMEDID = "900000000000548007";
    public static final String ACCEPTABLE_SNOMEDID = "900000000000549004";
    public static final String DEFINITION_DESCRIPTION_TYPE_SNOMEDID = "900000000000550004";
    public static final String FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE_SNOMEDID = "900000000000003001";
    public static final String REGULAR_NAME_DESCRIPTION_TYPE_SNOMEDID = "900000000000013009";
    public static final String ACTIVE_VALUE_SNOMEDID = "900000000000545005";
    public static final String INACTIVE_VALUE_SNOMEDID = "900000000000546006";
    public static final String STATED_RELATIONSHIP_SNOMEDID = "900000000000010007";
    public static final String SUFFICIENTLY_DEFINED_SNOMEDID = "900000000000073002";
    public static final String INFERRED_RELATIONSHIP_SNOMEDID = "900000000000011006";
    public static final String NOT_SUFFICIENTLY_DEFINED_SNOMEDID = "900000000000074008";
    public static final String CLINICAL_COURSE_SNOMEDID = "90734009";
    public static final String FINDING_SITE_SNOMEDID = "39607008";
    public static final String IS_A_SNOMEDID = "64572001";
    public static final String ASSOCIATED_MORPHOLOGY_SNOMEDID = "108369006";

    public static final String STATUS = "Status";
    public static final String SNOMEDCT_URL = "http://snomed.info/sct";
    public static final String IKM_DEV_URL = "https://www.ikm.dev/";
    public static final String CODE_CONCEPT_ADDITIONAL_IDENTIFIER_URL = "https://hl7.org/fhir/uv/termchangeset/StructureDefinition/codesystem-concept-additional-identifier";
    public static final String DESCRIPTION_CASE_SENSITIVITY_URL = "https://hl7.org/fhir/uv/termchangeset/StructureDefinition/description-case-sensitivity";
    public static final String DESCRIPTION_ACCEPTABILITY_URL = "https://hl7.org/fhir/uv/termchangeset/StructureDefinition/description-acceptability";
    public static final String DEFINING_RELATIONSHIP_TYPE_URL = "https://hl7.org/fhir/uv/termchangeset/StructureDefinition/defining-relationship-type";
    public static final String EL_PROFILE_SET_OPERATOR_URL = "https://hl7.org/fhir/uv/termchangeset/StructureDefinition/el-profile-set-operator";
    public static final String ROLE_GROUP_URL = "https://hl7.org/fhir/uv/termchangeset/StructureDefinition/role-group";
    public static final EntityProxy.Pattern IDENTIFIER_PATTERN = EntityProxy.Pattern.make("Identifier Pattern", UUID.fromString("5d60e14b-c410-5172-9559-3c4253278ae2"));
    public static final EntityProxy.Pattern AXIOM_SYNTAX_PATTERN = EntityProxy.Pattern.make("Axiom Syntax Pattern", UUID.fromString("c0ca180b-aae2-5fa1-9ab7-4a24f2dfe16b"));
    public static final String PROVENANCE_PARTICIPANT_TYPE_URL = "http://terminology.hl7.org/CodeSystem/provenance-participant-type";
    public static final String TERMINOLOGY_CHANGESET_PROVENANCE_PROFILE = "https://hl7.org/fhir/uv/termchangeset/StructureDefinition/terminology-changeset-provenance-profile";
    public static final String TERMINOLOGY_CODESYSTEM_V3_ACTREASON_URL = "http://terminology.hl7.org/CodeSystem/v3-ActReason";
    public static final String TERMINOLOGY_CODESYSTEM_V3_DATAOPERATION_URL = "http://terminology.hl7.org/CodeSystem/v3-DataOperation";
    public static final String TERMINOLOGY_CODESYSTEM_VARIABLE_ROLE_URL = "http://snomed.info/sct/731000124108/version/20240404";
    public static final String REFERENCE_URL = "CodeSystem/HL7VariableRoleChangeSet";
    public static final String CODE_SYSTEM_ID = "snomedctVAExtension";
    public static final String CODESYSTEM = "CodeSystem/";

}