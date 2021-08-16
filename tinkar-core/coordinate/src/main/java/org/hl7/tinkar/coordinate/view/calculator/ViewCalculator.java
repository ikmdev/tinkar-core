package org.hl7.tinkar.coordinate.view.calculator;

import org.hl7.tinkar.coordinate.language.calculator.LanguageCalculatorDelegate;
import org.hl7.tinkar.coordinate.logic.PremiseType;
import org.hl7.tinkar.coordinate.logic.calculator.LogicCalculatorDelegate;
import org.hl7.tinkar.coordinate.navigation.calculator.NavigationCalculatorDelegate;
import org.hl7.tinkar.coordinate.stamp.calculator.Latest;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculatorDelegate;
import org.hl7.tinkar.coordinate.view.ViewCoordinateRecord;
import org.hl7.tinkar.entity.graph.DiTreeEntity;
import org.hl7.tinkar.entity.graph.EntityVertex;
import org.hl7.tinkar.terms.EntityFacade;
import org.hl7.tinkar.terms.TinkarTerm;

public interface ViewCalculator extends StampCalculatorDelegate, LanguageCalculatorDelegate, NavigationCalculatorDelegate, LogicCalculatorDelegate {

    ViewCoordinateRecord viewCoordinateRecord();


    default boolean isDefined(EntityFacade facade) {
        return isDefined(facade.nid());
    }

    default boolean isDefined(int conceptNid) {
        Latest<DiTreeEntity<EntityVertex>> conceptExpression = getAxiomTreeForEntity(conceptNid, PremiseType.STATED);
        if (!conceptExpression.isPresent()) {
            conceptExpression = getAxiomTreeForEntity(conceptNid, PremiseType.INFERRED);
        }
        if (!conceptExpression.isPresent()) {
            return false;
        }
        return conceptExpression.get().containsVertexWithMeaning(TinkarTerm.SUFFICIENT_SET);
    }

}
