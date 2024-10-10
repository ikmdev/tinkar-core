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

import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.eclipse.collections.api.set.primitive.ImmutableIntSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.ikm.elk.snomed.SnomedIds;
import dev.ikm.elk.snomed.SnomedOntology;
import dev.ikm.reasoner.hybrid.snomed.StatementSnomedOntology;
import dev.ikm.reasoner.hybrid.snomed.StatementSnomedOntology.SwecIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.reasoner.elksnomed.ElkSnomedReasonerService;
import dev.ikm.tinkar.terms.PatternFacade;
import dev.ikm.tinkar.terms.TinkarTerm;

public class HybridReasonerService extends ElkSnomedReasonerService {

	private static final Logger LOG = LoggerFactory.getLogger(HybridReasonerService.class);

	private StatementSnomedOntology sso;

	public static long getRootId() {
		return TinkarTerm.ROOT_VERTEX.nid();
	}

	public static SwecIds getSwecNids() {
		SwecIds swec_ids = new StatementSnomedOntology.SwecIds(getNid(StatementSnomedOntology.swec_id),
				getNid(SnomedIds.root), getNid(StatementSnomedOntology.finding_context_id),
				getNid(StatementSnomedOntology.known_absent_id));
		return swec_ids;
	}

	public static int getNid(long sctid) {
		UUID uuid = UuidUtil.fromSNOMED("" + sctid);
		int nid = PrimitiveData.nid(uuid);
		return nid;
	}

	@Override
	public void init(ViewCalculator viewCalculator, PatternFacade statedAxiomPattern,
			PatternFacade inferredAxiomPattern) {
		super.init(viewCalculator, statedAxiomPattern, inferredAxiomPattern);
		sso = null;
	}

	@Override
	public void loadData() throws Exception {
		progressUpdater.updateProgress(0, data.getActiveConceptCount());
		LOG.info("Create ontology");
		ontology = new SnomedOntology(data.getConcepts(), data.getRoleTypes(), List.of());
	};

	@Override
	public void computeInferences() {
		sso = StatementSnomedOntology.create(ontology, HybridReasonerService.getRootId(), getSwecNids());
		sso.classify();
	}

	@Override
	public boolean isIncrementalReady() {
		return false;
	}

	@Override
	public void processIncremental(DiTreeEntity definition, int conceptNid) {
		throw new UnsupportedOperationException();
//		Concept concept = builder.processIncremental(definition, conceptNid);
//		reasoner.process(concept);
//		reasoner.flush();
	}

	@Override
	public ImmutableIntSet getEquivalent(int id) {
		Set<Long> eqs = sso.getEquivalentConcepts(id);
		return toIntSet(eqs);
	}

	@Override
	public ImmutableIntSet getParents(int id) {
		Set<Long> supers = sso.getSuperConcepts(id);
		return toIntSet(supers);
	}

	@Override
	public ImmutableIntSet getChildren(int id) {
		Set<Long> subs = sso.getSubConcepts(id);
		return toIntSet(subs);
	}

}
