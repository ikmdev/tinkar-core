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

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.collections.api.list.ImmutableList;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.ikm.elk.snomed.ConceptComparer;
import dev.ikm.elk.snomed.SnomedDescriptions;
import dev.ikm.elk.snomed.SnomedIds;
import dev.ikm.elk.snomed.SnomedIsa;
import dev.ikm.elk.snomed.SnomedLoader;
import dev.ikm.elk.snomed.SnomedOntology;
import dev.ikm.elk.snomed.model.Concept;
import dev.ikm.elk.snomed.model.Definition;
import dev.ikm.tinkar.common.id.IntIdSet;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.reasoner.service.ReasonerService;
import dev.ikm.tinkar.terms.TinkarTerm;

public abstract class ElkSnomedReasonerWriteTestBase extends ElkSnomedTestBase {

	private static final Logger LOG = LoggerFactory.getLogger(ElkSnomedReasonerWriteTestBase.class);

	@Test
	public void reasonerWrite() throws Exception {
		ReasonerService rs = initReasonerService();
		rs.extractData();
		rs.loadData();
		rs.computeInferences();
		rs.buildNecessaryNormalForm();
		rs.writeInferredResults();
		int inferredNavigationPatternNid = TinkarTerm.INFERRED_NAVIGATION_PATTERN.nid();
		int inferredPatternNid = rs.getViewCalculator().viewCoordinateRecord().logicCoordinate()
				.inferredAxiomsPatternNid();
		SnomedIsa isas = SnomedIsa.init(rels_file);
		SnomedDescriptions descr = SnomedDescriptions.init(descriptions_file);
		SnomedOntology inferredOntology = new SnomedLoader().load(concepts_file, descriptions_file, rels_file,
				values_file);
		inferredOntology.setDescriptions(descr);
		inferredOntology.setNames();
		ElkSnomedData data = buildSnomedData();
		NidToSctid nid_to_sctid = new NidToSctid(data, inferredOntology);
		nid_to_sctid.build();
		ConceptComparer cc = new ConceptComparer(inferredOntology);
		int parent_miss = 0;
		int child_miss = 0;
		int mis_match_cnt = 0;
		for (long sctid : isas.getOrderedConcepts()) {
			int nid = ElkSnomedData.getNid(sctid);
			{
				Set<Integer> expected_parent_nids = isas.getParents(sctid).stream().map(ElkSnomedData::getNid)
						.collect(Collectors.toSet());
				if (sctid == SnomedIds.root) {
					expected_parent_nids = Set.of(TinkarTerm.ROOT_VERTEX.nid());
					LOG.warn("Reset expected for " + sctid + " " + PrimitiveData.text(nid));
				}
				Set<Integer> expected_child_nids = isas.getChildren(sctid).stream().map(ElkSnomedData::getNid)
						.collect(Collectors.toSet());
				int[] inferredNavigationNids = PrimitiveData.get().semanticNidsForComponentOfPattern(nid,
						inferredNavigationPatternNid);
				if (inferredNavigationNids.length == 0) {
					LOG.error("No semantic of pattern " + PrimitiveData.text(inferredNavigationPatternNid)
							+ " for component: " + PrimitiveData.text(nid));
				} else if (inferredNavigationNids.length == 1) {
					Latest<SemanticEntityVersion> latestInferredNavigationSemantic = rs.getViewCalculator()
							.latest(inferredNavigationNids[0]);
					if (latestInferredNavigationSemantic.isPresent()) {
						ImmutableList<Object> latestInferredNavigationFields = latestInferredNavigationSemantic.get()
								.fieldValues();
						IntIdSet actual_child_nids = (IntIdSet) latestInferredNavigationFields.get(0);
						IntIdSet actual_parent_nids = (IntIdSet) latestInferredNavigationFields.get(1);
						if (!expected_parent_nids.equals(actual_parent_nids.mapToSet(x -> x))) {
							LOG.error("Parents: " + sctid + " " + descr.getFsn(sctid));
							parent_miss++;
						}
						if (!expected_child_nids.equals(actual_child_nids.mapToSet(x -> x))) {
							LOG.error("Children: " + sctid + " " + descr.getFsn(sctid));
							child_miss++;
						}
					} else {
						LOG.error("No LATEST semantic of pattern " + PrimitiveData.text(inferredNavigationPatternNid)
								+ " for component: " + PrimitiveData.text(nid));
					}

				} else {
					LOG.error("More than one semantic of pattern " + PrimitiveData.text(inferredNavigationPatternNid)
							+ " for component: " + PrimitiveData.text(nid));
				}
			}
			{
				if (sctid == SnomedIds.root) {
					LOG.warn("Skipping compare for " + sctid + " " + PrimitiveData.text(nid));
					continue;
				}
				int[] inferredSemanticNids = PrimitiveData.get().semanticNidsForComponentOfPattern(nid,
						inferredPatternNid);
				if (inferredSemanticNids.length == 0) {
					LOG.error("No semantic of pattern " + PrimitiveData.text(inferredPatternNid) + " for component: "
							+ PrimitiveData.text(nid));
				} else if (inferredSemanticNids.length == 1) {
					Latest<SemanticEntityVersion> latestInferredSemantic = rs.getViewCalculator()
							.latest(inferredSemanticNids[0]);
					if (latestInferredSemantic.isPresent()) {
						ImmutableList<Object> latestInferredFields = latestInferredSemantic.get().fieldValues();
						DiTreeEntity latestInferredTree = (DiTreeEntity) latestInferredFields.get(0);
						ElkSnomedDataBuilder builder = new ElkSnomedDataBuilder(null, null, new ElkSnomedData());
						List<Concept> concepts = builder.processDefinition(nid, latestInferredTree);
						assertEquals(1, concepts.size());
						Definition new_def = nid_to_sctid
								.makeNewDefinition(concepts.getFirst().getDefinitions().getFirst());
						Concept new_concept = new Concept(sctid);
						new_concept.addDefinition(new_def);
						if (!cc.compare(new_concept)) {
							LOG.error("Mis match: " + new_concept);
							mis_match_cnt++;
						}
					} else {
						LOG.error("No LATEST semantic of pattern " + PrimitiveData.text(inferredNavigationPatternNid)
								+ " for component: " + PrimitiveData.text(nid));
					}
				} else {
					LOG.error("More than one semantic of pattern " + PrimitiveData.text(inferredPatternNid)
							+ " for component: " + PrimitiveData.text(nid));
				}
			}
		}
		assertEquals(0, parent_miss);
		// TODO 609096000 Role group (attribute) & 1295447006 Annotation attribute
		// (attribute)
		assertEquals(2, child_miss);
		assertEquals(0, mis_match_cnt);
	}

}
