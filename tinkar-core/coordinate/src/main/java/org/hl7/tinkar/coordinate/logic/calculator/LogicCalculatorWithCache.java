package org.hl7.tinkar.coordinate.logic.calculator;

import com.google.auto.service.AutoService;
import org.hl7.tinkar.collection.ConcurrentReferenceHashMap;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.coordinate.logic.LogicCoordinate;
import org.hl7.tinkar.coordinate.logic.LogicCoordinateRecord;
import org.hl7.tinkar.coordinate.stamp.StampCoordinate;
import org.hl7.tinkar.coordinate.stamp.StampCoordinateRecord;
import org.hl7.tinkar.coordinate.stamp.calculator.Latest;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculator;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import org.hl7.tinkar.entity.SemanticEntityVersion;
import org.hl7.tinkar.entity.graph.DiTreeEntity;
import org.hl7.tinkar.entity.graph.EntityVertex;
import org.hl7.tinkar.terms.TinkarTerm;

import java.util.OptionalInt;

public class LogicCalculatorWithCache implements LogicCalculator {
    record LogicAndStampCoordinate(LogicCoordinateRecord logicCoordinate, StampCoordinateRecord stampCoordinate) {};

    private static final ConcurrentReferenceHashMap<LogicAndStampCoordinate, LogicCalculatorWithCache> SINGLETONS =
            new ConcurrentReferenceHashMap<>(ConcurrentReferenceHashMap.ReferenceType.WEAK,
                    ConcurrentReferenceHashMap.ReferenceType.WEAK);

    @AutoService(CachingService.class)
    public static class CacheProvider implements CachingService {
        @Override
        public void reset() {
            SINGLETONS.clear();
        }
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

    private final LogicCoordinateRecord logicCoordinateRecord;
    private final StampCoordinateRecord stampCoordinateRecord;
    private final StampCalculator stampCalculator;

    public LogicCalculatorWithCache(LogicCoordinate logicCoordinate, StampCoordinate stampCoordinate) {
        this.logicCoordinateRecord = logicCoordinate.toLogicCoordinateRecord();
        this.stampCoordinateRecord = stampCoordinate.toStampCoordinateRecord();
        this.stampCalculator = StampCalculatorWithCache.getCalculator(stampCoordinateRecord);
    }

    @Override
    public boolean hasSufficientSet(int nid) {
        int axiomsPatternNid = logicCoordinateRecord.statedAxiomsPatternNid();

        int [] semanticNids = PrimitiveData.get().semanticNidsForComponentOfPattern(nid, axiomsPatternNid);
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
                        for (EntityVertex vertex: axiomsField.vertexMap()) {
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
                throw new IllegalStateException("More than one set of axioms for concept. ");
        }
    }

    @Override
    public LogicCoordinateRecord logicCoordinateRecord() {
        return this.logicCoordinateRecord;
    }
}
