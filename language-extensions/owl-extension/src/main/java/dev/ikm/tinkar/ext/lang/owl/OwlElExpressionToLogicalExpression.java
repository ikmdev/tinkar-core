package dev.ikm.tinkar.ext.lang.owl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.ikm.elk.snomed.SnomedIds;
import dev.ikm.elk.snomed.SnomedOntology;
import dev.ikm.elk.snomed.model.Concept;
import dev.ikm.elk.snomed.model.ConcreteRole;
import dev.ikm.elk.snomed.model.ConcreteRoleType;
import dev.ikm.elk.snomed.model.Definition;
import dev.ikm.elk.snomed.model.Role;
import dev.ikm.elk.snomed.model.RoleGroup;
import dev.ikm.elk.snomed.model.RoleType;
import dev.ikm.elk.snomed.owlel.OwlElObjectFactory;
import dev.ikm.elk.snomed.owlel.model.OwlElObject;
import dev.ikm.elk.snomed.owlel.parser.SnomedOfsParser;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiom;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiom.Atom;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalAxiom.Atom.Connective.And;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpression;
import dev.ikm.tinkar.entity.graph.adaptor.axiom.LogicalExpressionBuilder;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.TinkarTerm;

public class OwlElExpressionToLogicalExpression {

	private static final Logger LOG = LoggerFactory.getLogger(OwlElExpressionToLogicalExpression.class);

	private List<String> owlExpressions;

	private int conceptNid;

	private SnomedOntology ontology;

	private LogicalExpressionBuilder builder;

	public OwlElExpressionToLogicalExpression(List<String> owlExpressions, int conceptNid) {
		super();
		this.owlExpressions = owlExpressions;
		this.conceptNid = conceptNid;
	}

	private static String uuidMatchToNid(MatchResult uuidMatch, boolean sub_object_property) {
		String str = uuidMatch.group();
		// remove []
		str = str.substring(1, str.length() - 1);
		UUID uuid = UUID.fromString(str);
		// The OwlEl transform depends on this value
		// TODO make this a param in the owl el transform
		if (!sub_object_property && TinkarTerm.ROLE_GROUP.contains(uuid))
			return String.valueOf(SnomedIds.role_group);
		int nid = PrimitiveData.nid(uuid);
		return String.valueOf(nid);
	}

	public static String uuidToNid(String expr) {
		// TODO make this a param in the owl el transform
		boolean sub_object_property = expr.startsWith("SubObjectProperty");
		// uuids are enclosed in []
		Pattern pattern = Pattern.compile("\\[.+?\\]");
		Matcher matcher = pattern.matcher(expr);
		return matcher.replaceAll(uuid -> uuidMatchToNid(uuid, sub_object_property));
	}

	public void testParse() {
		LOG.info("Exprs (" + owlExpressions.size() + ") " + PrimitiveData.text(conceptNid));
		owlExpressions.forEach(expr -> LOG.info("     : " + expr));
		for (String owlExpression : owlExpressions) {
			OwlElObjectFactory factory = new OwlElObjectFactory();
			SnomedOfsParser parser = new SnomedOfsParser(factory);
			OwlElObject obj = parser.buildExpression(owlExpression);
			LOG.info("Res: " + obj);
			if (parser.getSyntaxError() != null)
				LOG.error(owlExpression);
		}
	}

	private void load() throws IOException {
		List<String> nid_exprs = owlExpressions.stream().map(OwlElExpressionToLogicalExpression::uuidToNid).toList();
		ontology = SnomedOntology.load(nid_exprs);
	}

	public LogicalExpression build() throws Exception {
		builder = new LogicalExpressionBuilder();
		if (!owlExpressions.isEmpty()) {
			load();
			Concept con = ontology.getConcept(conceptNid);
			if (con != null)
				process(con);
			// TODO role & concrete role
			RoleType role_type = ontology.getRoleType(conceptNid);
			if (role_type != null)
				process(role_type);
			ConcreteRoleType concrete_role_type = ontology.getConcreteRoleType(conceptNid);
			if (concrete_role_type != null)
				process(concrete_role_type);
		}
		return builder.build();
	}

	private ConceptFacade getConceptFacade(long id) {
		// TODO Maybe (ConceptFacade) EntityProxy.Concept.make((int) role_type.getId()
		Optional<? extends ConceptFacade> role_type_cf = EntityService.get().getEntity((int) id);
		return role_type_cf.get();
	}

	private void process(RoleType role_type) {
		List<Atom> exprs = new ArrayList<>();
		for (RoleType sup : role_type.getSuperRoleTypes()) {
			exprs.add(builder.ConceptAxiom((int) sup.getId()));
		}
		if (role_type.isTransitive())
			exprs.add(builder.ConceptAxiom(TinkarTerm.TRANSITIVE_PROPERTY));
		if (role_type.isReflexive())
			exprs.add(builder.ConceptAxiom(TinkarTerm.REFLEXIVE_PROPERTY));
		if (role_type.getChained() != null) {
			ImmutableList<ConceptFacade> chain = Lists.immutable.of(getConceptFacade(role_type.getId()),
					getConceptFacade(role_type.getChained().getId()));
			exprs.add(builder.PropertySequenceImplicationAxiom(chain, getConceptFacade(role_type.getId())));
		}
		And expr = builder.And(toArray(exprs));
		builder.PropertySet(expr);
	}

	private void process(ConcreteRoleType role_type) {
		List<Atom> exprs = new ArrayList<>();
		for (ConcreteRoleType sup : role_type.getSuperConcreteRoleTypes()) {
			exprs.add(builder.ConceptAxiom((int) sup.getId()));
		}
		And expr = builder.And(toArray(exprs));
		builder.DataPropertySet(expr);
	}

	private void process(Concept con) {
		for (Definition def : con.getDefinitions()) {
			processDefinition(def, false);
		}
		for (Definition def : con.getGciDefinitions()) {
			processDefinition(def, true);
		}
	}

	private Atom[] toArray(List<Atom> exprs) {
		return exprs.toArray(new Atom[exprs.size()]);
	}

	private void processDefinition(Definition def, boolean gci) {
		List<Atom> exprs = new ArrayList<>();
		for (Concept sup : def.getSuperConcepts()) {
			exprs.add(builder.ConceptAxiom((int) sup.getId()));
		}
		exprs.addAll(buildRoles(def.getUngroupedRoles()));
		exprs.addAll(buildConcreteRoles(def.getUngroupedConcreteRoles()));
		for (RoleGroup rg : def.getRoleGroups()) {
			List<Atom> roles = buildRoles(rg.getRoles());
			roles.addAll(buildConcreteRoles(rg.getConcreteRoles()));
			exprs.add(builder.SomeRole(TinkarTerm.ROLE_GROUP, builder.And(toArray(roles))));
		}
		And expr = builder.And(toArray(exprs));
		if (gci) {
			builder.InclusionSet(expr);
		} else {
			switch (def.getDefinitionType()) {
			case EquivalentConcept -> builder.SufficientSet(expr);
			case SubConcept -> builder.NecessarySet(expr);
			}
		}
	}

	private List<Atom> buildRoles(Set<Role> roles) {
		List<LogicalAxiom.Atom> exprs = new ArrayList<>();
		for (Role role : roles) {
			exprs.add(builder.SomeRole(getConceptFacade(role.getRoleType().getId()),
					builder.ConceptAxiom((int) role.getConcept().getId())));
		}
		return exprs;
	}

	private List<Atom> buildConcreteRoles(Set<ConcreteRole> roles) {
		List<LogicalAxiom.Atom> exprs = new ArrayList<>();
		for (ConcreteRole role : roles) {
			Object value = switch (role.getValueType()) {
			// TODO Use BigDecimal
			case Decimal -> Double.parseDouble(role.getValue());
			case Double -> Double.parseDouble(role.getValue());
			case Float -> Float.parseFloat(role.getValue());
			case Integer -> Integer.parseInt(role.getValue());
			case String -> Float.parseFloat(role.getValue());
			};
			exprs.add(builder.FeatureAxiom(getConceptFacade(role.getConcreteRoleType().getId()), TinkarTerm.EQUAL_TO,
					value));
		}
		return exprs;
	}

}
