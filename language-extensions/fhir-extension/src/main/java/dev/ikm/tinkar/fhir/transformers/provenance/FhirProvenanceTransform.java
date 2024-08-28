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
package dev.ikm.tinkar.fhir.transformers.provenance;

import dev.ikm.tinkar.fhir.transformers.FhirUtils;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.hl7.fhir.r4.model.*;

import java.util.*;

import static dev.ikm.tinkar.fhir.transformers.FhirConstants.*;

public class FhirProvenanceTransform {

    public static Date dateFormatter(Date date) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);

        calendar.setTimeZone(TimeZone.getTimeZone("UTC"));
        return calendar.getTime();
    }

    public static Provenance provenanceTransform(String reference, Date oldestOfTheLatestDate, Date latestOfTheLatestDate) {

        Provenance provenance = new Provenance();
        provenance.setId(UUID.randomUUID().toString());
        provenance.setMeta(new Meta().addProfile(TERMINOLOGY_CHANGESET_PROVENANCE_PROFILE));

        provenance.addTarget().setReference(reference);
        provenance.getOccurredPeriod()
                .setStart(oldestOfTheLatestDate)
                .setEnd(latestOfTheLatestDate);

        provenance.setRecorded(new Date());

        List<Coding> reasonCodings = new ArrayList<>();
        reasonCodings.add(FhirUtils.generateCoding(TERMINOLOGY_CODESYSTEM_V3_ACTREASON_URL, "METAMGT"));
        provenance.addReason().setCoding(reasonCodings);

        CodeableConcept activityCodeableConcept = new CodeableConcept();
        activityCodeableConcept.addCoding(FhirUtils.generateCoding(TERMINOLOGY_CODESYSTEM_V3_DATAOPERATION_URL, "UPDATE"));
        provenance.setActivity(activityCodeableConcept);

        CodeableConcept authorCodeAbleConcept = new CodeableConcept();
        authorCodeAbleConcept.addCoding(FhirUtils.generateCoding(PROVENANCE_PARTICIPANT_TYPE_URL, "author"));
        provenance.addAgent().setType(authorCodeAbleConcept)
                .setWho(new Reference().setDisplay(TinkarTerm.USER.description()));

        CodeableConcept custodianCodeAbleConcept = new CodeableConcept();
        custodianCodeAbleConcept.addCoding(FhirUtils.generateCoding(PROVENANCE_PARTICIPANT_TYPE_URL, "custodian"));
        provenance.addAgent().setType(custodianCodeAbleConcept)
                .setWho(new Reference().setDisplay("Integrated Knowledge Management"));

        Identifier identifier = new Identifier();
        identifier.setValue(TERMINOLOGY_CODESYSTEM_VARIABLE_ROLE_URL);
        provenance.addEntity().setRole(Provenance.ProvenanceEntityRole.REVISION)
                .setWhat(new Reference().setIdentifier(identifier)
                        .setDisplay("SNOMED CT"));

        Bundle provenanceBundle = new Bundle();
        provenanceBundle.setType(Bundle.BundleType.BATCH);
        provenanceBundle.addEntry()
                .setResource(provenance)
                .setFullUrl(provenance.getIdElement().getValue());

        return provenance;
    }
}
