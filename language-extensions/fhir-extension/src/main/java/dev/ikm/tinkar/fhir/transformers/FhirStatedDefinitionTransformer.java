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
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.terms.EntityProxy;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.hl7.fhir.r4.model.CodeSystem;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

import static dev.ikm.tinkar.fhir.transformers.FhirConstants.*;
import static dev.ikm.tinkar.fhir.transformers.FhirUtils.generateCodingObject;
import static dev.ikm.tinkar.fhir.transformers.FhirUtils.retrieveConcept;

public class FhirStatedDefinitionTransformer {
    private static final Logger LOG = LoggerFactory.getLogger(FhirStatedDefinitionTransformer.class);
    String relationshipSnomedId;
    String definedSnomedId;
    StampCalculator stampCalculator = null;

    public FhirStatedDefinitionTransformer(StampCalculator stampCalculator) {
        this.stampCalculator = stampCalculator;
    }

    public List<CodeSystem.ConceptPropertyComponent> transfromAxiomSemantics(SemanticEntity<SemanticEntityVersion> statedSemanticEntity, EntityProxy.Pattern elPlusPlusAxiomsPattern) {
        Latest<SemanticEntityVersion> latestStatedSemanticVersion = stampCalculator.latest(statedSemanticEntity);
        List<CodeSystem.ConceptPropertyComponent> statedAxiomProperties = new ArrayList<>();
        latestStatedSemanticVersion.ifPresent((statedSemanticVersion) ->{
            if(statedSemanticVersion.fieldValues().get(0) instanceof DiTreeEntity diTreeEntity){
            /*    if(EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.equals(elPlusPlusAxiomsPattern)){
                    relationshipSnomedId = STATED_RELATIONSHIP_SNOMEDID;
                    definedSnomedId = SUFFICIENTLY_DEFINED_SNOMEDID;
                    generateConceptRootDefinition(statedAxiomProperties, statedSemanticEntity, "Is a");
                }else{
                    relationshipSnomedId = INFERRED_RELATIONSHIP_SNOMEDID;
                    definedSnomedId = NOT_SUFFICIENTLY_DEFINED_SNOMEDID;
                }*/
                processVertexMap(statedAxiomProperties, diTreeEntity);
            }
        });
        return statedAxiomProperties;
    }

    private void processVertexMap(List<CodeSystem.ConceptPropertyComponent> statedAxiomProperties, DiTreeEntity diTreeEntity) {
            processVertex(statedAxiomProperties, diTreeEntity, diTreeEntity.vertexMap().get(0));
    }

    private void processVertex(List<CodeSystem.ConceptPropertyComponent> statedAxiomProperties, DiTreeEntity diTreeEntity, EntityVertex entityVertex) {
        if(LOG.isDebugEnabled()){
            LOG.debug("Processing entity vertex : {} ", entityVertex);
        }
        if(PublicId.equals(TinkarTerm.SUFFICIENT_SET.publicId(), entityVertex.meaning().publicId())){
            relationshipSnomedId = STATED_RELATIONSHIP_SNOMEDID;
            definedSnomedId = SUFFICIENTLY_DEFINED_SNOMEDID;
        }else if(PublicId.equals(TinkarTerm.NECESSARY_SET.publicId(), entityVertex.meaning().publicId())){
            relationshipSnomedId = INFERRED_RELATIONSHIP_SNOMEDID;
            definedSnomedId = NOT_SUFFICIENTLY_DEFINED_SNOMEDID;
        }
        ImmutableIntList nextVertices = diTreeEntity.successorMap().get(entityVertex.vertexIndex());
        if(nextVertices == null){
            diTreeEntity.predecessor(entityVertex).ifPresent(parentEntityVertex -> {
               String code =  processLeafNode(diTreeEntity, entityVertex);
                generateJsonStatedComponent(statedAxiomProperties, entityVertex, code );
            });
        }else{
            if(LOG.isDebugEnabled()){
                LOG.debug("{} Next Vertex -> {}",entityVertex.vertexIndex(), nextVertices);
            }
            nextVertices.forEach(vertex-> {
                processVertex(statedAxiomProperties, diTreeEntity, diTreeEntity.vertexMap().get(vertex));
            });
        }
    }

    private String processLeafNode(DiTreeEntity diTreeEntity, EntityVertex entityVertex) {
        StringBuilder stringBuilder = new StringBuilder();
        diTreeEntity.predecessor(entityVertex).ifPresent(parentVertex -> {
            if(PublicId.equals(TinkarTerm.AND.publicId(), parentVertex.meaning().publicId())){
                stringBuilder.append(processLeafNode(diTreeEntity, parentVertex));
            } else  if(PublicId.equals(TinkarTerm.CONCEPT_REFERENCE.publicId(), parentVertex.meaning().publicId())){
                stringBuilder.append(processLeafNode(diTreeEntity, parentVertex));
            } else  if(PublicId.equals(TinkarTerm.SUFFICIENT_SET.publicId(), parentVertex.meaning().publicId())
                || (PublicId.equals(TinkarTerm.NECESSARY_SET.publicId(), parentVertex.meaning().publicId()))){
                stringBuilder.append("Is a");
            }else if(PublicId.equals(TinkarTerm.ROLE.publicId(), parentVertex.meaning().publicId())){
                parentVertex.properties().values().forEach(value -> {
                    EntityProxy.Concept concept = (EntityProxy.Concept) value;
                    if(!concept.description().toLowerCase().contains("restriction")){
                        stringBuilder.append(concept.description());
                    }
                });
            }
        });
        return stringBuilder.toString();
    }

    private void generateJsonStatedComponent(List<CodeSystem.ConceptPropertyComponent> statedAxiomProperties, EntityVertex entityVertex, String code) {
        if(LOG.isDebugEnabled()){
            LOG.debug("statedAxiomProperties {}", statedAxiomProperties);
            LOG.debug("entityVertex {}", entityVertex);
            LOG.debug("Code {}", code);
        }
        if(code != null && !code.isBlank()){
            CodeSystem.ConceptPropertyComponent statedAxiomProperty = generteConceptPropertyComponent(code);
            statedAxiomProperties.add(statedAxiomProperty);
            entityVertex.properties().values().forEach(value -> {
                EntityProxy.Concept concept = (EntityProxy.Concept) value;
                PublicId publicId = concept.publicId();
                Coding coding = new Coding();
                statedAxiomProperty.setValue(coding);
                retrieveConcept(stampCalculator, publicId, (snomedCTCode, referencedEntity)->{
                    coding.setSystem(SNOMEDCT_URL);
                    coding.setCode(snomedCTCode);
                    coding.setDisplay(referencedEntity.description());
                });
                if(coding.getSystem() == null){
                    coding.setSystem("TBD...");
                    coding.setCode(concept.publicId().asUuidArray()[0].toString());
                    coding.setDisplay(concept.description());
                }
            });
        }
    }

    private CodeSystem.ConceptPropertyComponent generteConceptPropertyComponent(String code){
        CodeSystem.ConceptPropertyComponent statedAxiomProperty = new CodeSystem.ConceptPropertyComponent();
        statedAxiomProperty.addExtension(generateExtension(DEFINING_RELATIONSHIP_TYPE_URL, relationshipSnomedId));
        statedAxiomProperty.addExtension(generateExtension(EL_PROFILE_SET_OPERATOR_URL, definedSnomedId));
        statedAxiomProperty.addExtension(FhirUtils.generateRoleGroup(0));
        statedAxiomProperty.setCode(code);
        return statedAxiomProperty;
    }

    private void generateConceptRootDefinition(List<CodeSystem.ConceptPropertyComponent> statedAxiomProperties, SemanticEntity<SemanticEntityVersion> statedSemanticEntity, String code) {
        CodeSystem.ConceptPropertyComponent statedAxiomProperty = generteConceptPropertyComponent(code);
        statedAxiomProperties.add(statedAxiomProperty);
        PublicId referencedComponenetPublicId = EntityService.get().getEntityFast(statedSemanticEntity.referencedComponentNid()).publicId();
        retrieveConcept(stampCalculator, referencedComponenetPublicId, (snomedCTCode, referencedEntity)->{
            Coding coding = new Coding();
            statedAxiomProperty.setValue(coding);
            coding.setSystem(SNOMEDCT_URL);
            coding.setCode(snomedCTCode);
            coding.setDisplay(referencedEntity.description());
        });
    }

    private Extension generateExtension(String definingRelationshipTypeUrl, String statedRelationshipSnomedid) {
        Extension statedAxiomExtension = new Extension();
        statedAxiomExtension.setUrl(definingRelationshipTypeUrl);
        CodeableConcept statedAxiomCodeableConcept = new CodeableConcept();
        statedAxiomExtension.setValue(statedAxiomCodeableConcept);
        statedAxiomCodeableConcept.addCoding(generateCodingObject(stampCalculator, statedRelationshipSnomedid));
        return statedAxiomExtension;
    }
}
