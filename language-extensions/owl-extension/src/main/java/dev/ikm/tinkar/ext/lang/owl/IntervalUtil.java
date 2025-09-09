package dev.ikm.tinkar.ext.lang.owl;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import dev.ikm.elk.snomed.interval.Interval;
import dev.ikm.elk.snomed.model.Concept;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.TinkarTerm;

public class IntervalUtil {

	@SuppressWarnings("unused")
	private static final Logger LOG = LoggerFactory.getLogger(IntervalUtil.class);

	public static int getNid(EntityVertex node, dev.ikm.tinkar.terms.EntityProxy.Concept concept) {
		ConceptFacade cf = node.propertyFast(concept);
		return cf.nid();
	}

	public static String getIntervalRoleString(ViewCalculator vc, EntityVertex node) {
		int role_type_nid = getNid(node, TinkarTerm.INTERVAL_ROLE_TYPE);
		Interval interval = makeInterval(node);
		return vc.getPreferredDescriptionTextWithFallbackOrNid(role_type_nid) + " \u2192 " + interval.toString(false)
				+ " " + vc.getPreferredDescriptionTextWithFallbackOrNid((int) interval.getUnitOfMeasure().getId());
	}

	private static int toInt(Object object) {
		return switch (object) {
		case Integer x -> x;
		case Long x -> x.intValue();
		default -> throw new IllegalArgumentException(object + " " + object.getClass());
		};
	}

	public static Interval makeInterval(EntityVertex node) {
		Object lb = node.propertyFast(TinkarTerm.INTERVAL_LOWER_BOUND);
		int lowerBound = toInt(lb);
		boolean lowerOpen = node.propertyFast(TinkarTerm.LOWER_BOUND_OPEN);
		Object ub = node.propertyFast(TinkarTerm.INTERVAL_UPPER_BOUND);
		int upperBound = toInt(ub);
		boolean upperOpen = node.propertyFast(TinkarTerm.UPPER_BOUND_OPEN);
		int unit_nid = getNid(node, TinkarTerm.UNIT_OF_MEASURE);
		return new Interval(lowerBound, lowerOpen, upperBound, upperOpen, new Concept(unit_nid));
	}

}
