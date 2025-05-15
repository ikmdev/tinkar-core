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
import dev.ikm.reasoner.hybrid.snomed.StatementSnomedOntology;
import dev.ikm.reasoner.hybrid.snomed.StatementSnomedOntology.SwecIds;
import dev.ikm.tinkar.common.service.PluggableService;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.reasoner.elksnomed.ElkSnomedData;
import dev.ikm.tinkar.reasoner.elksnomed.ElkSnomedDataBuilder;
import dev.ikm.tinkar.reasoner.service.ReasonerService;
import dev.ikm.tinkar.terms.TinkarTerm;

public abstract class HybridReasonerServiceTestBase extends SnomedTestBase {

	private static final Logger LOG = LoggerFactory.getLogger(HybridReasonerServiceTestBase.class);

	protected static String test_case;

	// This is overridden in the version test cases
	protected ViewCalculator getViewCalculator() {
		return PrimitiveDataTestUtil.getViewCalculator();
	}

	public ReasonerService initReasonerService() {
		ReasonerService rs = PluggableService.load(ReasonerService.class).stream()
				.filter(x -> x.type().getSimpleName().equals(HybridReasonerService.class.getSimpleName())) //
				.findFirst().get().get();
		rs.init(getViewCalculator(), TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN,
				TinkarTerm.EL_PLUS_PLUS_INFERRED_AXIOMS_PATTERN);
		rs.setProgressUpdater(null);
		return rs;
	}

	public ElkSnomedData buildSnomedData() throws Exception {
		LOG.info("buildSnomedData");
		ViewCalculator viewCalculator = getViewCalculator();
		ElkSnomedData data = new ElkSnomedData();
		ElkSnomedDataBuilder builder = new ElkSnomedDataBuilder(viewCalculator,
				TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN, data);
		builder.build();
		return data;
	}

	@Test
	public void runReasonerService() throws Exception {
		LOG.info("runReasonerService");
		ReasonerService rs = initReasonerService();
		rs.extractData();
		rs.loadData();
		rs.computeInferences();
		rs.buildNecessaryNormalForm();
		rs.getReasonerConceptSet().forEach(rs::getParents);
		rs.getReasonerConceptSet().forEach(rs::getChildren);
		checkRoot(rs);
	}

	@Test
	public void ids() throws Exception {
		for (long sctid : List.of(StatementSnomedOntology.swec_id, StatementSnomedOntology.finding_context_id,
				StatementSnomedOntology.known_absent_id)) {
			int nid = ElkSnomedData.getNid(sctid);
			LOG.info(PrimitiveData.text(nid) + " " + nid + " " + sctid);
		}
		SwecIds swecNids = HybridReasonerService.getSwecNids();
		for (long nid : List.of(HybridReasonerService.getRootId(), swecNids.swec(), swecNids.swec_parent(),
				swecNids.findingContext(), swecNids.knownAbsent())) {
			LOG.info(PrimitiveData.text((int) nid) + " " + nid);
		}
		{
			int nid = ElkSnomedData.getNid(SnomedIds.root);
			LOG.info(PrimitiveData.text(nid) + " " + nid);
		}
		{
			int nid = TinkarTerm.ROOT_VERTEX.nid();
			LOG.info(PrimitiveData.text(nid) + " " + nid);
		}
	}
	
	protected static int expected_swec_children = -1;

	private void checkRoot(ReasonerService rs) {
		SwecIds swecNids = HybridReasonerService.getSwecNids();
		assertEquals(1, rs.getParents((int) swecNids.swec()).size());
		assertEquals(swecNids.swec_parent(), rs.getParents((int) swecNids.swec()).toArray()[0]);
		assertEquals(expected_swec_children, rs.getChildren((int) swecNids.swec()).size());
		rs.getChildren((int) swecNids.swec()).forEach(id -> {
			assertEquals(1, rs.getParents(id).size());
			assertEquals(swecNids.swec(), rs.getParents(id).toArray()[0]);
		});
	}

}
