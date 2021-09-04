package org.hl7.tinkar.coordinate.logic.calculator;

import com.google.auto.service.AutoService;
import org.hl7.tinkar.collection.ConcurrentReferenceHashMap;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.coordinate.logic.LogicCoordinate;
import org.hl7.tinkar.coordinate.logic.LogicCoordinateRecord;
import org.hl7.tinkar.coordinate.logic.PremiseType;
import org.hl7.tinkar.coordinate.stamp.StampCoordinate;
import org.hl7.tinkar.coordinate.stamp.StampCoordinateRecord;
import org.hl7.tinkar.coordinate.stamp.calculator.Latest;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculator;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.Field;
import org.hl7.tinkar.entity.SemanticEntityVersion;
import org.hl7.tinkar.entity.graph.DiTreeEntity;
import org.hl7.tinkar.entity.graph.EntityVertex;
import org.hl7.tinkar.terms.TinkarTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.OptionalInt;

public class LogicCalculatorWithCache implements LogicCalculator {
    private static final Logger LOG = LoggerFactory.getLogger(LogicCalculatorWithCache.class);
    private static final ConcurrentReferenceHashMap<LogicAndStampCoordinate, LogicCalculatorWithCache> SINGLETONS =
            new ConcurrentReferenceHashMap<>(ConcurrentReferenceHashMap.ReferenceType.WEAK,
                    ConcurrentReferenceHashMap.ReferenceType.WEAK);

    ;
    private final LogicCoordinateRecord logicCoordinateRecord;
    private final StampCoordinateRecord stampCoordinateRecord;
    private final StampCalculator stampCalculator;

    public LogicCalculatorWithCache(LogicCoordinate logicCoordinate, StampCoordinate stampCoordinate) {
        this.logicCoordinateRecord = logicCoordinate.toLogicCoordinateRecord();
        this.stampCoordinateRecord = stampCoordinate.toStampCoordinateRecord();
        this.stampCalculator = StampCalculatorWithCache.getCalculator(stampCoordinateRecord);
    }

    /**
     * Gets the stampCoordinateRecord.
     *
     * @return the stampCoordinateRecord
     */
    public static LogicCalculatorWithCache getCalculator(LogicCoordinate logicCoordinate, StampCoordinate stampCoordinate) {
        return SINGLETONS.computeIfAbsent(new LogicAndStampCoordinate(logicCoordinate.toLogicCoordinateRecord(),
                        stampCoordinate.toStampCoordinateRecord()),
                logicCoordinateRecord -> new LogicCalculatorWithCache(logicCoordinate, stampCoordinate));
    }

    record LogicAndStampCoordinate(LogicCoordinateRecord logicCoordinate, StampCoordinateRecord stampCoordinate) {
    }

    @AutoService(CachingService.class)
    public static class CacheProvider implements CachingService {
        @Override
        public void reset() {
            SINGLETONS.clear();
        }
    }

    @Override
    public LogicCoordinateRecord logicCoordinateRecord() {
        return this.logicCoordinateRecord;
    }


    @Override
    public boolean hasSufficientSet(int nid) {
        int axiomsPatternNid = logicCoordinateRecord.statedAxiomsPatternNid();

        int[] semanticNids = PrimitiveData.get().semanticNidsForComponentOfPattern(nid, axiomsPatternNid);
        switch (semanticNids.length) {
            case 0:
                // TODO Raise an alert... ?
                return false;
            case 1:
                Latest<SemanticEntityVersion> latestAxioms = stampCalculator.latest(semanticNids[0]);
                if (latestAxioms.isPresent()) {
                    SemanticEntityVersion axioms = latestAxioms.get();
                    OptionalInt optionalIndexForMeaning = stampCalculator.getIndexForMeaning(axiomsPatternNid, TinkarTerm.EL_PLUS_PLUS_STATED_TERMINOLOGICAL_AXIOMS.nid());
                    if (optionalIndexForMeaning.isPresent()) {
                        DiTreeEntity<? extends EntityVertex> axiomsField =
                                (DiTreeEntity<? extends EntityVertex>) axioms.fields().get(optionalIndexForMeaning.getAsInt());
                        for (EntityVertex vertex : axiomsField.vertexMap()) {
                            if (vertex.getMeaningNid() == TinkarTerm.SUFFICIENT_SET.nid()) {
                                return true;
                            }
                        }
                    }
                }
                // TODO Raise an alert... ?
                return false;
            default:
                // TODO Raise an alert...
                throw new IllegalStateException("More than one set of axioms for concept: " + Entity.getFast(nid));
        }
    }

    @Override
    public Latest<SemanticEntityVersion> getAxiomSemanticForEntity(int entityNid, StampCalculator stampCalculator, PremiseType premiseType) {
        int[] semanticNids = switch (premiseType) {
            case STATED -> {
                yield PrimitiveData.get().semanticNidsForComponentOfPattern(entityNid, logicCoordinateRecord().statedAxiomsPatternNid());
            }
            case INFERRED -> {
                yield PrimitiveData.get().semanticNidsForComponentOfPattern(entityNid, logicCoordinateRecord().inferredAxiomsPatternNid());
            }
            default -> {
                throw new IllegalStateException("Can't handle PremiseType: " + premiseType);
            }
        };
        if (semanticNids.length == 0) {
            return Latest.empty();
        }
        if (semanticNids.length > 1) {
            LOG.warn("More than one " + premiseType +
                    " logical expression for " + PrimitiveData.text(entityNid));
        }
        return stampCalculator.latest(semanticNids[0]);
    }

    @Override
    public Latest<DiTreeEntity<EntityVertex>> getAxiomTreeForEntity(int entityNid, StampCalculator stampCalculator, PremiseType premiseType) {
        int[] semanticNids = switch (premiseType) {
            case STATED -> {
                yield PrimitiveData.get().semanticNidsForComponentOfPattern(entityNid, logicCoordinateRecord().statedAxiomsPatternNid());
            }
            case INFERRED -> {
                yield PrimitiveData.get().semanticNidsForComponentOfPattern(entityNid, logicCoordinateRecord().inferredAxiomsPatternNid());
            }
            default -> {
                throw new IllegalStateException("Can't handle PremiseType: " + premiseType);
            }
        };
        if (semanticNids.length == 0) {
            return Latest.empty();
        }
        if (semanticNids.length > 1) {
            LOG.warn("More than one " + premiseType +
                    " logical expression for " + PrimitiveData.text(entityNid));
        }

        Latest<Field<DiTreeEntity<EntityVertex>>> latestAxiomField = switch (premiseType) {
            case INFERRED -> {
                yield stampCalculator.getFieldForSemanticWithMeaning(semanticNids[0], TinkarTerm.EL_PLUS_PLUS_INFERRED_TERMINOLOGICAL_AXIOMS);
            }
            case STATED -> {
                yield stampCalculator.getFieldForSemanticWithMeaning(semanticNids[0], TinkarTerm.EL_PLUS_PLUS_STATED_TERMINOLOGICAL_AXIOMS);
            }
        };
        if (latestAxiomField.isPresent()) {
            Latest<DiTreeEntity<EntityVertex>> latestAxioms = new Latest<>(latestAxiomField.get().value());
            if (latestAxiomField.isContradicted()) {
                for (Field<DiTreeEntity<EntityVertex>> contradictedField : latestAxiomField.contradictions()) {
                    latestAxioms.contradictions().add(contradictedField.value());
                }
            }
            return latestAxioms;
        }
        return Latest.empty();
    }


}
