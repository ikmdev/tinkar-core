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

import java.util.HashMap;
import java.util.Set;

import dev.ikm.tinkar.common.service.TrackingCallable;
import org.eclipse.collections.api.factory.primitive.IntSets;
import org.eclipse.collections.api.factory.primitive.LongObjectMaps;
import org.eclipse.collections.api.factory.primitive.LongSets;
import org.eclipse.collections.api.map.primitive.MutableLongObjectMap;
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
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.reasoner.elksnomed.ElkSnomedData;
import dev.ikm.tinkar.reasoner.elksnomed.ElkSnomedReasonerService;
import dev.ikm.tinkar.terms.PatternFacade;
import dev.ikm.tinkar.terms.TinkarTerm;

public class HybridReasonerService extends ElkSnomedReasonerService {

	private static final Logger LOG = LoggerFactory.getLogger(HybridReasonerService.class);

	private StatementSnomedOntology sso;

	@Override
	public String getName() {
		return "Absence Reasoner";
	}

	public static long getRootId() {
		return TinkarTerm.ROOT_VERTEX.nid();
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
	public void processIncremental(DiTreeEntity definition, int conceptNid, TrackingCallable<?> progressUpdater) {
		throw new UnsupportedOperationException();
	}
	@Override
	public void buildNecessaryNormalForm(TrackingCallable<?> progressUpdater) {
		//TODO: refactor to use primitive collections directly.
		nnfb = NecessaryNormalFormBuilder.create(sso.getOntology(),
				convertToLongMap(sso.getSuperConcepts()),
				convertToLongMap(sso.getSuperRoleTypes(false)),
				TinkarTerm.ROOT_VERTEX.nid(),
				(workDone, max) -> progressUpdater.updateProgress(workDone, max));
		nnfb.generate();
	}

	/**
	 * Converts boxed HashMap to primitive Eclipse Collections map.
	 * Transforms HashMap<Long, Set<Long>> to MutableLongObjectMap<MutableLongSet>
	 * for better performance with primitive types.
	 *
	 * @param source the boxed map to convert
	 * @return a primitive map with primitive long sets as values
	 * TODO: refactor to use primitive collections directly.
	 */
	private MutableLongObjectMap<MutableLongSet> convertToLongMap(HashMap<Long, Set<Long>> source) {
		MutableLongObjectMap<MutableLongSet> result = LongObjectMaps.mutable.withInitialCapacity(source.size());
		source.forEach((key, values) -> {
			MutableLongSet primitiveValues = LongSets.mutable.withInitialCapacity(values.size());
			values.forEach(primitiveValues::add);
			result.put(key, primitiveValues);
		});
		return result;
	}

	@Override
	public ImmutableIntSet getEquivalent(int id) {
		Set<Long> eqs = sso.getEquivalentConcepts(id);
		MutableIntSet eqsInt = IntSets.mutable.empty();
		eqs.stream().mapToInt(Long::intValue).forEach(eqsInt::add);
		return eqsInt.toImmutable();
	}

	@Override
	public ImmutableIntSet getParents(int id) {
		Set<Long> supers = sso.getSuperConcepts(id);
		MutableIntSet eqsInt = IntSets.mutable.empty();
		supers.stream().mapToInt(Long::intValue).forEach(eqsInt::add);
		return eqsInt.toImmutable();
	}

	@Override
	public ImmutableIntSet getChildren(int id) {
		Set<Long> subs = sso.getSubConcepts(id);
		MutableIntSet eqsInt = IntSets.mutable.empty();
		subs.stream().mapToInt(Long::intValue).forEach(eqsInt::add);
		return eqsInt.toImmutable();
	}

}
