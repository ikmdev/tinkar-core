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
import dev.ikm.tinkar.composer.ComposerSession;
import dev.ikm.tinkar.entity.EntityCountSummary;
import dev.ikm.tinkar.fhir.transformers.provenance.FhirProvenanceTransform;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.State;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.factory.Lists;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Provenance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Date;
import java.util.concurrent.atomic.AtomicLong;

import static dev.ikm.tinkar.fhir.transformers.FhirConstants.CODE_CONCEPT_ADDITIONAL_IDENTIFIER_URL;

public class LoadEntitiesFromFhirJson extends TrackingCallable<EntityCountSummary> {
    private static final Logger LOG = LoggerFactory.getLogger(LoadEntitiesFromFhirJson.class.getName());
    private final File importFile;
    private CodeSystem codeSystem;
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
        LOG.info("Loading entities from: " + importFile.getAbsolutePath());
    }
    @Override
    protected EntityCountSummary compute() throws Exception {
        State status = State.ACTIVE;
        long time = PrimitiveData.PREMUNDANE_TIME;
        EntityProxy.Concept author = TinkarTerm.USER;
        EntityProxy.Concept module = TinkarTerm.PRIMORDIAL_MODULE;
        EntityProxy.Concept path = TinkarTerm.PRIMORDIAL_PATH;
        ComposerSession session = new ComposerSession(status, time, author, module, path);

        Provenance provenance = FhirProvenanceTransform.provenanceTransform("CodeSystem/"+codeSystem.getId(), fromDate, toDate);
        Bundle bundle = new Bundle();
        bundle.setType(Bundle.BundleType.TRANSACTION);
        bundle.addEntry()
                .setResource(codeSystem)
                .setFullUrl(codeSystem.getResourceType().name() + "/" + codeSystem.getIdElement().getValue());
        bundle.addEntry()
                .setResource(provenance)
                .setFullUrl(provenance.getResourceType().name() + "/" +provenance.getIdElement().getValue());

        FhirContext ctx = FhirContext.forR4();
        IParser parser = ctx.newJsonParser();

        String jsonBundle = parser.setPrettyPrint(true).encodeResourceToString(bundle);

        Identifier identifier=bundle.getIdentifier();

        if (identifier.getExtension().getFirst().getUrl().equals(CODE_CONCEPT_ADDITIONAL_IDENTIFIER_URL) && identifier.getSystem().equals(FhirConstants.IKM_DEV_URL)) {
                //code is publicID
                session.composeSemantic(EntityProxy.Semantic.make(identifier.getExtension().getFirst().getValue().toString()),
                                EntityProxy.Concept.make(identifier.getExtension().getFirst().getValue().toString()),
                                TinkarTerm.DESCRIPTION_PATTERN,
                                Lists.immutable.of(
                                        TinkarTerm.DESCRIPTION_ACCEPTABILITY));
            }else if (identifier.getExtension().getFirst().getUrl().equals(CODE_CONCEPT_ADDITIONAL_IDENTIFIER_URL) && identifier.getSystem().equals(FhirConstants.SNOMEDCT_URL)) {
                //code is snomedID
            session.composeSemantic(EntityProxy.Semantic.make(identifier.getExtension().getLast().getValue().toString()),
                    EntityProxy.Concept.make(identifier.getExtension().getLast().getValue().toString()),
                    TinkarTerm.DESCRIPTION_PATTERN,
                    Lists.immutable.of(
                            TinkarTerm.DESCRIPTION_ACCEPTABILITY));
            }

        return null;
    }
}
