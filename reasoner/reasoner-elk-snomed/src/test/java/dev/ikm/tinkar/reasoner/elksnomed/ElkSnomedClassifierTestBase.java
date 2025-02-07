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
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
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
import dev.ikm.elk.snomed.SnomedOntologyReasoner;
import dev.ikm.elk.snomed.model.Concept;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import dev.ikm.tinkar.reasoner.service.ReasonerService;
import dev.ikm.tinkar.terms.TinkarTerm;

public abstract class ElkSnomedClassifierTestBase extends ElkSnomedTestBase {

	private static final Logger LOG = LoggerFactory.getLogger(ElkSnomedClassifierTestBase.class);

	protected int expected_supercs_cnt = -1;
	protected int expected_non_snomed_cnt = 284;
	protected int expected_miss_cnt = 0;
	protected int expected_pharma_miss_cnt = 0;
	protected int expected_other_miss_cnt = 0;

	@Test
	public void supercs() throws Exception {
		ArrayList<String> lines = runSnomedReasoner();
		assertEquals(expected_supercs_cnt, lines.size());
	}

	@Test
	public void supercsService() throws Exception {
		ArrayList<String> lines = runSnomedReasonerService();
		assertEquals(expected_supercs_cnt, lines.size());
	}

	@Test
	public void nnfService() throws Exception {
		ReasonerService rs = runReasonerServiceNNF();
		rs.writeInferredResults();
	}

	private HashMap<Integer, Long> nid_sctid_map;

	private Set<Long> toSctids(Set<Long> nids) {
		return nids.stream().map(x -> nid_sctid_map.get(x.intValue())).collect(Collectors.toSet());
	}

	@Test
	public void isas() throws Exception {
		LOG.info("runSnomedReasoner");
		ElkSnomedData data = buildSnomedData();
		LOG.info("Create ontology");
		SnomedOntology ontology = new SnomedOntology(data.getConcepts(), data.getRoleTypes(), List.of());
		LOG.info("Create reasoner");
		SnomedOntologyReasoner reasoner = SnomedOntologyReasoner.create(ontology);
		TreeSet<Long> misses = new TreeSet<>();
		TreeSet<Long> other_misses = new TreeSet<>();
		int non_snomed_cnt = 0;
		int miss_cnt = 0;
		int pharma_miss_cnt = 0;
		int other_miss_cnt = 0;
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
			Set<Long> sups = toSctids(reasoner.getSuperConcepts(nid));
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
				assertEquals(TinkarTerm.ROOT_VERTEX.nid(), reasoner.getSuperConcepts(nid).iterator().next());
				continue;
			} else {
				assertNotNull(parents);
			}
			if (!parents.equals(sups)) {
				misses.add(sctid);
				miss_cnt++;
				if (isas.hasAncestor(sctid, 373873005)) {
					// 373873005 |Pharmaceutical / biologic product (product)|
					pharma_miss_cnt++;
				} else if (isas.hasAncestor(sctid, 127785005)) {
					// 127785005 |Administration of substance to produce immunity, either active or
					// passive (procedure)|
				} else if (isas.hasAncestor(sctid, 713404003)) {
					// 713404003 |Vaccination given (situation)|
				} else if (isas.hasAncestor(sctid, 591000119102l)) {
					// 591000119102 |Vaccine declined by patient (situation)|
				} else if (isas.hasAncestor(sctid, 90351000119108l)) {
					// 90351000119108 |Vaccination not done (situation)|
				} else if (isas.hasAncestor(sctid, 293104008)) {
					// 293104008 |Adverse reaction to component of vaccine product (disorder)|
				} else if (isas.hasAncestor(sctid, 266758009)) {
					// 266758009 |Immunization contraindicated (situation)|
				} else {
					other_misses.add(sctid);
					other_miss_cnt++;
				}
			}
		}
		isas.getOrderedConcepts().stream().filter(other_misses::contains) //
				.limit(10) //
				.forEach((sctid) -> {
					UUID uuid = UuidUtil.fromSNOMED("" + sctid);
					int nid = PrimitiveData.nid(uuid);
					LOG.error("Miss: " + sctid + " " + PrimitiveData.text(nid));
					Set<Long> sups = toSctids(reasoner.getSuperConcepts(nid));
					Set<Long> parents = isas.getParents(sctid);
					HashSet<Long> par = new HashSet<>(parents);
					par.removeAll(sups);
					HashSet<Long> sup = new HashSet<>(sups);
					sup.removeAll(parents);
					LOG.error("Sno:  " + par);
					LOG.error("Elk:  " + sup);
					if (sups.contains(null)) {
						reasoner.getSuperConcepts(nid)
								.forEach(sup_nid -> LOG.error("   :  " + PrimitiveData.text((sup_nid.intValue()))));
					}
				});
		LOG.error("Miss cnt: " + miss_cnt);
		LOG.error("Pharma cnt: " + pharma_miss_cnt);
		LOG.error("Other cnt: " + other_miss_cnt);
		assertEquals(expected_non_snomed_cnt, non_snomed_cnt);
		assertEquals(expected_miss_cnt, miss_cnt);
		assertEquals(expected_pharma_miss_cnt, pharma_miss_cnt);
		assertEquals(expected_other_miss_cnt, other_miss_cnt);
	}

}
