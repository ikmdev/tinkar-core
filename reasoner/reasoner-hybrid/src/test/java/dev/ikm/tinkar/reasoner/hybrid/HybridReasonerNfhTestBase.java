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

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.ikm.elk.snomed.model.Concept;
import dev.ikm.elk.snomed.model.Definition;
import dev.ikm.elk.snomed.model.DefinitionType;
import dev.ikm.reasoner.hybrid.snomed.FamilyHistoryIds;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpression;
import dev.ikm.tinkar.ext.lang.owl.OwlElToLogicalExpression;
import dev.ikm.tinkar.reasoner.elksnomed.ElkSnomedData;
import dev.ikm.tinkar.reasoner.elksnomed.ElkSnomedUtil;

public abstract class HybridReasonerNfhTestBase extends HybridReasonerTestBase {

	private static final Logger LOG = LoggerFactory.getLogger(HybridReasonerNfhTestBase.class);

	private void updateNfh() throws Exception {
		ViewCalculator vc = PrimitiveDataTestUtil.getViewCalculator();
		SemanticEntityVersion sev = ElkSnomedUtil.getStatedSemantic(vc,
				ElkSnomedData.getNid(FamilyHistoryIds.no_family_history_swec));
		LOG.info("SEV:\n" + sev);
		Concept nfh_con = ElkSnomedUtil.getConcept(vc, ElkSnomedData.getNid(FamilyHistoryIds.no_family_history_swec));
		Definition def = nfh_con.getDefinitions().getFirst();
		def.setDefinitionType(DefinitionType.SubConcept);
		def.getSuperConcepts().clear();
		Concept fh_con = ElkSnomedUtil.getConcept(vc, ElkSnomedData.getNid(FamilyHistoryIds.family_history_swec));
		def.addSuperConcept(fh_con);
		def.getRoleGroups().clear();
		LogicalExpression le = new OwlElToLogicalExpression().build(def);
		LOG.info("LE:\n" + le);
		ElkSnomedUtil.updateStatedSemantic(vc, (int) nfh_con.getId(), le);
		// 704008007 |No family history of asthma (situation)|
		updateParent(704008007, FamilyHistoryIds.no_family_history_swec);
		// 160274005 |No family history of diabetes mellitus (situation)|
		updateParent(160274005, FamilyHistoryIds.no_family_history_swec);
		// 1344634002 |No family history of multiple sclerosis (situation)|
		updateParent(1344634002, FamilyHistoryIds.no_family_history_swec);
	}

	private void updateParent(long sctid, long parent_sctid) throws Exception {
		ViewCalculator vc = PrimitiveDataTestUtil.getViewCalculator();
		Concept nfh_con = ElkSnomedUtil.getConcept(vc, ElkSnomedData.getNid(sctid));
		Definition def = nfh_con.getDefinitions().getFirst();
		def.getSuperConcepts().clear();
		Concept fh_con = ElkSnomedUtil.getConcept(vc, ElkSnomedData.getNid(parent_sctid));
		def.addSuperConcept(fh_con);
		LogicalExpression le = new OwlElToLogicalExpression().build(def);
		ElkSnomedUtil.updateStatedSemantic(vc, (int) nfh_con.getId(), le);
	}

	@Test
	public void nfh() throws Exception {
		updateNfh();
	}

}
