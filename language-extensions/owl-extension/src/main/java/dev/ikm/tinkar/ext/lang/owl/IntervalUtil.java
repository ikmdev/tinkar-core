package dev.ikm.tinkar.ext.lang.owl;

import dev.ikm.elk.snomed.interval.Interval;
import dev.ikm.tinkar.coordinate.view.calculator.ViewCalculator;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.terms.ConceptFacade;
import dev.ikm.tinkar.terms.TinkarTerm;

public class IntervalUtil {

	public static int getNid(EntityVertex node, dev.ikm.tinkar.terms.EntityProxy.Concept concept) {
		ConceptFacade cf = node.propertyFast(concept);
		return cf.nid();
	}

	public static String getIntervalRoleString(ViewCalculator vc, EntityVertex node) {
		int role_type_nid = getNid(node, TinkarTerm.INTERVAL_ROLE_TYPE);
		Interval interval = makeInterval(node);
		return vc.getPreferredDescriptionTextWithFallbackOrNid(role_type_nid) + " \u2192 " + interval.toString(false)
				+ " " + vc.getPreferredDescriptionTextWithFallbackOrNid((int) interval.getUnitOfMeasure());
	}

	public static Interval makeInterval(EntityVertex node) {
		int lowerBound = node.propertyFast(TinkarTerm.INTERVAL_LOWER_BOUND);
		boolean lowerOpen = node.propertyFast(TinkarTerm.LOWER_BOUND_OPEN);
		int upperBound = node.propertyFast(TinkarTerm.INTERVAL_UPPER_BOUND);
		boolean upperOpen = node.propertyFast(TinkarTerm.UPPER_BOUND_OPEN);
		int unit_nid = getNid(node, TinkarTerm.UNIT_OF_MEASURE);
		return new Interval(lowerBound, lowerOpen, upperBound, upperOpen, unit_nid);
	}

}
