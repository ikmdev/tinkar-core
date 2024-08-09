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

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.composer.Composer;
import dev.ikm.tinkar.composer.Session;
import dev.ikm.tinkar.composer.template.Synonym;
import dev.ikm.tinkar.composer.template.USDialect;
import dev.ikm.tinkar.entity.EntityCountSummary;
import dev.ikm.tinkar.fhir.transformers.provenance.FhirProvenanceTransform;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.State;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static dev.ikm.tinkar.fhir.transformers.FhirConstants.CODE_CONCEPT_ADDITIONAL_IDENTIFIER_URL;
import static dev.ikm.tinkar.fhir.transformers.FhirConstants.IKM_DEV_URL;

public class LoadEntitiesFromFhirJson extends TrackingCallable<EntityCountSummary> {
    private static final Logger LOG = LoggerFactory.getLogger(LoadEntitiesFromFhirJson.class);
    private File importFile;
    private CodeSystem codeSystem;
    private FhirContext ctx;
    private IParser parser;
    private Date fromDate;
    private Date toDate;
    private Consumer<String> fhirAndProvenanceJson;
    private final AtomicLong importCount = new AtomicLong();
    private final AtomicLong importConceptCount = new AtomicLong();
    private final AtomicLong importSemanticCount = new AtomicLong();
    private final AtomicLong importPatternCount = new AtomicLong();
    private final AtomicLong importStampCount = new AtomicLong();

    public LoadEntitiesFromFhirJson(File importFile) {
        super(false, true);
        this.importFile = importFile;
        System.out.println("Loading entities from: " + importFile.getAbsolutePath());
    }

    public LoadEntitiesFromFhirJson() {
        this.ctx = FhirContext.forR4();
        this.parser = ctx.newJsonParser();
        this.codeSystem = new CodeSystem();
    }

    public static void main(String[] args) throws IOException {

    }

    @Override
    protected EntityCountSummary compute() throws Exception {
        return null;
    }

   /* public static Session composerSession() {
        State status = State.ACTIVE;
        long time = PrimitiveData.PREMUNDANE_TIME;
        EntityProxy.Concept author = TinkarTerm.USER;
        EntityProxy.Concept module = TinkarTerm.PRIMORDIAL_MODULE;
        EntityProxy.Concept path = TinkarTerm.PRIMORDIAL_PATH;
        Composer composer = new Composer("FHIR Concept Composer...");
        Session session = composer.open(status, time, author, module, path);
        return session;

    }*/

    public Bundle parseJsonBundle(String jsonBundle, CodeSystem codeSystem) {
        codeSystem = new CodeSystem();
        Provenance provenance = FhirProvenanceTransform.provenanceTransform("CodeSystem/" + codeSystem.getId(), fromDate, toDate);
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.TRANSACTION);
        bundle.addEntry()
                .setResource(codeSystem)
                .setFullUrl(codeSystem.getResourceType().name() + "/" + codeSystem.getIdElement().getValue());
        bundle.addEntry()
                .setResource(provenance)
                .setFullUrl(provenance.getResourceType().name() + "/" + provenance.getIdElement().getValue());

        FhirContext ctx = FhirContext.forR4();
        IParser parser = ctx.newJsonParser();

        String bundleJson = parser.setPrettyPrint(true).encodeResourceToString(bundle);
        if (LOG.isDebugEnabled()) {
            LOG.debug(bundleJson);
        }

        return (Bundle) parser.parseResource(jsonBundle);
    }

    public Session FhirCodeSystemConceptTransform(Bundle bundle) {
        State status = State.ACTIVE;
        long time = PrimitiveData.PREMUNDANE_TIME;
        EntityProxy.Concept author = TinkarTerm.USER;
        EntityProxy.Concept module = TinkarTerm.PRIMORDIAL_MODULE;
        EntityProxy.Concept path = TinkarTerm.PRIMORDIAL_PATH;
        Composer composer = new Composer("FHIR Concept Composer...");
        Session session = composer.open(status, time, author, module, path);
        CodeSystem codeSystem = new CodeSystem();
        String jsonBundle = parser.setPrettyPrint(true).encodeResourceToString(bundle);
        bundle = parseJsonBundle(jsonBundle, codeSystem);

        EntityProxy.Concept conceptId = null;

        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            Resource resource = entry.getResource();
            if (resource instanceof CodeSystem) {
                codeSystem = (CodeSystem) resource;
                //all concepts retrieved...
                for (CodeSystem.ConceptDefinitionComponent concept : codeSystem.getConcept()) {
                    for (Extension extension : concept.getExtension()) {
                        String url = extension.getUrl();
                        if (CODE_CONCEPT_ADDITIONAL_IDENTIFIER_URL.equals(url)) {
                            Identifier valueIdentifier = (Identifier) extension.getValue();
                            String system = valueIdentifier.getSystem();
                            String value = valueIdentifier.getValue();
                            if (IKM_DEV_URL.equals(system)) {
                                conceptId = EntityProxy.Concept.make(PublicIds.of(value));
                            }
                        }
                    }
                    //Designation transform
                    for (CodeSystem.ConceptDefinitionDesignationComponent designation : concept.getDesignation()) {
                        Extension caseSensitivityExtension = designation.getExtension().get(0);
                        Extension acceptabilityExtension = designation.getExtension().get(1);
                        CodeableConcept designationCaseSensitivityCodeableConcept = (CodeableConcept) caseSensitivityExtension.getValue();
                        CodeableConcept designationAcceptabilityCodeableConcept = (CodeableConcept) acceptabilityExtension.getValue();
                        //Coding useCoding = designation.getUse();
                        session.compose(new Synonym()
                                        .language(FhirUtils.generateLanguage(designation.getLanguage()))
                                        .text(designation.getValue())
                                        .caseSignificance(FhirUtils.generateCaseSignificance(designationCaseSensitivityCodeableConcept.getCoding().get(0).getCode())), conceptId)
                                .attach(new USDialect()
                                        .acceptability(FhirUtils.generateAcceptability(designationAcceptabilityCodeableConcept.getCoding().getLast().getCode())));
                        composer.commitSession(session);
                    }

                        /*for (CodeSystem.ConceptPropertyComponent property : concept.getProperty()) {
                            String propertyCode= property.getCode();
                            StringType propertyString=property.getValueStringType();
                            for (Extension propertyExtensions: property.getExtension()) {
                                    //create semantic here, composer api
                                *//*EntityProxy.Concept finalConceptId1 = conceptId;
                                session.compose((SemanticAssembler semanticAssembler) -> semanticAssembler
                                        .reference(finalConceptId1)
                                        .pattern(TinkarTerm.OWL_AXIOM_SYNTAX_PATTERN)
                                        .fieldValues(fieldValues -> fieldValues
                                                .with()
                                                //synonym
                                                .with(FhirUtils.generateNameType(REGULAR_NAME_DESCRIPTION_TYPE_SNOMEDID))
                                                .with(TinkarTerm.RE))
                                        .attach((USDialect dialect) -> dialect
                                                .acceptability(FhirUtils.generateAcceptability())));*//*
                                String url=propertyExtensions.getUrl();
                                if (url.equals(DEFINING_RELATIONSHIP_TYPE_URL) || url.equals(EL_PROFILE_SET_OPERATOR_URL)){
                                    Coding coding=FhirUtils.getCodingByURL(url);
                                    //create pattern here using composer api
                                    if (url.equals(ROLE_GROUP_URL)){
                                        //role group code
                                    }
                                }
                            }

                        }*/


                }
            }

            return session;
        }

        return session;

    }
}
