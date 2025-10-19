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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.ikm.elk.snomed.SnomedDescriptions;
import dev.ikm.elk.snomed.SnomedIds;
import dev.ikm.elk.snomed.SnomedIsa;
import dev.ikm.elk.snomed.SnomedOntology;
import dev.ikm.elk.snomed.model.Concept;
import dev.ikm.reasoner.hybrid.snomed.StatementSnomedOntology;
import dev.ikm.reasoner.hybrid.snomed.StatementSnomedOntology.SwecIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import dev.ikm.tinkar.reasoner.elksnomed.ElkSnomedData;
import dev.ikm.tinkar.terms.TinkarTerm;

public abstract class HybridClassifierWithoutAbsentTestBase extends HybridReasonerTestBase {

	private static final Logger LOG = LoggerFactory.getLogger(HybridClassifierWithoutAbsentTestBase.class);

	private HashMap<Integer, Long> nid_sctid_map;

	private Set<Long> toSctids(Set<Long> nids) {
		return nids.stream().map(x -> nid_sctid_map.get(x.intValue())).collect(Collectors.toSet());
	}

	/**
	 * Tests with useAbsent = false, so the results are expected to be same as
	 * Snomed release
	 * 
	 * @throws Exception
	 */
	@Test
	public void isas() throws Exception {
		LOG.info("runSnomedReasoner");
		ElkSnomedData data = buildSnomedData();
		LOG.info("Create ontology");
		SnomedOntology ontology = new SnomedOntology(data.getConcepts(), data.getRoleTypes(),
				data.getConcreteRoleTypes());
		LOG.info("Create reasoner");
		// This tests structural, so useAbsent = false
		// TODO create a method so that SwecIds aren't needed
		SwecIds swec_ids = StatementSnomedOntology.swec_sctids;
		SwecIds swec_nids = new StatementSnomedOntology.SwecIds(ElkSnomedData.getNid(swec_ids.swec()),
				ElkSnomedData.getNid(swec_ids.swec_parent()), ElkSnomedData.getNid(swec_ids.findingContext()),
				ElkSnomedData.getNid(swec_ids.knownAbsent()));
		StatementSnomedOntology sso = StatementSnomedOntology.create(ontology, false, HybridReasonerService.getRootId(),
				swec_nids);
		sso.classify();
		TreeSet<Long> misses = new TreeSet<>();
		int non_snomed_cnt = 0;
		int miss_cnt = 0;
		SnomedIsa isas = SnomedIsa.init(rels_file);
		SnomedDescriptions descr = SnomedDescriptions.init(descriptions_file);
		nid_sctid_map = new HashMap<>();
		for (long sctid : isas.getOrderedConcepts()) {
			int nid = ElkSnomedData.getNid(sctid);
			nid_sctid_map.put(nid, sctid);
			if (ontology.getConcept(nid) == null)
				LOG.info("No concept for: " + sctid + " " + descr.getFsn(sctid));
		}
		for (Concept con : ontology.getConcepts()) {
			long nid = con.getId();
			Set<Long> sups = toSctids(sso.getSuperConcepts(nid));
			Long sctid = nid_sctid_map.get((int) nid);
			if (sctid == null) {
				non_snomed_cnt++;
				continue;
			}
			Set<Long> parents = isas.getParents(sctid);
			if (sctid == SnomedIds.root) {
				assertTrue(parents.isEmpty());
				// has a parent in the db
				assertEquals(1, sups.size());
				assertEquals(TinkarTerm.ROOT_VERTEX.nid(), sso.getSuperConcepts(nid).iterator().next());
				continue;
			} else {
				assertNotNull(parents);
			}
			if (!parents.equals(sups)) {
				misses.add(sctid);
				miss_cnt++;
			}
		}
		isas.getOrderedConcepts().stream().filter(misses::contains) //
				.limit(10) //
				.forEach((sctid) -> {
					UUID uuid = UuidUtil.fromSNOMED("" + sctid);
					int nid = PrimitiveData.nid(uuid);
					LOG.error("Miss: " + sctid + " " + PrimitiveData.text(nid));
					Set<Long> sups = toSctids(sso.getSuperConcepts(nid));
					Set<Long> parents = isas.getParents(sctid);
					HashSet<Long> par = new HashSet<>(parents);
					par.removeAll(sups);
					HashSet<Long> sup = new HashSet<>(sups);
					sup.removeAll(parents);
					LOG.error("Sno:  " + par);
					LOG.error("Elk:  " + sup);
					if (sups.contains(null)) {
						sso.getSuperConcepts(nid)
								.forEach(sup_nid -> LOG.error("   :  " + PrimitiveData.text((sup_nid.intValue()))));
					}
				});
		LOG.error("Miss cnt: " + miss_cnt);
		int expected_non_snomed_cnt = PrimitiveDataTestUtil.getPrimordialNids().size()
				- PrimitiveDataTestUtil.getPrimordialNidsWithSctids().size();
		assertEquals(expected_non_snomed_cnt, non_snomed_cnt);
		assertEquals(0, miss_cnt);
	}

}
