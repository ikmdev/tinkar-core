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
package dev.ikm.tinkar.entity.graph.adaptor.axiom;

import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.terms.EntityProxy;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.set.ImmutableSet;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.TinkarTerm;

import java.util.UUID;

/**
 *
 */
public class LogicalExpressionBuilder {

    LogicalExpression logicalExpression;
    DiTreeEntity.Builder builder;
    final int rootIndex;

    public LogicalExpressionBuilder(UUID rootVertexUuid) {
        this.builder = DiTreeEntity.builder();
        this.logicalExpression = new LogicalExpression(builder);
        EntityVertex rootVertex = EntityVertex.make(rootVertexUuid, LogicalAxiomSemantic.DEFINITION_ROOT.nid);
        builder.addVertex(rootVertex);
        builder.setRoot(rootVertex);
        this.rootIndex = rootVertex.vertexIndex();
        new LogicalAxiomAdaptor.DefinitionRootAdaptor(logicalExpression, rootIndex);

    }
    public LogicalExpressionBuilder() {
        this(UUID.randomUUID());
    }

    public LogicalExpressionBuilder(LogicalExpression logicalExpression) {
        this.builder = DiTreeEntity.builder(logicalExpression.sourceGraph);
        this.rootIndex = logicalExpression.definitionRoot().vertexIndex();
        this.logicalExpression = new LogicalExpression(this.builder);
    }
    public LogicalExpressionBuilder(DiTreeEntity logicalExpressionTree) {
        this.builder = DiTreeEntity.builder(logicalExpressionTree);
        this.rootIndex = logicalExpressionTree.root().vertexIndex();
        this.logicalExpression = new LogicalExpression(this.builder);
    }
    public LogicalExpression build() {
        return logicalExpression.build();
    }

    public LogicalAxiom get(int axiomIndex) {
        return logicalExpression.adaptors.get(axiomIndex);
    }

    /**
     * NOTE: Not thread safe...
     * @param axiomToRemove
     * @return A newly constructed logical expression with the axiom recursively removed.
     */
    public LogicalExpressionBuilder removeAxiom(LogicalAxiom axiomToRemove) {
        if (this.rootIndex == axiomToRemove.vertexIndex()) {
            throw new IllegalStateException("Removing root vertex is not allowed. ");
        }
        DiTreeEntity axiomTree = this.builder.build();
        this.builder = axiomTree.removeVertex(axiomToRemove.vertexIndex());
        this.logicalExpression = new LogicalExpression(this.builder);
        return this;
    }

    public LogicalExpressionBuilder addToRoot(LogicalAxiom.LogicalSet logicalSet) {
        builder.addEdge(logicalSet.vertexIndex(), this.rootIndex);
        return this;
    }

    public LogicalAxiom.LogicalSet.SufficientSet SufficientSet(LogicalAxiom.Atom... elements) {
        return SufficientSet(UUID.randomUUID(), elements);
    }
    public LogicalAxiom.LogicalSet.SufficientSet SufficientSet(UUID vertexUuid, LogicalAxiom.Atom... elements) {
        EntityVertex sufficientSet = EntityVertex.make(vertexUuid, LogicalAxiomSemantic.SUFFICIENT_SET.nid);
        builder.addVertex(sufficientSet);
        builder.addEdge(sufficientSet.vertexIndex(), rootIndex);
        for (LogicalAxiom.Atom element : elements) {
            builder.addEdge(element.vertexIndex(), sufficientSet.vertexIndex());
        }
        return new LogicalAxiomAdaptor.SufficientSetAdaptor(logicalExpression, sufficientSet.vertexIndex());
    }
    public LogicalAxiom.LogicalSet.NecessarySet NecessarySet(LogicalAxiom.Atom... elements) {
        return NecessarySet(UUID.randomUUID(), elements);
    }

    public LogicalAxiom.LogicalSet.NecessarySet NecessarySet(UUID vertexUuid, LogicalAxiom.Atom... elements) {
        EntityVertex necessarySet = EntityVertex.make(vertexUuid, LogicalAxiomSemantic.NECESSARY_SET.nid);
        builder.addVertex(necessarySet);
        builder.addEdge(necessarySet.vertexIndex(), rootIndex);
        for (LogicalAxiom.Atom element : elements) {
            builder.addEdge(element.vertexIndex(), necessarySet.vertexIndex());
        }
        return new LogicalAxiomAdaptor.NecessarySetAdaptor(logicalExpression, necessarySet.vertexIndex());
    }

    public LogicalAxiom.LogicalSet.InclusionSet InclusionSet(LogicalAxiom.Atom... elements) {
        return InclusionSet(UUID.randomUUID(), elements);
    }
    public LogicalAxiom.LogicalSet.InclusionSet InclusionSet(UUID vertexUuid, LogicalAxiom.Atom... elements) {
        EntityVertex propertySet = EntityVertex.make(vertexUuid, LogicalAxiomSemantic.INCLUSION_SET.nid);
        builder.addVertex(propertySet);
        builder.addEdge(propertySet.vertexIndex(), rootIndex);
        for (LogicalAxiom.Atom element : elements) {
            builder.addEdge(element.vertexIndex(), propertySet.vertexIndex());
        }
        return new LogicalAxiomAdaptor.InclusionSetAdaptor(logicalExpression, propertySet.vertexIndex());
    }


    public LogicalAxiom.LogicalSet.PropertySet PropertySet(LogicalAxiom.Atom... elements) {
        return PropertySet(UUID.randomUUID(), elements);
    }
    public LogicalAxiom.LogicalSet.PropertySet PropertySet(UUID vertexUuid, LogicalAxiom.Atom... elements) {
        EntityVertex propertySet = EntityVertex.make(vertexUuid, LogicalAxiomSemantic.PROPERTY_SET.nid);
        builder.addVertex(propertySet);
        builder.addEdge(propertySet.vertexIndex(), rootIndex);
        for (LogicalAxiom.Atom element : elements) {
            builder.addEdge(element.vertexIndex(), propertySet.vertexIndex());
        }
        return new LogicalAxiomAdaptor.PropertySetAdaptor(logicalExpression, propertySet.vertexIndex());
    }

    public LogicalAxiom.Atom.Connective.And And(UUID vertexUuid, ImmutableList<? extends LogicalAxiom.Atom> atoms) {
        EntityVertex and = EntityVertex.make(vertexUuid, LogicalAxiomSemantic.AND.nid);
        builder.addVertex(and);
        for (LogicalAxiom.Atom atom : atoms) {
            builder.addEdge(atom.vertexIndex(), and.vertexIndex());
        }
        return new LogicalAxiomAdaptor.AndAdaptor(logicalExpression, and.vertexIndex());
    }

    public LogicalAxiom.Atom.Connective.And And(ImmutableList<? extends LogicalAxiom.Atom> atoms) {
        return And(UUID.randomUUID(), atoms);
    }


    public void addToSet(LogicalAxiom.LogicalSet setToAddTo, LogicalAxiom... axioms) {
        addToFirstAnd(setToAddTo.vertexIndex(), axioms);
    }

    private int findFirstAnd(int vertexIndex) {
        EntityVertex vertex = this.builder.vertex(vertexIndex);
        if (vertex.getMeaningNid() == TinkarTerm.AND.nid()) {
            return vertex.vertexIndex();
        } else {
            for (int successorIndex: this.builder.successors(vertexIndex).toArray()) {
                int andIndex = findFirstAnd(successorIndex);
                if (andIndex > -1) {
                    return andIndex;
                }
            }
        }
        return -1;
    }

    public void addToFirstAnd(int vertexIndex, LogicalAxiom... axioms) {
        int andIndex = findFirstAnd(vertexIndex);
        if (andIndex < 0) {
            throw new IllegalStateException("No and vertex at index or below. Index: " + vertexIndex + " in graph: " + this.builder.build());
        }
        for (LogicalAxiom axiom: axioms) {
            this.builder.addEdge(axiom.vertexIndex(), andIndex);
        }
    }


    public LogicalAxiom.Atom.Connective.And And(LogicalAxiom.Atom... atoms) {
        return And(UUID.randomUUID(), atoms);
    }

    public LogicalAxiom.Atom.Connective.And And(UUID vertexUuid, LogicalAxiom.Atom... atoms) {
        EntityVertex and = EntityVertex.make(vertexUuid, LogicalAxiomSemantic.AND.nid);
        builder.addVertex(and);
        for (LogicalAxiom.Atom atom : atoms) {
            builder.addEdge(atom.vertexIndex(), and.vertexIndex());
        }
        return new LogicalAxiomAdaptor.AndAdaptor(logicalExpression, and.vertexIndex());
    }
    public LogicalAxiom.Atom.Connective.Or Or(LogicalAxiom.Atom... atoms) {
        return Or(UUID.randomUUID(), atoms);
    }

    public LogicalAxiom.Atom.Connective.Or Or(UUID vertexUuid, LogicalAxiom.Atom... atoms) {
        EntityVertex or = EntityVertex.make(vertexUuid, LogicalAxiomSemantic.OR.nid);
        builder.addVertex(or);
        for (LogicalAxiom.Atom atom : atoms) {
            builder.addEdge(atom.vertexIndex(), or.vertexIndex());
        }
        return new LogicalAxiomAdaptor.OrAdaptor(logicalExpression, or.vertexIndex());
    }
    public LogicalAxiom.Atom.TypedAtom.Role SomeRole(ConceptFacade roleType, LogicalAxiom.Atom restriction) {
        return SomeRole(UUID.randomUUID(), roleType, restriction);
    }

    public LogicalAxiom.Atom.TypedAtom.Role SomeRole(UUID vertexUuid, ConceptFacade roleType, LogicalAxiom.Atom restriction) {
        EntityVertex someRole = EntityVertex.make(vertexUuid, LogicalAxiomSemantic.ROLE.nid);
        builder.addVertex(someRole);
        someRole.putUncommittedProperty(TinkarTerm.ROLE_TYPE.nid(), roleType);
        someRole.putUncommittedProperty(TinkarTerm.ROLE_OPERATOR.nid(), TinkarTerm.EXISTENTIAL_RESTRICTION);
        someRole.commitProperties();
        builder.addEdge(restriction.vertexIndex(), someRole.vertexIndex());
        return new LogicalAxiomAdaptor.RoleAxiomAdaptor(logicalExpression, someRole.vertexIndex());
    }
    public LogicalAxiom.Atom.TypedAtom.Role AllRole(ConceptFacade roleType, LogicalAxiom.Atom restriction) {
        return AllRole(UUID.randomUUID(), roleType, restriction);
    }
    public LogicalAxiom.Atom.TypedAtom.Role AllRole(UUID vertexUuid, ConceptFacade roleType, LogicalAxiom.Atom restriction) {
        EntityVertex allRole = EntityVertex.make(vertexUuid, LogicalAxiomSemantic.ROLE.nid);
        builder.addVertex(allRole);
        allRole.putUncommittedProperty(TinkarTerm.ROLE_TYPE.nid(), roleType);
        allRole.putUncommittedProperty(TinkarTerm.ROLE_OPERATOR.nid(), TinkarTerm.UNIVERSAL_RESTRICTION);
        allRole.commitProperties();
        builder.addEdge(restriction.vertexIndex(), allRole.vertexIndex());
        return new LogicalAxiomAdaptor.RoleAxiomAdaptor(logicalExpression, allRole.vertexIndex());
    }

    public LogicalAxiom.Atom.TypedAtom.Role Role(ConceptFacade roleOperator, ConceptFacade roleType, LogicalAxiom.Atom restriction) {
        return Role(UUID.randomUUID(), roleOperator, roleType, restriction);
    }

    public LogicalAxiom.Atom.TypedAtom.Role Role(UUID vertexUuid, ConceptFacade roleOperator, ConceptFacade roleType, LogicalAxiom.Atom restriction) {
        EntityVertex role = EntityVertex.make(vertexUuid, LogicalAxiomSemantic.ROLE.nid);
        builder.addVertex(role);
        role.putUncommittedProperty(TinkarTerm.ROLE_TYPE.nid(), roleType);
        role.putUncommittedProperty(TinkarTerm.ROLE_OPERATOR.nid(), roleOperator);
        role.commitProperties();
        builder.addEdge(restriction.vertexIndex(), role.vertexIndex());
        return new LogicalAxiomAdaptor.RoleAxiomAdaptor(logicalExpression, role.vertexIndex());
    }

    public LogicalAxiom.Atom.ConceptAxiom ConceptAxiom(int conceptNid) {
        return ConceptAxiom(UUID.randomUUID(), ConceptFacade.make(conceptNid));
    }
    public LogicalAxiom.Atom.ConceptAxiom ConceptAxiom(UUID vertexUuid, int conceptNid) {
        return ConceptAxiom(vertexUuid, ConceptFacade.make(conceptNid));
    }
    public LogicalAxiom.Atom.ConceptAxiom ConceptAxiom(ConceptFacade concept) {
        return ConceptAxiom(UUID.randomUUID(), concept);
    }

    public LogicalAxiom.Atom.ConceptAxiom ConceptAxiom(UUID vertexUuid, ConceptFacade concept) {
        EntityVertex conceptAxiom = EntityVertex.make(vertexUuid, LogicalAxiomSemantic.CONCEPT.nid);
        builder.addVertex(conceptAxiom);
        conceptAxiom.putUncommittedProperty(TinkarTerm.CONCEPT_REFERENCE.nid(), concept);
        conceptAxiom.commitProperties();
        return new LogicalAxiomAdaptor.ConceptAxiomAdaptor(logicalExpression, conceptAxiom.vertexIndex());
    }
    public LogicalAxiom.Atom.DisjointWithAxiom DisjointWithAxiom(ConceptFacade disjointConcept) {
        return DisjointWithAxiom(UUID.randomUUID(),  disjointConcept);
    }

    public LogicalAxiom.Atom.DisjointWithAxiom DisjointWithAxiom(UUID vertexUuid, ConceptFacade disjointConcept) {
        EntityVertex disjointWithAxiom = EntityVertex.make(vertexUuid, LogicalAxiomSemantic.DISJOINT_WITH.nid);
        builder.addVertex(disjointWithAxiom);
        disjointWithAxiom.putUncommittedProperty(TinkarTerm.DISJOINT_WITH.nid(), disjointConcept);
        disjointWithAxiom.commitProperties();
        return new LogicalAxiomAdaptor.DisjointWithAxiomAdaptor(logicalExpression, disjointWithAxiom.vertexIndex());
    }

    public LogicalAxiom.Atom.TypedAtom.Feature FeatureAxiom(ConceptFacade featureType, ConceptFacade concreteDomainOperator,
                                                            Object literalValue) {
        return FeatureAxiom(UUID.randomUUID(), featureType, concreteDomainOperator, literalValue);
    }

    public LogicalAxiom.Atom.TypedAtom.Feature FeatureAxiom(UUID vertexUuid, ConceptFacade featureType, ConceptFacade concreteDomainOperator,
                                                            Object literal) {
        EntityVertex featureAxiom = EntityVertex.make(vertexUuid, LogicalAxiomSemantic.FEATURE.nid);
        builder.addVertex(featureAxiom);
        featureAxiom.putUncommittedProperty(TinkarTerm.FEATURE_TYPE.nid(), featureType);
        featureAxiom.putUncommittedProperty(TinkarTerm.CONCRETE_DOMAIN_OPERATOR.nid(), concreteDomainOperator);
        featureAxiom.putUncommittedProperty(TinkarTerm.LITERAL_VALUE.nid(), literal);

        featureAxiom.commitProperties();
        return new LogicalAxiomAdaptor.FeatureAxiomAdaptor(logicalExpression, featureAxiom.vertexIndex());
    }
    public LogicalAxiom.Atom.PropertyPatternImplication PropertyPatternImplicationAxiom(ImmutableList<ConceptFacade> propertyPattern,
                                                                                        ConceptFacade implication) {
        return PropertyPatternImplicationAxiom(UUID.randomUUID(), propertyPattern, implication);
    }

        public LogicalAxiom.Atom.PropertyPatternImplication PropertyPatternImplicationAxiom(UUID vertexUuid,
                                                                                            ImmutableList<ConceptFacade> propertyPattern,
                                                                                        ConceptFacade implication) {
        EntityVertex propertyPatternImplicationAxiom = EntityVertex.make(vertexUuid, LogicalAxiomSemantic.PROPERTY_PATTERN_IMPLICATION.nid);
        builder.addVertex(propertyPatternImplicationAxiom);
        throw new UnsupportedOperationException();
//        propertyPatternImplicationAxiom.putUncommittedProperty(TinkarTerm.PATTERN.nid(), featureType);
//        propertyPatternImplicationAxiom.putUncommittedProperty(TinkarTerm.PROPERTY_PATTERN_IMPLICATION.nid(), implication);
//
//        propertyPatternImplicationAxiom.commitProperties();
//        return new LogicalAxiomAdaptor.PropertyPatternImplicationAdaptor(logicalExpression, propertyPatternImplicationAxiom.vertexIndex());
    }

    public LogicalAxiom addCloneOfNode(LogicalAxiom rootToClone) {
        return switch (rootToClone) {
            case LogicalAxiom.Atom.Connective.And and -> {
                ImmutableSet<LogicalAxiom.Atom> elements = and.elements();
                MutableList<LogicalAxiom.Atom> childElements = Lists.mutable.ofInitialCapacity(elements.size());
                for (LogicalAxiom.Atom element : elements) {
                    childElements.add((LogicalAxiom.Atom) addCloneOfNode(element));
                }
                yield And(and.vertexUUID(), childElements.toArray(new LogicalAxiom.Atom[childElements.size()]));
            }

            case LogicalAxiom.Atom.ConceptAxiom conceptAxiom -> ConceptAxiom(conceptAxiom.vertexUUID(), conceptAxiom.concept());

            case LogicalAxiom.DefinitionRoot definitionRoot -> {
                ImmutableSet<LogicalAxiom.LogicalSet> sets = definitionRoot.sets();
                MutableList<LogicalAxiom.Atom> childSets = Lists.mutable.ofInitialCapacity(sets.size());
                for (LogicalAxiom.LogicalSet logicalSet : sets) {
                    childSets.add((LogicalAxiom.Atom) addCloneOfNode(logicalSet));
                }
                // Definition root was created when builder was created...
                yield logicalExpression.definitionRoot();
            }
            case LogicalAxiom.Atom.DisjointWithAxiom disjointWithAxiom -> DisjointWithAxiom(disjointWithAxiom.vertexUUID(), disjointWithAxiom.disjointWith());

            case LogicalAxiom.Atom.TypedAtom.Feature feature -> FeatureAxiom(feature.vertexUUID(), feature.type(), feature.concreteDomainOperator(), feature.literal());
            case LogicalAxiom.LogicalSet.NecessarySet necessarySet -> {
                // TODO remove the AND from the set... Will make isomorphic calculations faster... ?
                ImmutableSet<LogicalAxiom.Atom> elements = necessarySet.elements();
                MutableList<LogicalAxiom.Atom.Connective> childElements = Lists.mutable.ofInitialCapacity(elements.size());
                for (LogicalAxiom.Atom element : elements) {
                    childElements.add((LogicalAxiom.Atom.Connective) addCloneOfNode(element));
                }
                yield NecessarySet(necessarySet.vertexUUID(), childElements.toArray(new LogicalAxiom.Atom[childElements.size()]));
            }
            case LogicalAxiom.Atom.Connective.Or or -> {
                ImmutableSet<LogicalAxiom.Atom> elements = or.elements();
                MutableList<LogicalAxiom.Atom> childElements = Lists.mutable.ofInitialCapacity(elements.size());
                for (LogicalAxiom.Atom element : elements) {
                    childElements.add((LogicalAxiom.Atom) addCloneOfNode(element));
                }
                yield Or(or.vertexUUID(), childElements.toArray(childElements.toArray(new LogicalAxiom.Atom[childElements.size()])));
            }
            case LogicalAxiom.Atom.PropertyPatternImplication propertyPatternImplication ->
                    PropertyPatternImplicationAxiom(propertyPatternImplication.vertexUUID(), propertyPatternImplication.propertyPattern(), propertyPatternImplication.implication());
            case LogicalAxiom.LogicalSet.PropertySet propertySet -> {
                // TODO remove the AND from the set... Will make isomorphic calculations faster... ?
                ImmutableSet<LogicalAxiom.Atom> elements = propertySet.elements();
                MutableList<LogicalAxiom.Atom.Connective> childElements = Lists.mutable.ofInitialCapacity(elements.size());
                for (LogicalAxiom.Atom element : elements) {
                    childElements.add((LogicalAxiom.Atom.Connective) addCloneOfNode(element));
                }
                yield PropertySet(propertySet.vertexUUID(), childElements.toArray(new LogicalAxiom.Atom[childElements.size()]));
            }
            case LogicalAxiom.Atom.TypedAtom.Role role -> Role(role.vertexUUID(), role.roleOperator(), role.type(), (LogicalAxiom.Atom) addCloneOfNode(role.restriction()));
            case LogicalAxiom.LogicalSet.SufficientSet sufficientSet -> {
                // TODO remove the AND from the set... Will make isomorphic calculations faster... ?
                ImmutableSet<LogicalAxiom.Atom> elements = sufficientSet.elements();
                MutableList<LogicalAxiom.Atom.Connective> childElements = Lists.mutable.ofInitialCapacity(elements.size());
                for (LogicalAxiom.Atom element : elements) {
                    childElements.add((LogicalAxiom.Atom.Connective) addCloneOfNode(element));
                }
                yield SufficientSet(sufficientSet.vertexUUID(), childElements.toArray(new LogicalAxiom.Atom[childElements.size()]));
            }
            default -> throw new IllegalStateException("Unexpected value: " + rootToClone);
        };
    }

    public void updateConceptReference(LogicalAxiom.Atom.ConceptAxiom conceptAxiom, ConceptFacade newConceptReference) {
        EntityVertex conceptReferenceVertex = this.builder.vertex(conceptAxiom.vertexIndex());
        conceptReferenceVertex.putUncommittedProperty(TinkarTerm.CONCEPT_REFERENCE.nid(),
                EntityProxy.Concept.make(newConceptReference.description(), newConceptReference.publicId()));
        conceptReferenceVertex.commitProperties();
    }

    public void updateConceptReference(EntityVertex conceptReferenceVertex, ConceptFacade newConceptReference) {
        conceptReferenceVertex.putUncommittedProperty(TinkarTerm.CONCEPT_REFERENCE.nid(),
                EntityProxy.Concept.make(newConceptReference.description(), newConceptReference.publicId()));
        conceptReferenceVertex.commitProperties();
    }

    public void updateConceptReference(LogicalAxiom.Atom.ConceptAxiom conceptAxiom, int newConceptReferenceNid) {
        updateConceptReference(conceptAxiom, EntityProxy.Concept.make(PrimitiveData.text(newConceptReferenceNid),
                PrimitiveData.publicId(newConceptReferenceNid)));
    }

    public void updateRoleType(LogicalAxiom.Atom.TypedAtom.Role roleAxiom, ConceptFacade conceptToChangeTo) {
        EntityVertex roleVertex = this.builder.vertex(roleAxiom.vertexIndex());
        roleVertex.putUncommittedProperty(TinkarTerm.ROLE_TYPE.nid(), EntityProxy.Concept.make(conceptToChangeTo));
        roleVertex.commitProperties();
    }

    public void updateFeatureType(LogicalAxiom.Atom.TypedAtom.Feature featureAxiom, ConceptFacade conceptToChangeTo) {
        EntityVertex roleVertex = this.builder.vertex(featureAxiom.vertexIndex());
        roleVertex.putUncommittedProperty(TinkarTerm.FEATURE_TYPE.nid(), EntityProxy.Concept.make(conceptToChangeTo));
        roleVertex.commitProperties();
    }

    public void updateFeatureOperator(LogicalAxiom.Atom.TypedAtom.Feature featureAxiom, ConceptFacade conceptToChangeTo) {
        EntityVertex roleVertex = this.builder.vertex(featureAxiom.vertexIndex());
        roleVertex.putUncommittedProperty(TinkarTerm.CONCRETE_DOMAIN_OPERATOR.nid(), EntityProxy.Concept.make(conceptToChangeTo));
        roleVertex.commitProperties();
    }

    public void updateRoleRestriction(LogicalAxiom.Atom.TypedAtom.Role roleAxiom, ConceptFacade conceptToChangeTo) {
        EntityVertex roleVertex = this.builder.vertex(roleAxiom.vertexIndex());
        ImmutableList<EntityVertex> successors = this.builder.successors(roleVertex);
        if (successors.size() != 1) {
            throw new IllegalStateException("Role should have 1 child for the concept restriction... " + builder.build());
        }
        updateConceptReference(successors.get(0), conceptToChangeTo);
    }

    public void changeSetType(LogicalAxiom.LogicalSet setAxiom, ConceptFacade conceptToChangeTo) {
        EntityVertex changedSet = EntityVertex.make(setAxiom.vertexUUID(), conceptToChangeTo.nid());
        builder.setVertexIndex(changedSet, setAxiom.vertexIndex());
        builder.replaceVertex(changedSet);
    }

}
