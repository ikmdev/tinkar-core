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

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.ikm.elk.snomed.SnomedOntology;
import dev.ikm.reasoner.hybrid.snomed.FamilyHistoryIds;
import dev.ikm.reasoner.hybrid.snomed.StatementSnomedOntology;
import dev.ikm.reasoner.hybrid.snomed.StatementSnomedOntology.SwecIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.reasoner.elksnomed.ElkSnomedData;
import dev.ikm.tinkar.terms.TinkarTerm;

public class SnomedUS20230901NfhHybridClassifierTestIT extends SnomedUS20230901HybridDataBuilderTestIT {

	private static final Logger LOG = LoggerFactory.getLogger(SnomedUS20230901NfhHybridClassifierTestIT.class);

	public static final String db = "SnomedCT_US_20230901_NFH_SpinedArray-20240920";

	@BeforeAll
	public static void startPrimitiveData() throws IOException {
		setupPrimitiveData(db);
		PrimitiveData.start();
	}

//	@Test
	public void supercsService() throws Exception {
		runSnomedReasonerService();
//		compare("supercs");
	}

	private SnomedOntology ontology;

	private StatementSnomedOntology sso;

	@Test
	public void isas() throws Exception {
		LOG.info("runSnomedReasoner");
		ElkSnomedData data = buildSnomedData();
		LOG.info("Create ontology");
		ontology = new SnomedOntology(data.getConcepts(), data.getRoleTypes(), List.of());
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
			int nid = -2147482338;
			LOG.info(PrimitiveData.text(nid) + " " + nid);
		}
		{
			int nid = TinkarTerm.ROOT_VERTEX.nid();
			LOG.info(PrimitiveData.text(nid) + " " + nid);
		}
		sso = StatementSnomedOntology.create(ontology, HybridReasonerService.getRootId(), swec_nids);
		sso.classify();
		assertEquals(1, sso.getSuperConcepts(swec_nids.swec()).size());
		assertEquals(swec_nids.swec_parent(), sso.getSuperConcepts(swec_nids.swec()).iterator().next());
		assertEquals(21, sso.getSubConcepts(swec_nids.swec()).size());
		for (long id : sso.getSubConcepts(swec_nids.swec())) {
			assertEquals(1, sso.getSuperConcepts(id).size());
			assertEquals(swec_nids.swec(), sso.getSuperConcepts(id).iterator().next());
		}
		{
			checkParents(408553000l, Set.of(704008007l, 408552005l));
			checkParents(160270001, Set.of(160273004l, 266882009l, 297250002l, 313342001l));
			checkParents(160250007l, Set.of(313376005l));
			assertEquals(11,
					sso.getSubConcepts(HybridReasonerService.getNid(FamilyHistoryIds.no_family_history_swec)).size());
		}
	}

	private void checkParents(long con, Set<Long> expect_parents) {
		int con_nid = HybridReasonerService.getNid(con);
		Set<Integer> expect_parents_nids = expect_parents.stream().map(HybridReasonerService::getNid)
				.collect(Collectors.toCollection(HashSet::new));
		Set<Long> actual_parents = sso.getSuperConcepts(con_nid);
		Set<Integer> actual_parents_nids = actual_parents.stream().map(Long::intValue)
				.collect(Collectors.toCollection(HashSet::new));
		assertEquals(expect_parents_nids, actual_parents_nids, "Concept " + con + " " + ontology.getFsn(con));
	}

}
