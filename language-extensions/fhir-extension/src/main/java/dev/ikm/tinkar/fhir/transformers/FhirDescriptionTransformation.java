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

import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.ikm.tinkar.fhir.transformers.FhirConstants.*;
import static dev.ikm.tinkar.fhir.transformers.FhirUtils.generateCodingObject;

public class FhirDescriptionTransformation {

    private final StampCalculator stampCalculatorWithCache;
    private final Map<String, String> caseSencitivityCodes;
    private final Map<String, String> acceptabilityCodes;
    private final Map<String, String> descriptionTypeCodes;

    public FhirDescriptionTransformation(StampCalculator stampCalculatorWithCache) {
        this.stampCalculatorWithCache = stampCalculatorWithCache;
        caseSencitivityCodes = new HashMap<>();
        caseSencitivityCodes.put(TinkarTerm.DESCRIPTION_NOT_CASE_SENSITIVE.publicId().asUuidArray()[0].toString(), DESCRIPTION_NOT_CASE_SENSITIVE_SNOMEDID);
        caseSencitivityCodes.put(TinkarTerm.DESCRIPTION_CASE_SENSITIVE.publicId().asUuidArray()[0].toString(), DESCRIPTION_CASE_SENSITIVE_SNOMEDID);
        caseSencitivityCodes.put(TinkarTerm.DESCRIPTION_INITIAL_CHARACTER_CASE_SENSITIVE.publicId().asUuidArray()[0].toString(), DESCRIPTION_INITIAL_CHARACTER_CASE_SENSITIVE_SNOMEDID);

        acceptabilityCodes = new HashMap<>();
        acceptabilityCodes.put(TinkarTerm.PREFERRED.publicId().asUuidArray()[0].toString(), PREFERRED_SNOMEDID);
        acceptabilityCodes.put(TinkarTerm.ACCEPTABLE.publicId().asUuidArray()[0].toString(), ACCEPTABLE_SNOMEDID);

        descriptionTypeCodes = new HashMap<>();
        descriptionTypeCodes.put(TinkarTerm.DEFINITION_DESCRIPTION_TYPE.publicId().asUuidArray()[0].toString(), DEFINITION_DESCRIPTION_TYPE_SNOMEDID);
        descriptionTypeCodes.put(TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.publicId().asUuidArray()[0].toString(), FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE_SNOMEDID);
        descriptionTypeCodes.put(TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE.publicId().asUuidArray()[0].toString(), REGULAR_NAME_DESCRIPTION_TYPE_SNOMEDID);
    }

    public List<CodeSystem.ConceptDefinitionDesignationComponent> transformDescription(SemanticEntity<SemanticEntityVersion> descriptionSemantic) {
        return descriptionAcceptability(descriptionSemantic);
    }

    private List<CodeSystem.ConceptDefinitionDesignationComponent> descriptionAcceptability(SemanticEntity<SemanticEntityVersion> descriptionSemantic) {

        AtomicBoolean missingLanguage = new AtomicBoolean(true);
        List<CodeSystem.ConceptDefinitionDesignationComponent> designations = new ArrayList<>();

        EntityService.get().forEachSemanticForComponent(descriptionSemantic.nid(), (languageSemantic) -> {
            missingLanguage.set(false);
            CodeSystem.ConceptDefinitionDesignationComponent designation = new CodeSystem.ConceptDefinitionDesignationComponent();
            designations.add(designation);
            Extension caseSensitivityExtension = descriptionCaseSensitivity(descriptionSemantic, designation);
            designation.addExtension(caseSensitivityExtension);

            Extension acceptabilityExtension = new Extension();
            designation.addExtension(acceptabilityExtension);
            acceptabilityExtension.setUrl(DESCRIPTION_ACCEPTABILITY_URL);
            CodeableConcept acceptabilityCodableConcept = new CodeableConcept();
            acceptabilityExtension.setValue(acceptabilityCodableConcept);
            Latest<SemanticEntityVersion> latestLanguageSemanticVersion = stampCalculatorWithCache.latest(languageSemantic);
            latestLanguageSemanticVersion.ifPresent(languageSemanticVersion -> {
                if (languageSemanticVersion.fieldValues().get(0) instanceof EntityProxy.Concept acceptabilityConcept) {
                    String acceptabilityId = acceptabilityCodes.get(acceptabilityConcept.publicId().asUuidArray()[0].toString());
                    if (acceptabilityId != null) {
                        acceptabilityCodableConcept.addCoding(generateCodingObject(stampCalculatorWithCache, acceptabilityId));
                    }
                }
                if (languageSemantic.patternNid() == TinkarTerm.US_DIALECT_PATTERN.nid()) {
                    designation.setLanguage("en-US");
                } else if (languageSemantic.patternNid() == TinkarTerm.GB_DIALECT_PATTERN.nid()) {
                    designation.setLanguage("en-GB");
                }
            });
        });

        if (missingLanguage.get()) {
            CodeSystem.ConceptDefinitionDesignationComponent designation = new CodeSystem.ConceptDefinitionDesignationComponent();
            designations.add(designation);
            Extension caseSensitivityExtension = descriptionCaseSensitivity(descriptionSemantic, designation);
            designation.addExtension(caseSensitivityExtension);
        }
        return designations;
    }

    private Extension descriptionCaseSensitivity(SemanticEntity<SemanticEntityVersion> descriptionSemantic, CodeSystem.ConceptDefinitionDesignationComponent designation) {

        Extension caseSensitivityExtension = new Extension();
        Latest<SemanticEntityVersion> latestDescriptionSemanticVersion = stampCalculatorWithCache.latest(descriptionSemantic);
        latestDescriptionSemanticVersion.ifPresent(descriptionSemanticVersion -> {
            ImmutableList<Object> fields = descriptionSemanticVersion.fieldValues();
            designation.setValue(fields.get(1).toString());
            caseSensitivityExtension.setUrl(DESCRIPTION_CASE_SENSITIVITY_URL);
            CodeableConcept caseSensitiveCodeableConcept = new CodeableConcept();
            caseSensitivityExtension.setValue(caseSensitiveCodeableConcept);
            if (fields.get(2) instanceof EntityProxy.Concept caseSencitivityConcept) {
                String caseSignificanceId = caseSencitivityCodes.get(caseSencitivityConcept.publicId().asUuidArray()[0].toString());
                if (caseSignificanceId != null) {
                    caseSensitiveCodeableConcept.addCoding(generateCodingObject(stampCalculatorWithCache, caseSignificanceId));
                }
            }
            if (fields.get(3) instanceof EntityProxy.Concept useConcept) {
                String typeId = descriptionTypeCodes.get(useConcept.publicId().asUuidArray()[0].toString());
                if (typeId != null) {
                    Coding useCoding = generateCodingObject(stampCalculatorWithCache, typeId);
                    designation.setUse(useCoding);
                }
            }
        });
        return caseSensitivityExtension;
    }

}
