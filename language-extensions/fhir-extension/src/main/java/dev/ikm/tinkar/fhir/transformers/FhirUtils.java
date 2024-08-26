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

import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IntegerType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

import static dev.ikm.tinkar.fhir.transformers.FhirConstants.*;

public class FhirUtils {

    private static final Logger LOG = LoggerFactory.getLogger(FhirUtils.class);
    public static Map<String, String> snomedConceptsMap = new HashMap<>();

    public static PublicId generatePublicId(String string) {
        return PublicIds.of(UuidUtil.fromSNOMED(string));
    }

    public static Coding generateCoding(String system, String code) {
        Coding coding = new Coding();
        coding.setSystem(system);
        coding.setCode(code);
        return coding;
    }


    public static EntityProxy.Concept generateProfileSetOperator(String code) {
        return switch (code) {
            case ACTIVE_VALUE_SNOMEDID -> TinkarTerm.ACTIVE_STATE;
            case INACTIVE_VALUE_SNOMEDID -> TinkarTerm.INACTIVE_STATE;
            default -> throw new IllegalStateException("Unexpected value: " + code);
        };
    }

    public static EntityProxy.Concept generateCaseSignificance(String code) {
        return switch (code) {
            case "900000000000448009" -> TinkarTerm.DESCRIPTION_NOT_CASE_SENSITIVE;
            case "900000000000017005" -> TinkarTerm.DESCRIPTION_CASE_SENSITIVE;
            case "900000000000020002" -> TinkarTerm.DESCRIPTION_INITIAL_CHARACTER_CASE_SENSITIVE;
            default ->
                    throw new IllegalArgumentException("Unexpected value while generating case significance: " + code);
        };
    }

    public static EntityProxy.Concept generateAcceptability(String code) {
        return switch (code) {
            case "900000000000548007" -> TinkarTerm.PREFERRED;
            case "900000000000549004" -> TinkarTerm.ACCEPTABLE;
            default -> throw new IllegalArgumentException("Unexpected value while generating acceptability: " + code);
        };
    }

    public static EntityProxy.Concept generateNameType(String code) {
        return switch (code) {
            case "900000000000003001" -> TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE;
            case "900000000000013009" -> TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE;
            default -> throw new IllegalArgumentException("Unexpected value while generating name type: " + code);
        };
    }

    public static EntityProxy.Concept generateLanguage(String language) {
        return switch (language) {
            case "en-US" -> TinkarTerm.ENGLISH_LANGUAGE;
            case "en-GB" -> TinkarTerm.GB_ENGLISH_DIALECT;
            default -> throw new IllegalArgumentException("Unexpected value while generating language: " + language);
        };
    }

    public static Coding generateCodingProperty(String system, String code, String display) {
        Coding coding = new Coding();
        coding.setSystem(system);
        coding.setCode(code);
        coding.setDisplay(display);
        return coding;
    }

    public static Coding generateCodingObject(StampCalculator stampCalculator, String snomedId) {
        if (snomedId == null || snomedId.trim().isEmpty()) {
            LOG.warn("snomedId is null, returning null coding value.");
            return null;
        }
        Coding coding = new Coding();
        coding.setSystem(SNOMEDCT_URL);
        coding.setCode(snomedId);
        String display = snomedConceptsMap.get(snomedId);
        if (display == null) {
            PublicId publicId = FhirUtils.generatePublicId(snomedId);
            FhirUtils.retrieveConcept(stampCalculator, publicId, (snomedCTCode, snomedCTEntity) -> {
                if (snomedCTEntity != null) {
                    coding.setDisplay(snomedCTEntity.description());
                } else {
                    coding.setDisplay("Does not exist");
                }
            });
        } else {
            coding.setDisplay(display);
        }
        return coding;
    }

    public static void retrieveConcept(StampCalculator stampCalculator, PublicId publicId, BiConsumer<String, Entity<EntityVersion>> snomedId) {
        Entity<EntityVersion> entity = EntityService.get().getEntityFast(publicId.asUuidArray());
        if (entity != null) {
            int[] idSemantics = EntityService.get().semanticNidsForComponentOfPattern(entity.nid(), FhirConstants.IDENTIFIER_PATTERN.nid());
            if (idSemantics.length > 0) {
                EntityService.get().forEachSemanticForComponentOfPattern(entity.nid(), FhirConstants.IDENTIFIER_PATTERN.nid(), identifierSemantic -> {
                    SemanticEntityVersion version = stampCalculator.latest(identifierSemantic).get();//getLatestVersion(identifierSemantic);
                    if (version.fieldValues().get(0) instanceof EntityProxy.Concept snomedIdentifierConcept
                            && (PublicId.equals(TinkarTerm.SCTID.publicId(), snomedIdentifierConcept.publicId())
                            || idSemantics.length == 1)) {
                        snomedConceptsMap.putIfAbsent(version.fieldValues().get(1).toString(), entity.description());
                        snomedId.accept(version.fieldValues().get(1).toString(), entity);
                    }
                });
            } else {
                snomedConceptsMap.putIfAbsent(entity.publicId().asUuidArray()[0].toString(), entity.description());
                snomedId.accept(entity.publicId().asUuidArray()[0].toString(), entity);
            }

        } else {
            LOG.warn("Unable to retrive snomed concept for publicId: " + publicId);
//            snomedConceptsMap.putIfAbsent("NA", "Does not exist");
//            snomedId.accept("NA", null);
        }
    }

    public static EntityProxy.Concept getSnomedIdentifierConcept() {
        return EntityProxy.Concept.make(PublicIds.of(UuidUtil.fromSNOMED("900000000000294009")));
    }

    public static Extension generateRoleGroup(int roleGroupValue) {
        Extension extension = new Extension();
        extension.setUrl(ROLE_GROUP_URL);
        extension.setValue(new IntegerType(roleGroupValue));
        return extension;
    }


}
