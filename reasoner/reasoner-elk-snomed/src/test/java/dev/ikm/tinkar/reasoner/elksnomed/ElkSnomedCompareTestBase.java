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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.Objects;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.ikm.elk.snomed.OwlElTransformer;
import dev.ikm.elk.snomed.SnomedDescriptions;
import dev.ikm.elk.snomed.SnomedIds;
import dev.ikm.elk.snomed.SnomedOntology;
import dev.ikm.elk.snomed.model.Concept;
import dev.ikm.elk.snomed.model.ConcreteRole;
import dev.ikm.elk.snomed.model.ConcreteRoleType;
import dev.ikm.elk.snomed.model.RoleType;
import dev.ikm.elk.snomed.owlel.OwlElOntology;

public abstract class ElkSnomedCompareTestBase extends ElkSnomedTestBase {

	private static final Logger LOG = LoggerFactory.getLogger(ElkSnomedCompareTestBase.class);
	
	protected int expected_compare_concept_cnt = 0;


	@Test
	public void compare() throws Exception {
		assumeTrue(Files.exists(axioms_file), "No file: " + axioms_file);
		assumeTrue(Files.exists(rels_file), "No file: " + rels_file);
		LOG.info("Files exist");
		LOG.info("\t" + axioms_file);
		LOG.info("\t" + rels_file);
		// TODO back this out once Decimal is implemented
		ConcreteRole.convert_to_float_for_compare = true;
		ElkSnomedData data = buildSnomedData();
		{
			Concept us_con = data.getConcept(ElkSnomedData.getNid(SnomedIds.us_nlm_module));
			if (getEdition().startsWith("US")) {
				assertNotNull(us_con);
			} else {
				assertNull(us_con);
			}
		}
		OwlElOntology ontology = new OwlElOntology();
		ontology.load(axioms_file);
		SnomedOntology snomedOntology = new OwlElTransformer().transform(ontology);
		snomedOntology.setDescriptions(SnomedDescriptions.init(descriptions_file));
		{
			Concept us_con = snomedOntology.getConcept(SnomedIds.us_nlm_module);
			if (getEdition().startsWith("US")) {
				assertNotNull(us_con);
			} else {
				assertNull(us_con);
			}
		}
		for (RoleType role : snomedOntology.getRoleTypes()) {
			role.setName(snomedOntology.getFsn(role.getId()));
			int nid = ElkSnomedData.getNid(role.getId());
			RoleType data_role = data.getRoleType(nid);
			if (data_role == null)
				continue;
			data_role.setId(role.getId());
			data_role.setName(role.getName());
		}
		for (ConcreteRoleType role : snomedOntology.getConcreteRoleTypes()) {
			role.setName(snomedOntology.getFsn(role.getId()));
			int nid = ElkSnomedData.getNid(role.getId());
			ConcreteRoleType data_role = data.getConcreteRoleType(nid);
			if (data_role == null)
				continue;
			data_role.setId(role.getId());
			data_role.setName(role.getName());
		}
		for (Concept con : snomedOntology.getConcepts()) {
			con.setName(snomedOntology.getFsn(con.getId()));
			int nid = ElkSnomedData.getNid(con.getId());
			Concept data_con = data.getConcept(nid);
			if (data_con == null)
				continue;
			data_con.setId(con.getId());
			data_con.setName(con.getName());
		}
		int missing_role_cnt = 0;
		int missing_concrete_role_cnt = 0;
		int missing_concept_cnt = 0;
		int compare_role_cnt = 0;
		int compare_concrete_role_cnt = 0;
		int compare_concept_cnt = 0;
		for (RoleType role : snomedOntology.getRoleTypes()) {
			int nid = ElkSnomedData.getNid(role.getId());
			RoleType data_role = data.getRoleType(nid);
			if (data_role == null) {
				LOG.error("No role: " + role);
				missing_role_cnt++;
				continue;
			}
			if (!compare(role, data_role, snomedOntology))
				compare_role_cnt++;
		}
		for (ConcreteRoleType role : snomedOntology.getConcreteRoleTypes()) {
			int nid = ElkSnomedData.getNid(role.getId());
			ConcreteRoleType data_role = data.getConcreteRoleType(nid);
			if (data_role == null) {
				LOG.error("No role: " + role);
				missing_concrete_role_cnt++;
				continue;
			}
			if (!compare(role, data_role, snomedOntology))
				compare_concrete_role_cnt++;
		}
		for (Concept con : snomedOntology.getConcepts()) {
			int nid = ElkSnomedData.getNid(con.getId());
			Concept data_con = data.getConcept(nid);
			if (data_con == null) {
				LOG.error("No concept: " + con);
				missing_concept_cnt++;
				continue;
			}
//			LOG.info(con.getId() + " " + snomedOntology.getFsn(con.getId()));
			if (!compare(con, data_con, snomedOntology))
				compare_concept_cnt++;
		}
		assertEquals(0, missing_role_cnt);
		assertEquals(0, missing_concrete_role_cnt);
		assertEquals(0, missing_concept_cnt);
		assertEquals(0, compare_role_cnt);
		assertEquals(0, compare_concrete_role_cnt);
		assertEquals(expected_compare_concept_cnt, compare_concept_cnt);
	}

	public boolean compareEquals(Object expect, Object actual, String msg) {
		if (Objects.equals(expect, actual))
			return true;
		LOG.error(msg);
		LOG.error("\tExpect: " + expect);
		LOG.error("\tActual: " + actual);
		return false;
	}

	public boolean compare(RoleType expect, RoleType actual, SnomedOntology snomedOntology) {
		String con_msg = expect.getId() + " " + snomedOntology.getFsn(expect.getId());
		return compareEquals(new HashSet<>(expect.getSuperRoleTypes()), new HashSet<>(actual.getSuperRoleTypes()),
				"Super Role Types " + con_msg)
				& compareEquals(expect.isTransitive(), actual.isTransitive(), "Transitive " + con_msg)
				& compareEquals(expect.getChained(), actual.getChained(), "Chained " + con_msg)
				& compareEquals(expect.isReflexive(), actual.isReflexive(), "Reflexive " + con_msg);
	}

	public boolean compare(ConcreteRoleType expect, ConcreteRoleType actual, SnomedOntology snomedOntology) {
		String con_msg = expect.getId() + " " + snomedOntology.getFsn(expect.getId());
		return compareEquals(new HashSet<>(expect.getSuperConcreteRoleTypes()),
				new HashSet<>(actual.getSuperConcreteRoleTypes()), "Super Concrete Role Types " + con_msg);
	}

	public boolean compare(Concept expect, Concept actual, SnomedOntology snomedOntology) {
//		compareDefinitions(expect.getDefinitions(), actual.getDefinitions(), "Definitions ", expect, snomedOntology);
//		compareDefinitions(expect.getGciDefinitions(), actual.getGciDefinitions(), "Gci Definitions ", expect,
//				snomedOntology);
		String con_msg = expect.getId() + " " + snomedOntology.getFsn(expect.getId());
		return compareEquals(new HashSet<>(expect.getDefinitions()),
				new HashSet<>(actual.getDefinitions().stream().map(x -> x.copy()).toList()), "Definitions " + con_msg)
				& compareEquals(new HashSet<>(expect.getGciDefinitions()),
						new HashSet<>(actual.getGciDefinitions().stream().map(x -> x.copy()).toList()),
						"Gci Definitions " + con_msg);
	}

//	@Deprecated
//	public boolean compareDefinitions(List<Definition> expect, List<Definition> actual, String msg, Concept con,
//			SnomedOntology snomedOntology) {
//		if (expect.size() != actual.size()) {
//			LOG.error(msg + con.getId() + " " + snomedOntology.getFsn(con.getId()) + "\n" + "Expect " + expect.size()
//					+ " Actual " + actual.size() + "\n"
//					// + nid + " "
//					+ UuidUtil.fromSNOMED("" + con.getId()));
//			return false;
//		}
//		return true;
//	}

}
