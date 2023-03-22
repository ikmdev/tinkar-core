package org.hl7.tinkar.coordinate.view.calculator;

import com.google.auto.service.AutoService;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.collection.ConcurrentReferenceHashMap;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.coordinate.language.LanguageCoordinateRecord;
import org.hl7.tinkar.coordinate.language.calculator.LanguageCalculator;
import org.hl7.tinkar.coordinate.language.calculator.LanguageCalculatorDelegate;
import org.hl7.tinkar.coordinate.language.calculator.LanguageCalculatorWithCache;
import org.hl7.tinkar.coordinate.logic.calculator.LogicCalculator;
import org.hl7.tinkar.coordinate.logic.calculator.LogicCalculatorDelegate;
import org.hl7.tinkar.coordinate.logic.calculator.LogicCalculatorWithCache;
import org.hl7.tinkar.coordinate.navigation.calculator.NavigationCalculator;
import org.hl7.tinkar.coordinate.navigation.calculator.NavigationCalculatorDelegate;
import org.hl7.tinkar.coordinate.navigation.calculator.NavigationCalculatorWithCache;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculator;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculatorDelegate;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import org.hl7.tinkar.coordinate.view.ViewCoordinateRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ViewCalculatorWithCache implements ViewCalculator, StampCalculatorDelegate,
        LanguageCalculatorDelegate, NavigationCalculatorDelegate, LogicCalculatorDelegate {
    /**
     * The Constant LOG.
     */
    private static final Logger LOG = LoggerFactory.getLogger(ViewCalculatorWithCache.class);

    private static final ConcurrentReferenceHashMap<ViewCoordinateRecord, ViewCalculatorWithCache> SINGLETONS =
            new ConcurrentReferenceHashMap<>(ConcurrentReferenceHashMap.ReferenceType.WEAK,
                    ConcurrentReferenceHashMap.ReferenceType.WEAK);
    private final StampCalculatorWithCache stampCalculator;
    private final LanguageCalculator languageCalculator;
    private final NavigationCalculator navigationCalculator;
    private final LogicCalculator logicCalculator;
    private final ViewCoordinateRecord viewCoordinateRecord;

    public ViewCalculatorWithCache(ViewCoordinateRecord viewCoordinateRecord) {
        this.stampCalculator = StampCalculatorWithCache.getCalculator(viewCoordinateRecord.stampCoordinate());
        this.languageCalculator = LanguageCalculatorWithCache.getCalculator(viewCoordinateRecord.stampCoordinate(),
                viewCoordinateRecord.languageCoordinateList());
        this.navigationCalculator = NavigationCalculatorWithCache.getCalculator(viewCoordinateRecord.stampCoordinate(),
                viewCoordinateRecord.languageCoordinateList(), viewCoordinateRecord.navigationCoordinate());
        this.logicCalculator = LogicCalculatorWithCache.getCalculator(viewCoordinateRecord.logicCoordinate(), viewCoordinateRecord.stampCoordinate());
        this.viewCoordinateRecord = viewCoordinateRecord;

    }

    public static ViewCalculatorWithCache getCalculator(ViewCoordinateRecord viewCoordinateRecord) {
        return SINGLETONS.computeIfAbsent(viewCoordinateRecord,
                filterKey -> new ViewCalculatorWithCache(viewCoordinateRecord));
    }

    @Override
    public ViewCoordinateRecord viewCoordinateRecord() {
        return viewCoordinateRecord;
    }

    @Override
    public LogicCalculator logicCalculator() {
        return this.logicCalculator;
    }

    @Override
    public NavigationCalculator navigationCalculator() {
        return navigationCalculator;
    }

    @Override
    public StampCalculator stampCalculator() {
        return stampCalculator;
    }

    @Override
    public ImmutableList<LanguageCoordinateRecord> languageCoordinateList() {
        return this.languageCalculator.languageCoordinateList();
    }

    @Override
    public LanguageCalculator languageCalculator() {
        return languageCalculator;
    }

    @AutoService(CachingService.class)
    public static class CacheProvider implements CachingService {
        @Override
        public void reset() {
            SINGLETONS.clear();
        }
    }


}
