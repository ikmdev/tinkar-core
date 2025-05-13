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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.eclipse.collections.api.set.primitive.ImmutableIntSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.ikm.elk.snomed.SnomedIds;
import dev.ikm.elk.snomed.SnomedIsa;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.reasoner.service.ReasonerService;
import dev.ikm.tinkar.reasoner.service.UnsupportedReasonerProcessIncremental;
import dev.ikm.tinkar.terms.TinkarTerm;

public class ElkSnomedIncrementalTestIT {

	private static final Logger LOG = LoggerFactory.getLogger(ElkSnomedIncrementalTestIT.class);

	private static final int version_start = 20210731; // 20240101;

	private static boolean includeVersion(String version) {
		return Integer.parseInt(version) >= version_start;
	}

	protected String getDir() {
		return "target/data/snomed-test-data-" + getEditionDir() + "-full";
	}

	protected String getEdition() {
		return "INT";
	}

	protected String getEditionDir() {
		return "intl";
	}

	protected String getVersion() {
		return "20250101";
	}

	protected Path axioms_file = Paths.get(getDir(),
			"sct2_sRefset_OWLExpressionFull_" + getEdition() + "_" + getVersion() + ".txt");

	protected Path concepts_file = Paths.get(getDir(),
			"sct2_Concept_Full_" + getEdition() + "_" + getVersion() + ".txt");

	protected Path rels_file = Paths.get(getDir(),
			"sct2_Relationship_Full_" + getEdition() + "_" + getVersion() + ".txt");

	protected Path values_file = Paths.get(getDir(),
			"sct2_RelationshipConcreteValues_Full_" + getEdition() + "_" + getVersion() + ".txt");

	protected Path descriptions_file = Paths.get(getDir(),
			"sct2_Description_Full-en_" + getEdition() + "_" + getVersion() + ".txt");

	private static String test_case = "snomed-intl-20250101";

	private static List<String> effectiveTimes;

	@BeforeAll
	public static void startPrimitiveData() throws IOException {
		PrimitiveDataTestUtil.setupPrimitiveData(test_case + "-sa");
		PrimitiveData.start();
	}

	@AfterAll
	public static void stopPrimitiveData() {
		PrimitiveDataTestUtil.stopPrimitiveData();
	}

	@BeforeEach
	protected void filesExist() {
		assertTrue(Files.exists(axioms_file), "No file: " + axioms_file);
		assertTrue(Files.exists(concepts_file), "No file: " + concepts_file);
		assertTrue(Files.exists(rels_file), "No file: " + rels_file);
		assertTrue(Files.exists(values_file), "No file: " + values_file);
		assertTrue(Files.exists(descriptions_file), "No file: " + descriptions_file);
		LOG.info("Files exist");
		LOG.info("\t" + axioms_file);
		LOG.info("\t" + concepts_file);
		LOG.info("\t" + rels_file);
		LOG.info("\t" + values_file);
		LOG.info("\t" + descriptions_file);
	}

	public List<String> getEffectiveTimes() throws IOException {
		if (effectiveTimes == null) {
			// id effectiveTime active moduleId definitionStatusId
			try (Stream<String> st = Files.lines(concepts_file)) {
				effectiveTimes = st.skip(1).map(line -> line.split("\\t")) //
						.map(fields -> fields[1]) // effectiveTime
						.distinct().sorted().toList();
			}
			;
		}
		return effectiveTimes;
	}

	@Test
	public void effectiveTimes() throws Exception {
		LOG.info("effectiveTimes");
		int cnt = 0;
		int cnt_20210731 = 0;
		for (String effective_time : getEffectiveTimes()) {
			LOG.info("Snomed version: " + effective_time);
			cnt++;
			if (includeVersion(effective_time))
				cnt_20210731++;
			ViewCalculator vc = PrimitiveDataTestUtil.getViewCalculator(effective_time);
			long vc_time = ((StampCalculatorWithCache) vc.stampCalculator()).filter().time();
			LOG.info("\tView calculator time: " + Instant.ofEpochMilli(vc_time) + " " + vc_time);
			HashSet<Integer> no_sctid_nids = new HashSet<>();
			HashSet<Integer> active_nids = new HashSet<>();
			HashSet<Integer> inactive_nids = new HashSet<>();
			vc.forEachSemanticVersionOfPattern(TinkarTerm.IDENTIFIER_PATTERN.nid(), (semanticEntityVersion, _) -> {
				int nid = semanticEntityVersion.referencedComponentNid();
				String sctid = PrimitiveDataTestUtil.getSctid(nid, vc);
				if (sctid != null) {
					if (vc.latestIsActive(nid)) {
						active_nids.add(nid);
					} else {
						inactive_nids.add(nid);
					}
				} else {
					no_sctid_nids.add(nid);
				}
			});
			LOG.info("\tT: " + (active_nids.size() + inactive_nids.size()) + " A: " + active_nids.size() + " I: "
					+ inactive_nids.size() + " Non-Snomed: " + no_sctid_nids.size());
		}
		assertEquals(79, cnt);
		assertEquals(40, cnt_20210731);
	}

	@Test
	public void axioms() throws Exception {
		LOG.info("axioms");
		for (String effective_time : getEffectiveTimes()) {
			if (!includeVersion(effective_time))
				continue;
			LOG.info("Snomed version: " + effective_time);
			ViewCalculator vc = PrimitiveDataTestUtil.getViewCalculator(effective_time);
			long time = ((StampCalculatorWithCache) vc.stampCalculator()).filter().time();
			LOG.info("View calculator time: " + Instant.ofEpochMilli(time) + " " + time);
			HashSet<Integer> no_sctid_nids = new HashSet<>();
			AtomicInteger active = new AtomicInteger();
			AtomicInteger inactive = new AtomicInteger();
			AtomicInteger time_cnt = new AtomicInteger();
			vc.forEachSemanticVersionOfPattern(TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid(),
					(semanticEntityVersion, _) -> {
						if (semanticEntityVersion.time() == time)
							time_cnt.incrementAndGet();
						int nid = semanticEntityVersion.referencedComponentNid();
						String sctid = PrimitiveDataTestUtil.getSctid(nid, vc);
						if (sctid != null) {
							ZonedDateTime zdt = Instant.ofEpochMilli(semanticEntityVersion.time())
									.atZone(ZoneId.of("UTC"));
							if (zdt.getHour() != 0)
								LOG.info("SEV time: " + Instant.ofEpochMilli(semanticEntityVersion.time()) + " " + sctid
										+ " " + PrimitiveData.text(nid));
							if (Long.parseLong(sctid) != SnomedIds.root)
								assertEquals(0, zdt.getHour());
							if (semanticEntityVersion.active()) {
								active.incrementAndGet();
							} else {
								inactive.incrementAndGet();
							}
						} else {
							no_sctid_nids.add(nid);
						}
					});
			LOG.info("\t" + (active.get() + inactive.get()) + " " + active.get() + " " + inactive.get() + " "
					+ no_sctid_nids.size() + " " + time_cnt.get());
		}
	}

	@Test
	public void timeZone() {
		LOG.info("Time zone: " + new SimpleDateFormat("yyyyMMdd").getCalendar().getTimeZone());
	}

	public ReasonerService initReasonerService(String version) throws Exception {
		LOG.info("Init reasoner service");
		ViewCalculator vc = PrimitiveDataTestUtil.getViewCalculator(version);
		ReasonerService rs = ElkSnomedTestBase.getElkSnomedReasonerService();
		rs.init(vc, TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN, TinkarTerm.EL_PLUS_PLUS_INFERRED_AXIOMS_PATTERN);
		rs.setProgressUpdater(null);
		rs.extractData();
		rs.loadData();
		rs.computeInferences();
		return rs;
	}

	@Test
	public void classify() throws Exception {
		LOG.info("classify");
		List<Long> full_times = new ArrayList<>();
		List<Long> incr_times = new ArrayList<>();
		List<Long> nnf_times = new ArrayList<>();
		ReasonerService rs = null;
		for (String effective_time : getEffectiveTimes()) {
			if (!includeVersion(effective_time))
				continue;
			LOG.info("Snomed version: " + effective_time);
			if (Integer.parseInt(effective_time) == version_start) {
				rs = initReasonerService(effective_time);
				continue;
			}
			ViewCalculator vc = PrimitiveDataTestUtil.getViewCalculator(effective_time);
			long time = ((StampCalculatorWithCache) vc.stampCalculator()).filter().time();
			LOG.info("\tView calculator time: " + Instant.ofEpochMilli(time) + " " + time);
			HashMap<Integer, SemanticEntityVersion> active = new HashMap<>();
			HashMap<Integer, SemanticEntityVersion> inactive = new HashMap<>();
			vc.forEachSemanticVersionOfPattern(TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN.nid(),
					(semanticEntityVersion, _) -> {
						if (semanticEntityVersion.time() == time) {
							int nid = semanticEntityVersion.referencedComponentNid();
							if (semanticEntityVersion.active()) {
								if (active.containsKey(nid))
									throw new RuntimeException("" + nid);
								active.put(nid, semanticEntityVersion);
							} else {
								if (inactive.containsKey(nid))
									throw new RuntimeException("" + nid);
								inactive.put(nid, semanticEntityVersion);
							}
						}
					});
			assertEquals(0, active.keySet().stream().filter(el -> inactive.keySet().contains(el)).count());
			LOG.info("\tA: " + active.size() + " I: " + inactive.size());
			{
				// 1295448001 |Attribution (attribute)|
				active.remove(ElkSnomedData.getNid(1295448001));
				// 1295449009 |Additional relationship attribute (attribute)|
				active.remove(ElkSnomedData.getNid(1295449009));
			}
			try {
				long beg = System.currentTimeMillis();
				rs.processIncremental(new ArrayList<>(inactive.keySet()), new ArrayList<>(active.values()));
				incr_times.add(System.currentTimeMillis() - beg);
			} catch (UnsupportedReasonerProcessIncremental ex) {
				LOG.error(ex.getMessage());
				Matcher m = Pattern.compile("-\\d+").matcher(ex.getMessage());
				if (m.find()) {
					String nid_str = m.group();
					int nid = Integer.parseInt(nid_str);
					LOG.error("\tSctid: " + PrimitiveDataTestUtil.getSctid(nid, vc));
					LOG.error("\t" + nid_str + " " + PrimitiveData.text(nid));
				}
				long beg = System.currentTimeMillis();
				rs = initReasonerService(effective_time);
				full_times.add(System.currentTimeMillis() - beg);
			}
			long beg = System.currentTimeMillis();
//			rs.buildNecessaryNormalForm();
			nnf_times.add(System.currentTimeMillis() - beg);
			checkParents(rs, Integer.parseInt(effective_time));
		}
		LOG.info("Full: " + full_times.size() + " " + full_times.stream().collect(Collectors.averagingLong(x -> x)));
		LOG.info("Incr: " + incr_times.size() + " " + incr_times.stream().collect(Collectors.averagingLong(x -> x)));
		LOG.info("NNF: " + nnf_times.size() + " " + nnf_times.stream().collect(Collectors.averagingLong(x -> x)));
	}

	private Set<Long> toSctids(ImmutableIntSet nids, HashMap<Integer, Long> nid_sctid_map) {
		return Arrays.stream(nids.toArray()).mapToObj(nid -> nid_sctid_map.get(nid)).collect(Collectors.toSet());
	}

	private void checkParents(ReasonerService rs, int version) throws Exception {
		TreeSet<Long> misses = new TreeSet<>();
		TreeSet<Long> other_misses = new TreeSet<>();
		int non_snomed_cnt = 0;
		int miss_cnt = 0;
		SnomedIsa isas = SnomedIsa.init(rels_file, version);
//		SnomedDescriptions descr = SnomedDescriptions.init(descriptions_file);
		HashMap<Integer, Long> nid_sctid_map = new HashMap<>();
		for (long sctid : isas.getOrderedConcepts()) {
			int nid = ElkSnomedData.getNid(sctid);
			nid_sctid_map.put(nid, sctid);
//			if (ontology.getConcept(nid) == null)
//				LOG.info("No concept for: " + sctid + " " + descr.getFsn(sctid));
		}
		for (int nid : rs.getReasonerConceptSet().toArray()) {
			Set<Long> sups = toSctids(rs.getParents(nid), nid_sctid_map);
			Long sctid = nid_sctid_map.get((int) nid);
			if (sctid == null) {
				non_snomed_cnt++;
				continue;
			}
//			// 361195001 |Pulmonary fibroplasia (disorder)|
//			// 371931008 |Combined diagnostic and therapeutic procedure (procedure)|
//			// 109998009 |Myelodysplastic syndrome with ring sideroblasts and single lineage
//			// dysplasia (disorder)|
//			// 19776001 |Decreased size (finding)|
//			// 307511000 |Under-running of bleeding duodenal ulcer (procedure)|
//			if (List.of(361195001l, 371931008l, 109998009l, 19776001l, 307511000l).contains(sctid))
//				LOG.info("In reasoner concept set: " + sctid + " " + nid + " " + PrimitiveData.text(nid));
			Set<Long> parents = isas.getParents(sctid);
			if (sctid == SnomedIds.root) {
				assertTrue(parents.isEmpty());
				// has a parent in the db
				assertEquals(1, sups.size());
				assertEquals(TinkarTerm.ROOT_VERTEX.nid(), rs.getParents(nid).intIterator().next());
				continue;
			} else {
				assertNotNull(parents);
			}
			if (!parents.equals(sups)) {
				misses.add(sctid);
				miss_cnt++;
			}
		}
		isas.getOrderedConcepts().stream().filter(other_misses::contains) //
				.limit(10) //
				.forEach((sctid) -> {
					UUID uuid = UuidUtil.fromSNOMED("" + sctid);
					int nid = PrimitiveData.nid(uuid);
					LOG.error("Miss: " + sctid + " " + PrimitiveData.text(nid));
					Set<Long> sups = toSctids(rs.getParents(nid), nid_sctid_map);
					Set<Long> parents = isas.getParents(sctid);
					HashSet<Long> par = new HashSet<>(parents);
					par.removeAll(sups);
					HashSet<Long> sup = new HashSet<>(sups);
					sup.removeAll(parents);
					LOG.error("Sno:  " + par);
					LOG.error("Elk:  " + sup);
					if (sups.contains(null)) {
						rs.getParents(nid).forEach(sup_nid -> LOG.error("   :  " + PrimitiveData.text((sup_nid))));
					}
				});
		if (miss_cnt != 0)
			LOG.error("Miss cnt: " + miss_cnt);
		int expected_non_snomed_cnt = PrimitiveDataTestUtil.getPrimordialNids().size()
				- PrimitiveDataTestUtil.getPrimordialNidsWithSctids().size();
		if (expected_non_snomed_cnt != non_snomed_cnt)
			LOG.error("Non-snomed: Expect: " + expected_non_snomed_cnt + " Actual: " + non_snomed_cnt);
		assertEquals(0, miss_cnt);
		assertEquals(expected_non_snomed_cnt, non_snomed_cnt);
	}

}
