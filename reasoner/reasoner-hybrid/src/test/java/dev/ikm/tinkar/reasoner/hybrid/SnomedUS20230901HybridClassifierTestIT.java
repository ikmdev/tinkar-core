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
package dev.ikm.tinkar.reasoner.hybrid;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.ikm.elk.snomed.SnomedIds;
import dev.ikm.elk.snomed.SnomedOntology;
import dev.ikm.reasoner.hybrid.snomed.StatementSnomedOntology;
import dev.ikm.reasoner.hybrid.snomed.StatementSnomedOntology.SwecIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.reasoner.elksnomed.ElkSnomedData;
import dev.ikm.tinkar.terms.TinkarTerm;

public class SnomedUS20230901HybridClassifierTestIT extends SnomedUS20230901HybridDataBuilderTestIT {

	private static final Logger LOG = LoggerFactory.getLogger(SnomedUS20230901HybridClassifierTestIT.class);

//	@Test
	public void supercsService() throws Exception {
		runSnomedReasonerService();
//		compare("supercs");
	}

	@Test
	public void isas() throws Exception {
		LOG.info("runSnomedReasoner");
		ElkSnomedData data = buildSnomedData();
		LOG.info("Create ontology");
		SnomedOntology ontology = new SnomedOntology(data.getConcepts(), data.getRoleTypes(), List.of());
		LOG.info("Create reasoner");
		for (long sctid : List.of(StatementSnomedOntology.swec_id, StatementSnomedOntology.finding_context_id,
				StatementSnomedOntology.known_absent_id)) {
			int nid = HybridReasonerService.getNid(sctid);
			LOG.info(PrimitiveData.text(nid) + " " + nid + " " + sctid);
		}
		SwecIds swec_nids = HybridReasonerService.getSwecNids();
		for (long nid : List.of(HybridReasonerService.getRootId(), swec_nids.swec(), swec_nids.findingContext(),
				swec_nids.knownAbsent())) {
			LOG.info(PrimitiveData.text((int) nid) + " " + nid);
		}
		{
			int nid = HybridReasonerService.getNid(SnomedIds.root);
			LOG.info(PrimitiveData.text(nid) + " " + nid);
		}
		{
			int nid = TinkarTerm.ROOT_VERTEX.nid();
			LOG.info(PrimitiveData.text(nid) + " " + nid);
		}
		StatementSnomedOntology sso = StatementSnomedOntology.create(ontology, HybridReasonerService.getRootId(),
				swec_nids);
		sso.classify();
		assertEquals(1, sso.getSuperConcepts(swec_nids.swec()).size());
		assertEquals(swec_nids.swec_parent(), sso.getSuperConcepts(swec_nids.swec()).iterator().next());
		assertEquals(21, sso.getSubConcepts(swec_nids.swec()).size());
		for (long id : sso.getSubConcepts(swec_nids.swec())) {
			assertEquals(1, sso.getSuperConcepts(id).size());
			assertEquals(swec_nids.swec(), sso.getSuperConcepts(id).iterator().next());
		}
	}

}
