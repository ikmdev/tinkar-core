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
package dev.ikm.tinkar.reasoner.elksnomed;

import java.math.BigDecimal;
import java.util.concurrent.atomic.AtomicInteger;

import org.eclipse.collections.api.list.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.ikm.elk.snomed.model.Concept;
import dev.ikm.elk.snomed.model.ConcreteRole;
import dev.ikm.elk.snomed.model.ConcreteRole.ValueType;
import dev.ikm.elk.snomed.model.ConcreteRoleType;
import dev.ikm.elk.snomed.model.Definition;
import dev.ikm.elk.snomed.model.DefinitionType;
import dev.ikm.elk.snomed.model.Role;
import dev.ikm.elk.snomed.model.RoleGroup;
import dev.ikm.elk.snomed.model.RoleType;
import dev.ikm.tinkar.common.id.IntIdList;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.service.TrackingCallable;
import dev.ikm.tinkar.coordinate.logic.LogicCoordinateRecord;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiomSemantic;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.PatternFacade;
import dev.ikm.tinkar.terms.TinkarTerm;

public class ElkSnomedDataBuilder {

	private static final Logger LOG = LoggerFactory.getLogger(ElkSnomedDataBuilder.class);

	private final ViewCalculator viewCalculator;

	private final PatternFacade statedAxiomPattern;

	private final ElkSnomedData data;

	private TrackingCallable<?> progressUpdater = null;

	public ElkSnomedDataBuilder(ViewCalculator viewCalculator, PatternFacade statedAxiomPattern, ElkSnomedData data) {
		super();
		this.viewCalculator = viewCalculator;
		this.statedAxiomPattern = statedAxiomPattern;
		this.data = data;
	}

	public void setProgressUpdater(TrackingCallable<?> progressUpdater) {
		this.progressUpdater = progressUpdater;
	}

	private void updateProgress(int count, int total) {
		if (progressUpdater != null && count % 100 == 0)
			progressUpdater.updateProgress(count, total);
	}

	private int computeTotalCount() {
		AtomicInteger totalCounter = new AtomicInteger();
		PrimitiveData.get().forEachSemanticNidOfPattern(statedAxiomPattern.nid(), i -> totalCounter.incrementAndGet());
		return totalCounter.get();
	}

	public void build() throws Exception {
		final int totalCount = computeTotalCount();
		LOG.info("Total axioms: " + totalCount);
		updateProgress(0, totalCount);
		final AtomicInteger processedCount = new AtomicInteger();
		LogicCoordinateRecord logicCoordinate = viewCalculator.logicCalculator().logicCoordinateRecord();
		AtomicInteger ex_cnt = new AtomicInteger();
		viewCalculator.forEachSemanticVersionOfPatternParallel(logicCoordinate.statedAxiomsPatternNid(),
				(semanticEntityVersion, patternEntityVersion) -> {
					try {
						int conceptNid = semanticEntityVersion.referencedComponentNid();
						if (viewCalculator.latestIsActive(conceptNid)) {
							// For now, only classify active
							DiTreeEntity definition = (DiTreeEntity) semanticEntityVersion.fieldValues().get(0);
							processDefinition(definition, conceptNid);
							data.incrementActiveConceptCount();
						} else {
							data.incrementInactiveConceptCount();
						}
						updateProgress(processedCount.incrementAndGet(), totalCount);
					} catch (Exception ex) {
						if (ex_cnt.incrementAndGet() < 10) {
							LOG.error(ex.getMessage());
							LOG.error("", ex);
						}
					}
				});
		data.initializeReasonerConceptSet();
		for (Concept con : data.getConcepts()) {
			if (con.getDefinitions().isEmpty())
				LOG.warn("No definitions: " + con.getId() + " " + PrimitiveData.text((int) con.getId()));
		}
		updateProgress(totalCount, totalCount);
		LOG.info("Total processed: " + totalCount + " " + processedCount.get());
		LOG.info("Active concepts: " + data.getActiveConceptCount());
		LOG.info("Inactive concepts: " + data.getInactiveConceptCount());
		if (ex_cnt.get() != 0) {
			String msg = "Exceptions: " + ex_cnt.get();
			LOG.error(msg);
			throw new Exception(msg);
		}
	}

	public Concept processIncremental(DiTreeEntity definition, int conceptNid) {
		Concept concept = data.getOrCreateConcept(conceptNid);
		concept.removeAllDefinitions();
		concept.removeAllGciDefinitions();
		processDefinition(definition, conceptNid);
		// TODO update active concept count etc. ??
		return concept;
	}

	private LogicalAxiomSemantic getMeaning(EntityVertex node) {
		return LogicalAxiomSemantic.get(node.getMeaningNid());
	}

	private int getNid(EntityVertex node, dev.ikm.tinkar.terms.EntityProxy.Concept concept) {
		ConceptFacade cf = node.propertyFast(concept);
		return cf.nid();
	}

	private void processDefinition(DiTreeEntity definition, int conceptNid) throws IllegalStateException {
		EntityVertex root = definition.root();
		for (EntityVertex child : definition.successors(root)) {
			switch (getMeaning(child)) {
			case SUFFICIENT_SET -> {
				Concept concept = data.getOrCreateConcept(conceptNid);
				Definition def = new Definition();
				def.setDefinitionType(DefinitionType.EquivalentConcept);
				processDefinition(def, child, definition);
				concept.addDefinition(def);
			}
			case NECESSARY_SET -> {
				Concept concept = data.getOrCreateConcept(conceptNid);
				Definition def = new Definition();
				def.setDefinitionType(DefinitionType.SubConcept);
				processDefinition(def, child, definition);
				concept.addDefinition(def);
			}
			case INCLUSION_SET -> {
//				LOG.info("Inclusion set: " + PrimitiveData.text(conceptNid));
//				LOG.info("" + definition);
				Concept concept = data.getOrCreateConcept(conceptNid);
				Definition def = new Definition();
				def.setDefinitionType(DefinitionType.SubConcept);
				processDefinition(def, child, definition);
				concept.addGciDefinition(def);
			}
			case PROPERTY_SET -> {
				processPropertySet(child, conceptNid, definition);
			}
			default -> throw new IllegalArgumentException("Unexpected value: " + getMeaning(child));
			}
		}
	}

	private void processDefinition(Definition def, EntityVertex node, DiTreeEntity definition) {
		final ImmutableList<EntityVertex> children = definition.successors(node);
		if (children.size() != 1)
			throw new IllegalStateException("Definitions require a single child: " + definition);
		EntityVertex child = children.getFirst();
		switch (getMeaning(child)) {
		case AND -> {
			processAnd(def, child, definition);
		}
		case CONCEPT -> {
			int nid = getNid(child, TinkarTerm.CONCEPT_REFERENCE);
			def.addSuperConcept(data.getOrCreateConcept(nid));
		}
		default -> throw new IllegalArgumentException("Unexpected value: " + getMeaning(child));
		}
	}

	private void processPropertySet(EntityVertex propertySetNode, int conceptNid, DiTreeEntity definition) {
//		LOG.info("PropertySet: " + PrimitiveData.text(conceptNid) + " " + propertySetNode + "\n" + definition);
		final ImmutableList<EntityVertex> children = definition.successors(propertySetNode);
		if (children.size() != 1)
			throw new IllegalStateException(
					"PropertySetNode can only have one child. Concept: " + conceptNid + " definition: " + definition);
		EntityVertex child = children.getFirst();
		if (child.getMeaningNid() != TinkarTerm.AND.nid())
			throw new IllegalStateException("PropertySetNode can only have AND for a child. Concept: " + conceptNid
					+ " definition: " + definition);
		for (EntityVertex node : definition.successors(child)) {
			switch (getMeaning(node)) {
			case CONCEPT -> {
				RoleType roleType = data.getOrCreateRoleType(conceptNid);
				ConceptFacade nodeConcept = node.propertyFast(TinkarTerm.CONCEPT_REFERENCE);
				if (nodeConcept.nid() == TinkarTerm.TRANSITIVE_PROPERTY.nid()) {
					roleType.setTransitive(true);
				} else if (nodeConcept.nid() == TinkarTerm.REFLEXIVE_PROPERTY.nid()) {
					roleType.setReflexive(true);
				} else {
					roleType.addSuperRoleType(data.getOrCreateRoleType(nodeConcept.nid()));
				}
			}
			case PROPERTY_PATTERN_IMPLICATION -> {
				LOG.info("PropertySet: " + PrimitiveData.text(conceptNid) + " " + propertySetNode + "\n" + definition);
				RoleType roleType = data.getOrCreateRoleType(conceptNid);
				ConceptFacade ppi = node.propertyFast(TinkarTerm.PROPERTY_PATTERN_IMPLICATION);
				if (ppi.nid() != conceptNid)
					throw new IllegalStateException(
							"Property chain malformed. Concept: " + conceptNid + " definition: " + definition);
				IntIdList ps = node.propertyFast(TinkarTerm.PROPERTY_SET);
				if (ps.size() != 2)
					throw new IllegalStateException("Property chain " + ps.size() + " != 2. Concept: " + conceptNid
							+ " definition: " + definition);
				if (ps.get(0) != conceptNid)
					throw new IllegalStateException(
							"Property chain malformed. Concept: " + conceptNid + " definition: " + definition);
				RoleType prop1 = data.getOrCreateRoleType(ps.get(0));
				RoleType prop2 = data.getOrCreateRoleType(ps.get(1));
				if (!roleType.equals(prop1))
					throw new IllegalStateException("This is a bug.");
				roleType.setChained(prop2);
				LOG.info("PPI: " + PrimitiveData.text((int) prop1.getId()) + " "
						+ PrimitiveData.text((int) prop1.getChained().getId()));
			}
			default -> throw new UnsupportedOperationException("Can't handle: " + node + " in: " + definition);
			}
		}
	}

	private void processAnd(Definition def, EntityVertex node, DiTreeEntity definition) {
		final ImmutableList<EntityVertex> children = definition.successors(node);
		for (EntityVertex child : children) {
			switch (getMeaning(child)) {
			case CONCEPT -> {
				int concept_nid = getNid(child, TinkarTerm.CONCEPT_REFERENCE);
				def.addSuperConcept(data.getOrCreateConcept(concept_nid));
			}
			case ROLE -> {
				int role_operator_nid = getNid(child, TinkarTerm.ROLE_OPERATOR);
				int role_type_nid = getNid(child, TinkarTerm.ROLE_TYPE);
				if (role_operator_nid == TinkarTerm.EXISTENTIAL_RESTRICTION.nid()) {
					// TODO use nids when the db is fixed
					ConceptFacade cf = child.propertyFast(TinkarTerm.ROLE_TYPE);
					PublicId role_type_public_id = cf.publicId();
					if (PublicId.equals(role_type_public_id, TinkarTerm.ROLE_GROUP.publicId())) {
//					if (role_type_nid == TinkarTerm.ROLE_GROUP.nid()) {
						// TODO Placeholder for now so the tests work
						data.getOrCreateRoleType(role_type_nid);
						processRoleGroup(def, child, definition);
					} else {
						Role role = makeRole(child, definition);
						def.addUngroupedRole(role);
					}
				} else {
					throw new UnsupportedOperationException(
							"Role: " + PrimitiveData.text(role_operator_nid) + " not supported. ");
				}
			}
			case FEATURE -> {
//				LOG.info("Feature: " + "\n" + definition);
				ConcreteRole role = makeConcreteRole(child, definition);
				def.addUngroupedConcreteRole(role);
			}
			default -> throw new IllegalArgumentException("Unexpected value: " + getMeaning(child));
			}
		}
	}

	private Role makeRole(EntityVertex node, DiTreeEntity definition) {
		int role_type_nid = getNid(node, TinkarTerm.ROLE_TYPE);
		RoleType role_type = data.getOrCreateRoleType(role_type_nid);
		final ImmutableList<EntityVertex> children = definition.successors(node);
		if (children.size() != 1)
			throw new IllegalStateException(
					"Role can only have one child. Role: " + node + " definition: " + definition);
		EntityVertex child = children.getFirst();
		int concept_nid = getNid(child, TinkarTerm.CONCEPT_REFERENCE);
		return new Role(role_type, data.getOrCreateConcept(concept_nid));
	}

	private ConcreteRole makeConcreteRole(EntityVertex node, DiTreeEntity definition) {
//		int role_operator_nid = getNid(node, TinkarTerm.CONCRETE_DOMAIN_OPERATOR);
		int role_type_nid = getNid(node, TinkarTerm.FEATURE_TYPE);
		Object value = node.propertyFast(TinkarTerm.LITERAL_VALUE);
		ValueType value_type = switch (value) {
		case BigDecimal x -> ValueType.Decimal;
		case Double x -> ValueType.Double;
		case Float x -> ValueType.Float;
		case Integer x -> ValueType.Integer;
		case String x -> ValueType.String;
		default -> throw new UnsupportedOperationException("Value type: " + value.getClass().getName());
		};
//		LOG.info("[" + PrimitiveData.text(role_operator_nid) + "] " + PrimitiveData.text(role_type_nid) + ": " + value
//				+ " [" + value_type + "]");
		ConcreteRoleType role_type = data.getOrCreateConcreteRoleType(role_type_nid);
		return new ConcreteRole(role_type, value.toString(), value_type);
	}

	private void processRoleGroup(Definition def, EntityVertex node, DiTreeEntity definition) {
		final ImmutableList<EntityVertex> children = definition.successors(node);
		if (children.size() != 1)
			throw new IllegalStateException(
					"RoleGroup can only have one child. Role: " + node + " definition: " + definition);
		EntityVertex child = children.getFirst();
		switch (getMeaning(child)) {
		case ROLE -> {
			int role_operator_nid = getNid(child, TinkarTerm.ROLE_OPERATOR);
			if (role_operator_nid == TinkarTerm.EXISTENTIAL_RESTRICTION.nid()) {
				RoleGroup rg = new RoleGroup();
				def.addRoleGroup(rg);
				Role role = makeRole(child, definition);
				rg.addRole(role);
			} else {
				throw new UnsupportedOperationException(
						"Role: " + PrimitiveData.text(role_operator_nid) + " not supported. ");
			}
		}
		case FEATURE -> {
//			LOG.info("Feature: " + "\n" + definition);
			RoleGroup rg = new RoleGroup();
			def.addRoleGroup(rg);
			ConcreteRole role = makeConcreteRole(child, definition);
			rg.addConcreteRole(role);
		}
		case AND -> {
			processRoleGroupAnd(def, child, definition);
		}
		default -> throw new IllegalArgumentException("Unexpected value: " + getMeaning(child));
		}
	}

	private void processRoleGroupAnd(Definition def, EntityVertex node, DiTreeEntity definition) {
		RoleGroup rg = new RoleGroup();
		def.addRoleGroup(rg);
		final ImmutableList<EntityVertex> children = definition.successors(node);
		for (EntityVertex child : children) {
			switch (getMeaning(child)) {
			case ROLE -> {
				int role_operator_nid = getNid(child, TinkarTerm.ROLE_OPERATOR);
				if (role_operator_nid == TinkarTerm.EXISTENTIAL_RESTRICTION.nid()) {
					Role role = makeRole(child, definition);
					rg.addRole(role);
				} else {
					throw new UnsupportedOperationException(
							"Role: " + PrimitiveData.text(role_operator_nid) + " not supported. ");
				}
			}
			case FEATURE -> {
//				LOG.info("Feature: " + "\n" + definition);
				ConcreteRole role = makeConcreteRole(child, definition);
				rg.addConcreteRole(role);
			}
			default -> throw new IllegalArgumentException("Unexpected value: " + getMeaning(child));
			}
		}
	}

}
