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
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.composer.Composer;
import dev.ikm.tinkar.composer.Session;
import dev.ikm.tinkar.composer.assembler.ConceptAssembler;
import dev.ikm.tinkar.composer.template.AxiomSyntax;
import dev.ikm.tinkar.composer.template.FullyQualifiedName;
import dev.ikm.tinkar.composer.template.USDialect;
import dev.ikm.tinkar.entity.EntityCountSummary;
import dev.ikm.tinkar.entity.transaction.Transaction;
import dev.ikm.tinkar.ext.lang.owl.Rf2OwlToLogicAxiomTransformer;
import dev.ikm.tinkar.fhir.transformers.provenance.FhirProvenanceTransform;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.State;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.hl7.fhir.r4.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

import static dev.ikm.tinkar.fhir.transformers.FhirConstants.*;

public class LoadEntitiesFromFhirJson extends TrackingCallable<EntityCountSummary> {
    private static final Logger LOG = LoggerFactory.getLogger(LoadEntitiesFromFhirJson.class);
    private File importFile;
    private CodeSystem codeSystem;
    private FhirContext ctx;
    private IParser parser;
    private Date fromDate;
    private Date toDate;
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
        parser = ctx.newJsonParser();
        this.codeSystem = new CodeSystem();
    }

    @Override
    protected EntityCountSummary compute() throws Exception {
        return null;
    }

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
        String jsonBundle = parser.setPrettyPrint(true).encodeResourceToString(bundle);
        bundle = parseJsonBundle(jsonBundle, codeSystem);

        EntityProxy.Concept conceptId = null;

        Session session = null;
        for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
            Resource resource = entry.getResource();
            if (resource instanceof CodeSystem) {
                codeSystem = (CodeSystem) resource;

                State status = State.ACTIVE;
                long time = codeSystem.getMeta().getLastUpdated().getTime();
                EntityProxy.Concept author = TinkarTerm.USER;
                EntityProxy.Concept module = TinkarTerm.DEVELOPMENT_MODULE;
                EntityProxy.Concept path = TinkarTerm.DEVELOPMENT_PATH;
                Composer composer = new Composer("FHIR Concept Composer...");
                session = composer.open(status, time, author, module, path);
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
                    EntityProxy.Concept finalConceptId1 = conceptId;
                    Session designationSession = session;
                    concept.getDesignation().forEach(designation -> {
                        Extension caseSensitivityExtension = designation.getExtension().get(0);
                        Extension acceptabilityExtension = designation.getExtension().get(1);
                        CodeableConcept designationCaseSensitivityCodeableConcept = (CodeableConcept) caseSensitivityExtension.getValue();
                        CodeableConcept designationAcceptabilityCodeableConcept = (CodeableConcept) acceptabilityExtension.getValue();
                        //Coding useCoding = designation.getUse();
                        EntityProxy.Concept finalConceptId = finalConceptId1;
                        designationSession.compose((ConceptAssembler conceptAssembler) -> conceptAssembler.concept(finalConceptId)
                                .attach((FullyQualifiedName fqn) -> fqn
                                        .language(FhirUtils.generateLanguage(designation.getLanguage()))
                                        .text(designation.getValue())
                                        .caseSignificance(FhirUtils.generateCaseSignificance(designationCaseSensitivityCodeableConcept.getCoding().get(0).getCode()))
                                        .attach((USDialect dialect) -> dialect
                                                .acceptability(FhirUtils.generateAcceptability(designationAcceptabilityCodeableConcept.getCoding().getLast().getCode())))));
                        composer.commitSession(designationSession);
                    });
                    //property axiom transform
                    EntityProxy.Concept finalConceptId = conceptId;
                    Session axiomSession = session;
                    concept.getProperty().subList(0, concept.getProperty().size()).forEach(property -> {
                        if (property.getExtension() != null && !property.getCode().equals(FhirConstants.STATUS)) {
                            if (property.getValue() instanceof StringType) {
                                String propertyOwlSyntax = property.getValueStringType().getValue();
                                axiomSession.compose(new AxiomSyntax().text(propertyOwlSyntax), finalConceptId);
                                Transaction owlTransformationTransaction = Transaction.make();
                                try {
                                    new Rf2OwlToLogicAxiomTransformer(
                                            owlTransformationTransaction,
                                            AXIOM_SYNTAX_PATTERN,
                                            TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN).call();
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                                composer.commitSession(axiomSession);
                            }
                        }
                    });
                }
            }
        }
        assert session != null;
        LOG.info("Generating concepts: " + session);
        return session;
    }
}
