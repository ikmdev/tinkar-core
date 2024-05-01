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
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.terms.EntityProxy;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IntegerType;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;

import static dev.ikm.tinkar.fhir.transformers.FhirConstants.ROLE_GROUP_URL;
import static dev.ikm.tinkar.fhir.transformers.FhirConstants.SNOMEDCT_URL;

public class FhirUtils {

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
    public static Coding generateCodingObject(StampCalculator stampCalculator, String snomedId){
        Coding coding = new Coding();
        coding.setSystem(SNOMEDCT_URL);
        coding.setCode(snomedId);
        String display = snomedConceptsMap.get(snomedId);
        if( display == null){
            PublicId publicId = FhirUtils.generatePublicId(snomedId);
            FhirUtils.retrieveConcept(stampCalculator, publicId, (snomedCTCode, snomedCTEntity) ->
                coding.setDisplay(snomedCTEntity.description())
            );
        }else{
            coding.setDisplay(display);
        }
        return coding;
    }

    public static void retrieveConcept(StampCalculator stampCalculator, PublicId publicId, BiConsumer<String, Entity<EntityVersion>> snomedId) {
        Entity<EntityVersion> entity = EntityService.get().getEntityFast(publicId.asUuidArray());
        EntityService.get().forEachSemanticForComponentOfPattern(entity.nid(), FhirConstants.IDENTIFIER_PATTERN.nid(), identifierSemantic -> {
            SemanticEntityVersion version = stampCalculator.latest(identifierSemantic).get();//getLatestVersion(identifierSemantic);
            if(version.fieldValues().get(0) instanceof EntityProxy.Concept snomedIdentifierConcept
            && Objects.equals(getSnomedIdentifierConcept().publicId().idString(), snomedIdentifierConcept.publicId().idString())){
                snomedConceptsMap.putIfAbsent(version.fieldValues().get(1).toString(), entity.description());
                snomedId.accept(version.fieldValues().get(1).toString(), entity);
            }
        });
    }

    public static EntityProxy.Concept getSnomedIdentifierConcept(){
        return EntityProxy.Concept.make(PublicIds.of(UuidUtil.fromSNOMED("900000000000294009")));
    }


    public static Extension generateRoleGroup(int roleGroupValue) {
        Extension extension = new Extension();
        extension.setUrl(ROLE_GROUP_URL);
        extension.setValue(new IntegerType(roleGroupValue));
        return extension;
    }
}
