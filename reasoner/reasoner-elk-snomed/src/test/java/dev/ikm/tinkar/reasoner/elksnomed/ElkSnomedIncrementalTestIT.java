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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.ikm.elk.snomed.SnomedIds;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.reasoner.service.ReasonerService;
import dev.ikm.tinkar.terms.TinkarTerm;

public class ElkSnomedIncrementalTestIT {

	private static final Logger LOG = LoggerFactory.getLogger(ElkSnomedIncrementalTestIT.class);

	private static final int version_start = 20210731;

	private static boolean includeVersion(String version) {
		return Integer.parseInt(version) >= version_start;
	}

//	protected String getDir() {
//		return System.getProperty("user.home")
//				+ "/data/snomed/SnomedCT_InternationalRF2_PRODUCTION_20250101T120000Z/Full/Terminology";
//	}

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
		ReasonerService rs;
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
		}
	}

}
