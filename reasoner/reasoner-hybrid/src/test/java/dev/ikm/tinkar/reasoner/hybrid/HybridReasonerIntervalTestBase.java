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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.ikm.elk.snomed.model.Concept;
import dev.ikm.elk.snomed.model.Definition;
import dev.ikm.elk.snomed.model.DefinitionType;
import dev.ikm.reasoner.hybrid.snomed.Interval;
import dev.ikm.reasoner.hybrid.snomed.IntervalReasoner;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiom.Atom.TypedAtom.IntervalRole;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpression;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpressionBuilder;
import dev.ikm.tinkar.ext.lang.owl.OwlElToLogicalExpression;
import dev.ikm.tinkar.reasoner.elksnomed.ElkSnomedData;
import dev.ikm.tinkar.reasoner.elksnomed.ElkSnomedUtil;
import dev.ikm.tinkar.reasoner.service.ReasonerService;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.TinkarTerm;

public abstract class HybridReasonerIntervalTestBase extends HybridReasonerTestBase {

	private static final Logger LOG = LoggerFactory.getLogger(HybridReasonerIntervalTestBase.class);

	private void updatePremature() throws Exception {
		ViewCalculator vc = PrimitiveDataTestUtil.getViewCalculator();
		// 103335007 |Duration (attribute)|
		int duration_role_nid = ElkSnomedData.getNid(103335007);
		{
			LogicalExpressionBuilder builder = new LogicalExpressionBuilder();
			// 762706009 |Concept model data attribute (attribute)|
			int attr_nid = ElkSnomedData.getNid(762706009);
			builder.IntervalPropertySet(builder.And(builder.ConceptAxiom(attr_nid)));
			LogicalExpression le = builder.build();
			LOG.info("IntervalPropertySet:\n" + le);
			ElkSnomedUtil.updateStatedSemantic(vc, duration_role_nid, le);
			SemanticEntityVersion sev = ElkSnomedUtil.getStatedSemantic(vc, duration_role_nid);
			LOG.info("SEV:\n" + sev);
		}
		// 395507008 |Premature infant (finding)|
		int pi_nid = ElkSnomedData.getNid(395507008);
		Concept pi_con = ElkSnomedUtil.getConcept(vc, pi_nid);
		Path intervals_file = Paths.get("src/test/resources",
				"intervals-" + getEditionDir() + "-" + getVersion() + ".txt");
		LOG.info("Intervals file: " + intervals_file);
		for (String line : Files.readAllLines(intervals_file)) {
			String[] fields = line.split("\t");
			long sctid = Long.parseLong(fields[0]);
			int nid = ElkSnomedData.getNid(sctid);
			Interval interval = Interval.fromString(fields[1]);
			int units_nid = ElkSnomedData.getNid(interval.getUnitOfMeasure());
			interval.setUnitOfMeasure(units_nid);
			LOG.info("Interval: " + interval + " " + sctid + " " + PrimitiveData.text(nid));
			SemanticEntityVersion sev = ElkSnomedUtil.getStatedSemantic(vc, nid);
			LOG.info("SEV:\n" + sev);
			Concept con = ElkSnomedUtil.getConcept(vc, nid);
			Definition def = con.getDefinitions().getFirst();
			def.setDefinitionType(DefinitionType.EquivalentConcept);
			def.getSuperConcepts().clear();
			def.addSuperConcept(pi_con);
			def.getRoleGroups().clear();
			def.getUngroupedRoles().clear();
			def.getUngroupedConcreteRoles().clear();
			LogicalExpression le = new OwlElToLogicalExpression().build(def);
			LogicalExpressionBuilder builder = new LogicalExpressionBuilder(le);
			IntervalRole interval_role = builder.IntervalRole(ConceptFacade.make(duration_role_nid),
					interval.getLowerBound(), interval.isLowerOpen(), interval.getUpperBound(), interval.isUpperOpen(),
					ConceptFacade.make(units_nid));
			builder.addToFirstAnd(0, interval_role);
			le = builder.build();
			LOG.info("ROLE:\n" + le);
			ElkSnomedUtil.updateStatedSemantic(vc, nid, le);
			LOG.info("SEV:\n" + ElkSnomedUtil.getStatedSemantic(vc, nid));
		}
	}

	public ReasonerService initReasonerService() {
		ReasonerService rs = new IntervalReasonerService();
		rs.init(getViewCalculator(), TinkarTerm.EL_PLUS_PLUS_STATED_AXIOMS_PATTERN,
				TinkarTerm.EL_PLUS_PLUS_INFERRED_AXIOMS_PATTERN);
		rs.setProgressUpdater(null);
		return rs;
	}

	@Test
	public void premature() throws Exception {
		updatePremature();
		ReasonerService rs = initReasonerService();
		rs.extractData();
		rs.loadData();
		rs.computeInferences();
		rs.buildNecessaryNormalForm();
		rs.writeInferredResults();
//		ElkSnomedData data = buildSnomedData();
//		LOG.info("Create ontology");
//		SnomedOntology snomedOntology = new SnomedOntology(data.getConcepts(), data.getRoleTypes(),
//				data.getConcreteRoleTypes());
//		List<ConcreteRoleType> intervalRoles = List.copyOf(data.getIntervalRoleTypes());
//		intervalRoles.forEach(x -> LOG.info("IR: " + PrimitiveData.text((int) x.getId())));
//		IntervalReasoner ir = IntervalReasoner.create(snomedOntology, intervalRoles);
//		// 395507008 |Premature infant (finding)|
//		int pi_nid = ElkSnomedData.getNid(395507008);
//		print(ir, pi_nid, 0);
//		NecessaryNormalFormBuilder nnfb = NecessaryNormalFormBuilder.create(snomedOntology, ir.getSuperConcepts(),
//				ir.getSuperRoleTypes(false), TinkarTerm.ROOT_VERTEX.nid());
//		nnfb.generate();
	}

	private void print(IntervalReasoner ir, int nid, int i) {
		LOG.info("\t".repeat(i) + PrimitiveData.text(nid));
		ir.getSubConcepts(nid).forEach(x -> print(ir, x.intValue(), i + 1));
	}

}
