package dev.ikm.tinkar.coordinate.view.calculator;

import dev.ikm.tinkar.coordinate.view.ViewCoordinateRecord;
import dev.ikm.tinkar.coordinate.language.calculator.LanguageCalculatorDelegate;
import dev.ikm.tinkar.coordinate.logic.PremiseType;
import dev.ikm.tinkar.coordinate.logic.calculator.LogicCalculatorDelegate;
import dev.ikm.tinkar.coordinate.navigation.calculator.NavigationCalculatorDelegate;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculatorDelegate;
import dev.ikm.tinkar.entity.graph.DiTreeEntity;
import dev.ikm.tinkar.entity.graph.EntityVertex;
import dev.ikm.tinkar.terms.EntityFacade;
import dev.ikm.tinkar.terms.TinkarTerm;

public interface ViewCalculator extends StampCalculatorDelegate, LanguageCalculatorDelegate, NavigationCalculatorDelegate, LogicCalculatorDelegate {

    ViewCoordinateRecord viewCoordinateRecord();


    default boolean isDefined(EntityFacade facade) {
        return isDefined(facade.nid());
    }

    default boolean isDefined(int conceptNid) {
        Latest<DiTreeEntity> conceptExpression = getAxiomTreeForEntity(conceptNid, PremiseType.STATED);
        if (!conceptExpression.isPresent()) {
            conceptExpression = getAxiomTreeForEntity(conceptNid, PremiseType.INFERRED);
        }
        if (!conceptExpression.isPresent()) {
            return false;
        }
        return conceptExpression.get().containsVertexWithMeaning(TinkarTerm.SUFFICIENT_SET);
    }

}
