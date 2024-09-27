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
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.entity.*;
import dev.ikm.tinkar.fhir.transformers.provenance.FhirProvenanceTransform;
import dev.ikm.tinkar.terms.State;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static dev.ikm.tinkar.fhir.transformers.FhirConstants.*;
import static dev.ikm.tinkar.fhir.transformers.FhirUtils.generateCodingObject;
import static dev.ikm.tinkar.terms.State.ACTIVE;
import static dev.ikm.tinkar.terms.State.INACTIVE;
import static dev.ikm.tinkar.terms.TinkarTerm.EL_PLUS_PLUS_INFERRED_AXIOMS_PATTERN;
import static dev.ikm.tinkar.terms.TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN;

public class FhirCodeSystemTransform extends TrackingCallable<Void> {

    private static final Logger LOG = LoggerFactory.getLogger(FhirCodeSystemTransform.class);
    public StampCalculator stampCalculatorWithCache;
    private final Stream<ConceptEntity<? extends ConceptEntityVersion>> concepts;
    private final CodeSystem codeSystem;
    private final Consumer<String> fhirAndProvenanceJson;
    private Date fromDate;
    private Date toDate;
    private Date oldestOfTheLatestDate;
    private Date latestOfTheLatestDate;


    public FhirCodeSystemTransform(StampCalculator stampCalculator, List<ConceptEntity<? extends ConceptEntityVersion>> concepts, Consumer<String> fhirAndProvenanceJson) {
        this(stampCalculator, concepts.stream(), fhirAndProvenanceJson);
    }

    public FhirCodeSystemTransform(StampCalculator stampCalculator, Stream<ConceptEntity<? extends ConceptEntityVersion>> concepts, Consumer<String> fhirAndProvenanceJson) {
        this.fhirAndProvenanceJson = fhirAndProvenanceJson;
        this.stampCalculatorWithCache = stampCalculator;
        this.codeSystem = new CodeSystem();
        this.concepts = concepts;
    }

    public FhirCodeSystemTransform(long fromDate, long toDate, StampCalculator stampCalculator,
                                   Stream<ConceptEntity<? extends ConceptEntityVersion>> concepts, Consumer<String> fhirAndProvenanceJson) {
        this.fhirAndProvenanceJson = fhirAndProvenanceJson;
        this.stampCalculatorWithCache = stampCalculator;
        this.codeSystem = new CodeSystem();
        this.concepts = concepts;
        this.fromDate = new Date(fromDate);
        this.toDate = new Date(toDate);
    }

    private void retrieveOldestOfLatest(StampEntity<?> stampEntity) {
        StampEntityVersion latestVersion = stampCalculatorWithCache.latest(stampEntity).get();
        long latestVersionTime = latestVersion.stamp().time();
        Date date = new Date(latestVersionTime);
        if (oldestOfTheLatestDate == null || oldestOfTheLatestDate.after(date)) {
            oldestOfTheLatestDate = date;
        }
    }

    private void retrieveLatestDate(StampEntity<?> stampEntity) {
        StampEntityVersion latestVersion = stampCalculatorWithCache.latest(stampEntity).get();
        long latestVersionTime = latestVersion.stamp().time();
        Date date = new Date(latestVersionTime);
        if (latestOfTheLatestDate == null || latestOfTheLatestDate.before(date)) {
            latestOfTheLatestDate = date;
        }
    }


    private void forEachSemanticForComponent(int conceptNid) {
        Latest<EntityVersion> latestConceptVersion = stampCalculatorWithCache.latest(conceptNid);
        latestConceptVersion.ifPresent(conceptEntity -> {
            retrieveOldestOfLatest(conceptEntity.stamp());
            retrieveLatestDate(conceptEntity.stamp());
            CodeSystem.ConceptDefinitionComponent concept = new CodeSystem.ConceptDefinitionComponent();
            codeSystem.addConcept(concept);

            FhirUtils.retrieveConcept(stampCalculatorWithCache, latestConceptVersion.get().publicId(),
                    (code, entity) -> concept.setCode(code));

            EntityService.get().forEachSemanticForComponent(conceptEntity.nid(), semanticEntity -> {
                Latest<SemanticEntityVersion> latestSemanticVersion = stampCalculatorWithCache.latest(semanticEntity);
                latestSemanticVersion.ifPresent(semanticEntityVersion -> {
                    retrieveOldestOfLatest(semanticEntityVersion.stamp());
                    retrieveLatestDate(semanticEntityVersion.stamp());
                    if (semanticEntityVersion.patternNid() == PrimitiveData.get().nidForPublicId(IDENTIFIER_PATTERN.publicId())) {
                        FhirIdentifierTransform fhirIdentifierTransform = new FhirIdentifierTransform();
                        List<Extension> identifierExtensions = fhirIdentifierTransform.transformIdentifierSemantic(semanticEntityVersion);
                        concept.getExtension().addAll(identifierExtensions);
                    } else if (semanticEntityVersion.patternNid() == PrimitiveData.get().nidForPublicId(TinkarTerm.DESCRIPTION_PATTERN.publicId())) {
                        List<CodeSystem.ConceptDefinitionDesignationComponent> designations = concept.getDesignation();
                        FhirDescriptionTransformation descriptionTransformation = new FhirDescriptionTransformation(stampCalculatorWithCache);
                        designations.addAll(descriptionTransformation.transformDescription(semanticEntity));
                    } else if (semanticEntityVersion.patternNid() == PrimitiveData.get().nidForPublicId(AXIOM_SYNTAX_PATTERN.publicId())) {
                        CodeSystem.ConceptPropertyComponent axiomProperty = new CodeSystem.ConceptPropertyComponent();
                        concept.addProperty(axiomProperty);
                        axiomProperty.setCode(AXIOM_SYNTAX_PATTERN.description());
                        StringType stringType = new StringType();
                        stringType.setValue(semanticEntityVersion.fieldValues().get(0).toString());
                        axiomProperty.setValue(stringType);
                    } else if (semanticEntityVersion.patternNid() == PrimitiveData.get().nidForPublicId(EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.publicId())) {
                        FhirStatedDefinitionTransformer fhirStatedDefinitionTransformer = new FhirStatedDefinitionTransformer(stampCalculatorWithCache);
                        concept.getProperty().addAll(fhirStatedDefinitionTransformer.transfromAxiomSemantics(semanticEntity, EL_PLUS_PLUS_STATED_AXIOMS_PATTERN));
                    } else if (semanticEntityVersion.patternNid() == PrimitiveData.get().nidForPublicId(EL_PLUS_PLUS_INFERRED_AXIOMS_PATTERN.publicId())) {
                        FhirStatedDefinitionTransformer fhirStatedDefinitionTransformer = new FhirStatedDefinitionTransformer(stampCalculatorWithCache);
                        concept.getProperty().addAll(fhirStatedDefinitionTransformer.transfromAxiomSemantics(semanticEntity, EL_PLUS_PLUS_INFERRED_AXIOMS_PATTERN));
                    }
                });
            });
            concept.addProperty(generateStatusProperty(latestConceptVersion));
        });
    }

    private CodeSystem.ConceptPropertyComponent generateStatusProperty(Latest<EntityVersion> latestConceptVersion) {
        CodeSystem.ConceptPropertyComponent property = new CodeSystem.ConceptPropertyComponent();
        property.setCode(STATUS);
        StampEntity<StampEntityVersion> stampEntity = EntityService.get().getEntityFast(latestConceptVersion.get().stampNid());
        State state = stampCalculatorWithCache.latest(stampEntity).get().state();
        Coding coding = null;
        if (state == ACTIVE) {
            coding = generateCodingObject(stampCalculatorWithCache, ACTIVE_VALUE_SNOMEDID);
        } else if (state == INACTIVE) {
            coding = generateCodingObject(stampCalculatorWithCache, INACTIVE_VALUE_SNOMEDID);
        }
        property.setValue(coding);
        return property;
    }


    @Override
    public Void compute() throws Exception {
        if (stampCalculatorWithCache == null) {
            throw new ExceptionInInitializerError("Stamp Calculator has not been initialized.");
        }
        FhirStaticData.generateCodeSystemExtensionContent(codeSystem);
        FhirStaticData.generateCodeSystemIdentifierContent(codeSystem);
        FhirStaticData.generateCodeSystemContactContent(codeSystem);
        FhirStaticData.generateCodeSystemFilterContent(codeSystem);
        FhirStaticData.generateCodeSystemPropertyContent(codeSystem);

        concepts.forEach(concept -> {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Processing Concept : {}", concept);
            }
            forEachSemanticForComponent(concept.nid());
        });

        Provenance provenance = FhirProvenanceTransform.provenanceTransform(CODESYSTEM + codeSystem.getId(), fromDate, toDate);
        Bundle.BundleEntryRequestComponent codeSystemRequest = new Bundle.BundleEntryRequestComponent();
        codeSystemRequest.setMethod(Bundle.HTTPVerb.POST)
                .setUrl("CodeSystem");

        Bundle.BundleEntryRequestComponent provenanceRequest = new Bundle.BundleEntryRequestComponent();
        provenanceRequest.setMethod(Bundle.HTTPVerb.POST)
                .setUrl("Provenance");

        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.TRANSACTION);
        bundle.addEntry()
                .setResource(codeSystem)
                .setFullUrl(codeSystem.getResourceType().name() + "/" + codeSystem.getIdElement().getValue())
                .setRequest(codeSystemRequest);

        bundle.addEntry()
                .setResource(provenance)
                .setFullUrl(provenance.getResourceType().name() + "/" + provenance.getIdElement().getValue())
                .setRequest(provenanceRequest);

        FhirContext ctx = FhirContext.forR4();
        IParser parser = ctx.newJsonParser();

        String bundleJson = parser.setPrettyPrint(true).encodeResourceToString(bundle);
        if (LOG.isDebugEnabled()) {
            LOG.debug(bundleJson);
        }
        fhirAndProvenanceJson.accept(bundleJson);
        return null;
    }
}


