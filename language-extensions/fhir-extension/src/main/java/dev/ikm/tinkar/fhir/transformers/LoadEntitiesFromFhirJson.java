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
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.composer.Composer;
import dev.ikm.tinkar.composer.Session;
import dev.ikm.tinkar.composer.assembler.ConceptAssembler;
import dev.ikm.tinkar.composer.template.FullyQualifiedName;
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

import static dev.ikm.tinkar.fhir.transformers.FhirConstants.*;

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

    public EntityProxy.Concept generateLanguage(String language){
        return switch (language) {
            case "en-GB" -> TinkarTerm.GB_ENGLISH_DIALECT;
            case "en-US" -> TinkarTerm.ENGLISH_LANGUAGE;
            default -> throw new IllegalArgumentException("Unsupported language code: "+language);
        };
    }

    public EntityProxy.Concept generateDisplay(String display){
        return switch (display) {
            case "Case insensitive" -> TinkarTerm.DESCRIPTION_CASE_SENSITIVE;
            case "Preferred" -> TinkarTerm.PREFERRED;
            case "Fully qualified name" -> TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE;
            case "Regular name description type" -> TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE;
            default -> throw new IllegalArgumentException("Unsupported display code: "+display);
        };
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
                            if (SNOMEDCT_URL.equals(system) || IKM_DEV_URL.equals(system)) {
                                EntityProxy.Concept identifierConcept = EntityProxy.Concept.make(value);

                            }
                        }
                    }

                    for (CodeSystem.ConceptDefinitionDesignationComponent designation : concept.getDesignation()) {
                        for (Extension designationExtension : designation.getExtension()) {
                            String url = designationExtension.getUrl();
                            Coding coding = FhirUtils.getCodingByURL(url);
                            if (SNOMEDCT_URL.equals(coding.getSystem())) {
                                session.compose((ConceptAssembler conceptAssembler) -> conceptAssembler
                                        .attach((FullyQualifiedName fqn) -> fqn
                                                .language(generateLanguage(designation.getLanguage()))
                                                .text(designationExtension.getValue().toString())
                                                .caseSignificance(generateDisplay(coding.getDisplay()))));
                                composer.commitSession(session);
                            }

                            if (designation.hasLanguage()){
                                String language=designation.getLanguage();
                                Coding languageCoding=FhirUtils.getUseByLanguage(language);
                                session.compose((ConceptAssembler conceptAssembler) -> conceptAssembler
                                        .attach((FullyQualifiedName fqn) -> fqn
                                                .language(generateLanguage(designation.getLanguage()))
                                                .text(designationExtension.getValue().toString())
                                                .caseSignificance(generateDisplay(languageCoding.getDisplay()))));
                                composer.commitSession(session);
                            }

                        }

                        for (CodeSystem.ConceptPropertyComponent property : concept.getProperty()) {
                            String propertyCode= property.getCode();
                            StringType propertyString=property.getValueStringType();
                            for (Extension propertyExtensions: property.getExtension()) {
                                    Coding propertyCoding=FhirUtils.getCodingByCode(propertyCode);
                                    //create pattern here, composer api
                                String url=propertyExtensions.getUrl();
                                if (url.equals(DEFINING_RELATIONSHIP_TYPE_URL) || url.equals(EL_PROFILE_SET_OPERATOR_URL)){
                                    Coding coding=FhirUtils.getCodingByURL(url);
                                    //create pattern here using composer api
                                    if (url.equals(ROLE_GROUP_URL)){
                                        //role group code
                                    }
                                }
                            }

                        }

                    /*for (CodeSystem.ConceptDefinitionDesignationComponent designationComponent: concept.getDesignation()) {
                        List<Extension> extension=designationComponent.getExtension();

                            designationComponent.getExtension().forEach(designationExtensions -> {
                                if (designationExtensions.getUrl().equals(FhirConstants.DESCRIPTION_CASE_SENSITIVITY_URL) || designationExtensions.getUrl().equals(FhirConstants.DESCRIPTION_ACCEPTABILITY_URL)){
                                    if (designationExtensions.getValue() instanceof CodeableConcept codeableConcept){
                                    codeableConcept.getCoding().forEach(designationCoding -> {
                                        if (designationCoding.getSystem().equals(SNOMEDCT_URL)){
                                            //figure out correct impl to put in here, go in komet to look at how fqn is made
                                            EntityProxy.Concept designationConcept = EntityProxy.Concept.make(designationCoding.getDisplay(),UUID.fromString(designationCoding.getCode()));
                                            session.composeConcept(designationConcept);
                                            if (designationComponent.hasLanguage() && designationComponent.hasExtension(FhirConstants.DESCRIPTION_ACCEPTABILITY_URL)){
                                                Extension acceptabilityExtension= designationComponent.getExtensionByUrl(FhirConstants.DESCRIPTION_ACCEPTABILITY_URL);
                                                if (acceptabilityExtension != null && acceptabilityExtension.getValue() instanceof CodeableConcept acceptabilityConcept){
                                                    //set the language for tinkar description "en-US" or "en-GB"
                                                    for (Coding coding: acceptabilityConcept.getCoding()) {
                                                        if (designationComponent.getLanguage().equals("en-US")) {
                                                            EntityProxy.Concept acceptabilityUS = EntityProxy.Concept.make(coding.getDisplay(), UUID.nameUUIDFromBytes(coding.getDisplay().getBytes()));
                                                            session.composeConcept(acceptabilityUS)
                                                                    .with(new FullyQualifiedName(EntityProxy.Semantic.make(PublicIds.newRandom()), TinkarTerm.ENGLISH_LANGUAGE, acceptabilityExtension.getValue().toString(), TinkarTerm.DESCRIPTION_ACCEPTABILITY)
                                                                            .with(new USEnglishDialect(EntityProxy.Semantic.make(PublicIds.newRandom()), TinkarTerm.PREFERRED)));
                                                        } else if (designationComponent.getLanguage().equals("en-GB")) {
                                                            EntityProxy.Concept acceptabilityGB = EntityProxy.Concept.make(coding.getDisplay(), UUID.nameUUIDFromBytes(coding.getDisplay().getBytes()));
                                                            session.composeConcept(acceptabilityGB)
                                                                    .with(new FullyQualifiedName(EntityProxy.Semantic.make(PublicIds.newRandom()), TinkarTerm.GB_ENGLISH_DIALECT, acceptabilityExtension.getValue().toString(), TinkarTerm.DESCRIPTION_ACCEPTABILITY)
                                                                            .with(new USEnglishDialect(EntityProxy.Semantic.make(PublicIds.newRandom()), TinkarTerm.PREFERRED)));
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    });
                                }
                            };
                        });
                    ;}*/
                    }
                }
            }

            return session;
        }

   /* public ComposerSession transformIdentifier(Bundle bundle) throws IOException{
        ComposerSession session = composerSession();
        this.codeSystem.getConcept();
        Provenance provenance = FhirProvenanceTransform.provenanceTransform("CodeSystem/"+codeSystem.getId(), fromDate, toDate);
        bundle.setType(Bundle.BundleType.TRANSACTION);
        bundle.addEntry()
                .setResource(codeSystem)
                .setFullUrl(codeSystem.getResourceType().name() + "/" + codeSystem.getIdElement().getValue());
        bundle.addEntry()
                .setResource(provenance)
                .setFullUrl(provenance.getResourceType().name() + "/" +provenance.getIdElement().getValue());

        FhirContext ctx = FhirContext.forR4();
        IParser parser = ctx.newJsonParser();

        String bundleJson = parser.setPrettyPrint(true).encodeResourceToString(bundle);

        Identifier identifier = bundle.getIdentifier();
        identifier.getExtension().forEach(identifierExtension -> {
            if (identifierExtension.getUrl().equals(CODE_CONCEPT_ADDITIONAL_IDENTIFIER_URL) && identifier.getSystem().equals(FhirConstants.IKM_DEV_URL)){
                session.composeSemantic(EntityProxy.Semantic.make(identifier.getExtension().getFirst().getValue().toString()),
                        EntityProxy.Concept.make(identifier.getExtension().getFirst().getValue().toString()),
                        TinkarTerm.DESCRIPTION_PATTERN,
                        Lists.immutable.of(
                                TinkarTerm.DESCRIPTION_ACCEPTABILITY));
            } else if (identifierExtension.getUrl().equals(CODE_CONCEPT_ADDITIONAL_IDENTIFIER_URL) && identifier.getSystem().equals(SNOMEDCT_URL)) {
                session.composeSemantic(EntityProxy.Semantic.make(identifier.getExtension().getLast().getValue().toString()),
                        EntityProxy.Concept.make(identifier.getExtension().getLast().getValue().toString()),
                        TinkarTerm.DESCRIPTION_PATTERN,
                        Lists.immutable.of(
                                TinkarTerm.DESCRIPTION_ACCEPTABILITY));
            }
        });
        if (identifier.getExtension().getFirst().getUrl().equals(CODE_CONCEPT_ADDITIONAL_IDENTIFIER_URL) && identifier.getSystem().equals(FhirConstants.IKM_DEV_URL)) {
            //code is publicID

        } else if (identifier.getExtension().getFirst().getUrl().equals(CODE_CONCEPT_ADDITIONAL_IDENTIFIER_URL) && identifier.getSystem().equals(FhirConstants.SNOMEDCT_URL)) {
            //code is snomedID
            session.composeSemantic(EntityProxy.Semantic.make(identifier.getExtension().getLast().getValue().toString()),
                    EntityProxy.Concept.make(identifier.getExtension().getLast().getValue().toString()),
                    TinkarTerm.DESCRIPTION_PATTERN,
                    Lists.immutable.of(
                            TinkarTerm.DESCRIPTION_ACCEPTABILITY));
        }
            return session;
    }*/
        return session;

    }
}
