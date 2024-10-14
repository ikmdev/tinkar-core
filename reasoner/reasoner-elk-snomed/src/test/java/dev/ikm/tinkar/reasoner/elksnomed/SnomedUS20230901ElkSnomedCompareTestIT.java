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
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.ikm.elk.snomed.SnomedDescriptions;
import dev.ikm.elk.snomed.SnomedIds;
import dev.ikm.elk.snomed.SnomedOntology;
import dev.ikm.elk.snomed.model.Concept;
import dev.ikm.elk.snomed.model.RoleType;
import dev.ikm.elk.snomed.owl.OwlTransformer;
import dev.ikm.elk.snomed.owl.SnomedOwlOntology;
import dev.ikm.tinkar.common.util.uuid.UuidUtil;

public class SnomedUS20230901ElkSnomedCompareTestIT extends SnomedUS20230901ElkSnomedTestBase {

	private static final Logger LOG = LoggerFactory.getLogger(SnomedUS20230901ElkSnomedCompareTestIT.class);

	@Test
	public void compare() throws Exception {
		assumeTrue(Files.exists(axioms_file), "No file: " + axioms_file);
		assumeTrue(Files.exists(rels_file), "No file: " + rels_file);
		LOG.info("Files exist");
		LOG.info("\t" + axioms_file);
		LOG.info("\t" + rels_file);
		ElkSnomedData data = buildSnomedData();
		{
			Concept us_con = data.getConcept(ElkSnomedData.getNid(SnomedIds.us_nlm_module));
			assertNotNull(us_con);
		}
		SnomedOwlOntology ontology = SnomedOwlOntology.createOntology();
		ontology.loadOntology(axioms_file);
		SnomedOntology snomedOntology = new OwlTransformer().transform(ontology);
		snomedOntology.setDescriptions(SnomedDescriptions.init(descriptions_file));
		{
			Concept us_con = snomedOntology.getConcept(SnomedIds.us_nlm_module);
			assertNotNull(us_con);
			LOG.info(snomedOntology.getFsn(us_con.getId()));
		}
		int missing_concept_cnt = 0;
		int missing_role_cnt = 0;
		for (Concept con : snomedOntology.getConcepts()) {
			int nid = ElkSnomedData.getNid(con.getId());
			Concept data_con = data.getConcept(nid);
			if (data_con == null) {
				LOG.error("No concept: " + con);
				missing_concept_cnt++;
				continue;
			}
			if (con.getDefinitions().size() != data_con.getDefinitions().size())
				LOG.error("Definition size: " + con.getId() + " " + snomedOntology.getFsn(con.getId()) + "\n"
						+ "Expect " + con.getDefinitions().size() + " Actual " + data_con.getDefinitions().size() + "\n"
						+ nid + " " + UuidUtil.fromSNOMED("" + con.getId()));

			if (con.getGciDefinitions().size() != data_con.getGciDefinitions().size())
				LOG.error("Gcis: " + con);
		}
		for (RoleType role : snomedOntology.getRoleTypes()) {
			int nid = ElkSnomedData.getNid(role.getId());
			RoleType data_role = data.getRoleType(nid);
			if (data_role == null) {
				LOG.error("No role: " + role);
				missing_role_cnt++;
				continue;
			}
		}
		assertEquals(0, missing_concept_cnt);
		assertEquals(0, missing_role_cnt);
	}

}
