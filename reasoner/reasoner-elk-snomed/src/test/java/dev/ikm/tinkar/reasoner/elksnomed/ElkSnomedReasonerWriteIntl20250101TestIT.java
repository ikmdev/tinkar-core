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

import dev.ikm.elk.snomed.ConceptComparer;
import dev.ikm.elk.snomed.SnomedConcreteRoles;
import dev.ikm.elk.snomed.SnomedDescriptions;
import dev.ikm.elk.snomed.SnomedIds;
import dev.ikm.elk.snomed.SnomedIsa;
import dev.ikm.elk.snomed.SnomedLoader;
import dev.ikm.elk.snomed.SnomedOntology;
import dev.ikm.elk.snomed.SnomedRoleGroups;
import dev.ikm.elk.snomed.SnomedRoles;
import dev.ikm.elk.snomed.model.Concept;
import dev.ikm.elk.snomed.model.ConcreteRole;
import dev.ikm.elk.snomed.model.ConcreteRoleType;
import dev.ikm.elk.snomed.model.Definition;
import dev.ikm.elk.snomed.model.Role;
import dev.ikm.elk.snomed.model.RoleGroup;
import dev.ikm.elk.snomed.model.RoleType;
import dev.ikm.tinkar.common.id.IntIdSet;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.reasoner.service.ReasonerService;
import dev.ikm.tinkar.terms.TinkarTerm;
import org.eclipse.collections.api.list.ImmutableList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class ElkSnomedReasonerWriteIntl20250101TestIT extends ElkSnomedTestBase implements SnomedVersionInternational {

	private static final Logger LOG = LoggerFactory.getLogger(ElkSnomedReasonerWriteIntl20250101TestIT.class);

	static {
		test_case = "snomed-intl-20250101";
	}

	@Override
	public String getVersion() {
		return "20250101";
	}

	@BeforeAll
	public static void startPrimitiveData() throws IOException {
		String write_db = "" + UUID.randomUUID();
		LOG.info("Write: " + write_db);
		PrimitiveDataTestUtil.copyDb(test_case + "-sa", write_db);
		PrimitiveDataTestUtil.setupPrimitiveData(write_db);
		PrimitiveData.start();
	}

	@AfterAll
	public static void stopPrimitiveData() {
		LOG.info("stopPrimitiveData");
		PrimitiveData.stop();
		LOG.info("Stopped");
	}

	@Test
	public void reasonerWrite() throws Exception {
		ReasonerService rs = initReasonerService();
		rs.extractData();
		rs.loadData();
		rs.computeInferences();
		rs.buildNecessaryNormalForm();
		rs.writeInferredResults();
		int inferredNavigationPatternNid = TinkarTerm.INFERRED_NAVIGATION_PATTERN.nid();
		int inferredPatternNid = rs.getViewCalculator().viewCoordinateRecord().logicCoordinate()
				.inferredAxiomsPatternNid();
		SnomedIsa isas = SnomedIsa.init(rels_file);
//		SnomedIsa isa = SnomedIsa.init(rels_file);
//		SnomedRoles roles = SnomedRoles.init(rels_file);
//		SnomedConcreteRoles values = SnomedConcreteRoles.init(values_file);
//		HashMap<Long, Concept> nnfs = buildSnomedNNFs(descr, isa, roles, values);
		SnomedDescriptions descr = SnomedDescriptions.init(descriptions_file);
		SnomedOntology inferredOntology = new SnomedLoader().load(concepts_file, descriptions_file, rels_file,
				values_file);
		inferredOntology.setDescriptions(descr);
		inferredOntology.setNames();
		ElkSnomedData data = buildSnomedData();
		NidToSctid nid_to_sctid = new NidToSctid(data, inferredOntology);
		nid_to_sctid.build();
		ConceptComparer cc = new ConceptComparer(inferredOntology);
		int parent_miss = 0;
		int child_miss = 0;
		int mis_match_cnt = 0;
		for (long sctid : isas.getOrderedConcepts()) {
			int nid = ElkSnomedData.getNid(sctid);
			{
				Set<Integer> expected_parent_nids = isas.getParents(sctid).stream().map(ElkSnomedData::getNid)
						.collect(Collectors.toSet());
				if (sctid == SnomedIds.root) {
					expected_parent_nids = Set.of(TinkarTerm.ROOT_VERTEX.nid());
					LOG.warn("Reset expected for " + sctid + " " + PrimitiveData.text(nid));
				}
				Set<Integer> expected_child_nids = isas.getChildren(sctid).stream().map(ElkSnomedData::getNid)
						.collect(Collectors.toSet());
				int[] inferredNavigationNids = PrimitiveData.get().semanticNidsForComponentOfPattern(nid,
						inferredNavigationPatternNid);
				if (inferredNavigationNids.length == 0) {
					LOG.error("No semantic of pattern " + PrimitiveData.text(inferredNavigationPatternNid)
							+ " for component: " + PrimitiveData.text(nid));
				} else if (inferredNavigationNids.length == 1) {
					Latest<SemanticEntityVersion> latestInferredNavigationSemantic = rs.getViewCalculator()
							.latest(inferredNavigationNids[0]);
					if (latestInferredNavigationSemantic.isPresent()) {
						ImmutableList<Object> latestInferredNavigationFields = latestInferredNavigationSemantic.get()
								.fieldValues();
						IntIdSet actual_child_nids = (IntIdSet) latestInferredNavigationFields.get(0);
						IntIdSet actual_parent_nids = (IntIdSet) latestInferredNavigationFields.get(1);
						if (!expected_parent_nids.equals(actual_parent_nids.mapToSet(x -> x))) {
							LOG.error("Parents: " + sctid + " " + descr.getFsn(sctid));
							parent_miss++;
						}
						if (!expected_child_nids.equals(actual_child_nids.mapToSet(x -> x))) {
							LOG.error("Children: " + sctid + " " + descr.getFsn(sctid));
							child_miss++;
						}
					} else {
						LOG.error("No LATEST semantic of pattern " + PrimitiveData.text(inferredNavigationPatternNid)
								+ " for component: " + PrimitiveData.text(nid));
					}

				} else {
					LOG.error("More than one semantic of pattern " + PrimitiveData.text(inferredNavigationPatternNid)
							+ " for component: " + PrimitiveData.text(nid));
				}
			}
			{
				if (sctid == SnomedIds.root) {
					LOG.warn("Skipping compare for " + sctid + " " + PrimitiveData.text(nid));
					continue;
				}
				int[] inferredSemanticNids = PrimitiveData.get().semanticNidsForComponentOfPattern(nid,
						inferredPatternNid);
				if (inferredSemanticNids.length == 0) {
					LOG.error("No semantic of pattern " + PrimitiveData.text(inferredPatternNid) + " for component: "
							+ PrimitiveData.text(nid));
				} else if (inferredSemanticNids.length == 1) {
					Latest<SemanticEntityVersion> latestInferredSemantic = rs.getViewCalculator()
							.latest(inferredSemanticNids[0]);
					if (latestInferredSemantic.isPresent()) {
						ImmutableList<Object> latestInferredFields = latestInferredSemantic.get().fieldValues();
						DiTreeEntity latestInferredTree = (DiTreeEntity) latestInferredFields.get(0);
						ElkSnomedDataBuilder builder = new ElkSnomedDataBuilder(null, null, new ElkSnomedData());
						List<Concept> concepts = builder.processDefinition(nid, latestInferredTree);
						assertEquals(1, concepts.size());
						Definition new_def = nid_to_sctid
								.makeNewDefinition(concepts.getFirst().getDefinitions().getFirst());
						Concept new_concept = new Concept(sctid);
						new_concept.addDefinition(new_def);
						if (!cc.compare(new_concept)) {
							LOG.error("Mis match: " + new_concept);
							mis_match_cnt++;
						}
//						Definition expect = nnfs.get(sctid).getDefinitions().getFirst();
//						Definition actual = concepts.getFirst().getDefinitions().getFirst();
						// TODO
//						expect.setDefinitionType(actual.getDefinitionType());
//						if (!Objects.equals(expect.copy(), actual.copy())) {
//							LOG.error("NNF " + sctid + " " + nid + " " + PrimitiveData.text(nid));
//						}
					} else {
						LOG.error("No LATEST semantic of pattern " + PrimitiveData.text(inferredNavigationPatternNid)
								+ " for component: " + PrimitiveData.text(nid));
					}
				} else {
					LOG.error("More than one semantic of pattern " + PrimitiveData.text(inferredPatternNid)
							+ " for component: " + PrimitiveData.text(nid));
				}
			}
		}
		assertEquals(0, parent_miss);
		// TODO 609096000 Role group (attribute) & 1295447006 Annotation attribute
		// (attribute)
		assertEquals(2, child_miss);
		assertEquals(0, mis_match_cnt);
	}

//	LOG.error("\tExpect: " + expect);
//	LOG.error("\tActual: " + actual);
//	LOG.info("Expect trace: " + rs.getNecessaryNormalForm(nid));
//	LOG.info("Actual trace: " + latestInferredTree);
//	LOG.info("" + Objects.equals(expect.getSuperConcepts(), actual.getSuperConcepts()));
//	LOG.info("" + Objects.equals(expect.getUngroupedRoles(), actual.getUngroupedRoles()));
//	LOG.info("" + Objects.equals(expect.getRoleGroups(), actual.getRoleGroups()));
//	Objects.equals(expect.getRoleGroups(), actual.getRoleGroups());
//	if (!actual.getRoleGroups().isEmpty()) {
//		LOG.info("" + expect.getRoleGroups().toArray()[0]);
//		LOG.info("" + actual.getRoleGroups().toArray()[0]);
//		LOG.info("" + expect.getRoleGroups().toArray()[0]
//				.equals(actual.getRoleGroups().toArray()[0]));
//		LOG.info("" + expect.getRoleGroups().contains(actual.getRoleGroups().toArray()[0]));
//		LOG.info("" + expect.getRoleGroups().toArray()[0].hashCode());
//		LOG.info("" + actual.getRoleGroups().toArray()[0].hashCode());
//	}

	private HashMap<Long, Concept> buildSnomedNNFs(SnomedDescriptions descr, SnomedIsa isa, SnomedRoles roles,
			SnomedConcreteRoles values) {
//		OwlElOntology ontology = new OwlElOntology();
//		ontology.load(axioms_file);
//		SnomedOntology snomedOntology = new OwlElTransformer().transform(ontology);
		HashMap<Long, RoleType> role_types = new HashMap<>();
		for (long id : isa.getDescendants(SnomedIds.concept_model_object_attribute)) {
			RoleType role_type = new RoleType(ElkSnomedData.getNid(id));
			role_type.setName(descr.getFsn(id));
			role_types.put(id, role_type);
		}
		HashMap<Long, ConcreteRoleType> concrete_role_types = new HashMap<>();
		for (long id : isa.getDescendants(SnomedIds.concept_model_data_attribute)) {
			ConcreteRoleType role_type = new ConcreteRoleType(ElkSnomedData.getNid(id));
			role_type.setName(descr.getFsn(id));
			concrete_role_types.put(id, role_type);
		}
		HashMap<Long, Concept> concepts = new HashMap<>();
		for (long id : isa.getOrderedConcepts()) {
			Concept con = new Concept(ElkSnomedData.getNid(id));
//			con.setName(descr.getFsn(id));
			con.addDefinition(new Definition());
			concepts.put(id, con);
		}
		for (long id : isa.getOrderedConcepts()) {
			Concept con = concepts.get(id);
			Definition def = con.getDefinitions().getFirst();
			isa.getParents(id).forEach(parent -> def.addSuperConcept(concepts.get(parent)));
			roles.getUngroupedRoles(id).forEach(role -> def
					.addUngroupedRole(new Role(role_types.get(role.typeId), concepts.get(role.destinationId))));
			values.getUngroupedConcreteRoles(id)
					.forEach(role -> def.addUngroupedConcreteRole(
							new ConcreteRole(concrete_role_types.get(role.typeId), role.value.replace("#", ""),
									(concrete_role_types.get(role.typeId).getName().startsWith("Count")
											? ConcreteRole.ValueType.Integer
											: ConcreteRole.ValueType.Decimal))));
			SnomedRoleGroups.getRoleGroups(id, roles, values).forEach(srg -> {
				RoleGroup rg = new RoleGroup();
				def.addRoleGroup(rg);
				srg.roles.forEach(
						role -> rg.addRole(new Role(role_types.get(role.typeId), concepts.get(role.destinationId))));
				srg.concreteRoles.forEach(role -> rg.addConcreteRole(
						new ConcreteRole(concrete_role_types.get(role.typeId), role.value.replace("#", ""),
								(concrete_role_types.get(role.typeId).getName().startsWith("Count")
										? ConcreteRole.ValueType.Integer
										: ConcreteRole.ValueType.Decimal))));
			});
		}
		return concepts;
	}

}
