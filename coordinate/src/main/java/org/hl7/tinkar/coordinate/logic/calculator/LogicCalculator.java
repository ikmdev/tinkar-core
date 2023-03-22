package org.hl7.tinkar.coordinate.logic.calculator;

import org.hl7.tinkar.coordinate.logic.LogicCoordinateRecord;
import org.hl7.tinkar.coordinate.logic.PremiseType;
import org.hl7.tinkar.coordinate.stamp.calculator.Latest;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculator;
import org.hl7.tinkar.entity.SemanticEntityVersion;
import org.hl7.tinkar.entity.graph.DiTreeEntity;
import org.hl7.tinkar.entity.graph.EntityVertex;
import org.hl7.tinkar.terms.EntityFacade;

public interface LogicCalculator {

    LogicCoordinateRecord logicCoordinateRecord();

    default boolean hasSufficientSet(EntityFacade entityFacade) {
        return hasSufficientSet(entityFacade.nid());
    }

    boolean hasSufficientSet(int nid);

    default Latest<DiTreeEntity> getStatedLogicalExpressionForEntity(EntityFacade entity, StampCalculator stampCalculator) {
        return getAxiomTreeForEntity(entity.nid(), stampCalculator, PremiseType.STATED);
    }

    Latest<DiTreeEntity> getAxiomTreeForEntity(int entityNid, StampCalculator stampCalculator, PremiseType premiseType);

    Latest<SemanticEntityVersion> getAxiomSemanticForEntity(int entityNid, StampCalculator stampCalculator, PremiseType premiseType);

    default Latest<DiTreeEntity> getAxiomTreeForEntity(EntityFacade entity, StampCalculator stampCalculator, PremiseType premiseType) {
        return getAxiomTreeForEntity(entity.nid(), stampCalculator, premiseType);
    }

    default Latest<DiTreeEntity> getStatedLogicalExpressionForEntity(int entityNid, StampCalculator stampCalculator) {
        return getAxiomTreeForEntity(entityNid, stampCalculator, PremiseType.STATED);
    }

    default Latest<DiTreeEntity> getInferredLogicalExpressionForEntity(EntityFacade entity, StampCalculator stampCalculator) {
        return getAxiomTreeForEntity(entity.nid(), stampCalculator, PremiseType.INFERRED);
    }

    default Latest<DiTreeEntity> getInferredLogicalExpressionForEntity(int entityNid, StampCalculator stampCalculator) {
        return getAxiomTreeForEntity(entityNid, stampCalculator, PremiseType.INFERRED);
    }

}
