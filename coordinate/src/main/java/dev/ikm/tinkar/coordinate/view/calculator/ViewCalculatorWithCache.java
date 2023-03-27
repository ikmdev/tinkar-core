package dev.ikm.tinkar.coordinate.view.calculator;

import com.google.auto.service.AutoService;
import dev.ikm.tinkar.coordinate.view.ViewCoordinateRecord;
import org.eclipse.collections.api.list.ImmutableList;
import dev.ikm.tinkar.collection.ConcurrentReferenceHashMap;
import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.coordinate.language.LanguageCoordinateRecord;
import dev.ikm.tinkar.coordinate.language.calculator.LanguageCalculator;
import dev.ikm.tinkar.coordinate.language.calculator.LanguageCalculatorDelegate;
import dev.ikm.tinkar.coordinate.language.calculator.LanguageCalculatorWithCache;
import dev.ikm.tinkar.coordinate.logic.calculator.LogicCalculator;
import dev.ikm.tinkar.coordinate.logic.calculator.LogicCalculatorDelegate;
import dev.ikm.tinkar.coordinate.logic.calculator.LogicCalculatorWithCache;
import dev.ikm.tinkar.coordinate.navigation.calculator.NavigationCalculator;
import dev.ikm.tinkar.coordinate.navigation.calculator.NavigationCalculatorDelegate;
import dev.ikm.tinkar.coordinate.navigation.calculator.NavigationCalculatorWithCache;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculatorDelegate;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
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
