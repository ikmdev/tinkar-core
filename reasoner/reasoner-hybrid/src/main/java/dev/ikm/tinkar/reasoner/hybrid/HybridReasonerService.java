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

import org.eclipse.collections.api.set.primitive.ImmutableLongSet;

import dev.ikm.tinkar.terms.KernelTerm;
import dev.ikm.tinkar.common.service.TrackingCallable;
import org.eclipse.collections.api.factory.primitive.IntSets;
import org.eclipse.collections.api.set.primitive.ImmutableIntSet;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.ikm.elk.snomed.NecessaryNormalFormBuilder;
import dev.ikm.elk.snomed.SnomedOntology;
import dev.ikm.reasoner.hybrid.snomed.StatementSnomedOntology;
import dev.ikm.reasoner.hybrid.snomed.StatementSnomedOntology.SwecIds;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.reasoner.elksnomed.ElkSnomedData;
import dev.ikm.tinkar.reasoner.elksnomed.ElkSnomedReasonerService;
import dev.ikm.tinkar.terms.PatternFacade;

public class HybridReasonerService extends ElkSnomedReasonerService {

	private static final Logger LOG = LoggerFactory.getLogger(HybridReasonerService.class);

	private StatementSnomedOntology sso;

	@Override
	public String getName() {
		return "Absence Reasoner";
	}

	public static long getRootId() {
		return KernelTerm.ROOT_VERTEX.nid();
	}

	private static final SwecIds swec_ids = StatementSnomedOntology.swec_nfh_sctids; // swec_sctids;

	public static SwecIds getSwecNids() {
		SwecIds swec_nids = new StatementSnomedOntology.SwecIds(ElkSnomedData.getNid(swec_ids.swec()),
				ElkSnomedData.getNid(swec_ids.swec_parent()), ElkSnomedData.getNid(swec_ids.findingContext()),
				ElkSnomedData.getNid(swec_ids.knownAbsent()));
		return swec_nids;
	}

	@Override
	public void init(ViewCalculator viewCalculator, PatternFacade statedAxiomPattern,
			PatternFacade inferredAxiomPattern) {
		super.init(viewCalculator, statedAxiomPattern, inferredAxiomPattern);
		sso = null;
	}

	@Override
	public void loadData(TrackingCallable<?> progressTracker) throws Exception {
		LOG.info("Create ontology");
		ontology = new SnomedOntology(data.getConcepts(), data.getRoleTypes(), data.getConcreteRoleTypes());
	};

	@Override
	public void computeInferences(TrackingCallable<?> progressTracker) {
		sso = StatementSnomedOntology.create(ontology, HybridReasonerService.getRootId(), getSwecNids());
		sso.classify();
	}

	@Override
	public boolean isIncrementalReady() {
		return false;
	}

	@Override
	public void buildNecessaryNormalForm(TrackingCallable<?> progressUpdater) {
		nnfb = NecessaryNormalFormBuilder.create(sso.getOntology(),
				sso.getSuperConcepts(),
				sso.getSuperRoleTypes(false),
				KernelTerm.ROOT_VERTEX.nid(),
				(workDone, max) -> progressUpdater.updateProgress(workDone, max));
		nnfb.generate();
	}

	@Override
	public ImmutableLongSet getEquivalent(long id) {
		// the reasoner's ids are the nids
		return sso.getEquivalentConcepts(id).toImmutable();
	}

	@Override
	public ImmutableLongSet getParents(long id) {
		// the reasoner's ids are the nids
		return sso.getSuperConcepts(id).toImmutable();
	}

	@Override
	public ImmutableLongSet getChildren(long id) {
		// the reasoner's ids are the nids
		return sso.getSubConcepts(id).toImmutable();
	}

}
