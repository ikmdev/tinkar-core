package org.hl7.tinkar.coordinate.view.calculator;

import com.google.auto.service.AutoService;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.collection.ConcurrentReferenceHashMap;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.coordinate.CoordinateUtil;
import org.hl7.tinkar.coordinate.language.*;
import org.hl7.tinkar.coordinate.language.calculator.LanguageCalculator;
import org.hl7.tinkar.coordinate.language.calculator.LanguageCalculatorDelegate;
import org.hl7.tinkar.coordinate.language.calculator.LanguageCalculatorWithCache;
import org.hl7.tinkar.coordinate.navigation.NavigationCoordinate;
import org.hl7.tinkar.coordinate.navigation.NavigationCoordinateRecord;
import org.hl7.tinkar.coordinate.navigation.calculator.NavigationCalculator;
import org.hl7.tinkar.coordinate.navigation.calculator.NavigationCalculatorDelegate;
import org.hl7.tinkar.coordinate.navigation.calculator.NavigationCalculatorWithCache;
import org.hl7.tinkar.coordinate.stamp.*;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculator;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculatorDelegate;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import org.hl7.tinkar.coordinate.view.ViewCoordinateRecord;

import java.util.logging.Logger;

public class ViewCalculatorWithCache implements ViewCalculator, StampCalculatorDelegate,
        LanguageCalculatorDelegate, NavigationCalculatorDelegate {
    /** The Constant LOG. */
    private static final Logger LOG = CoordinateUtil.LOG;

    private static record StampLangNavViewRecord(StampCoordinateRecord stampFilter,
                                                 ImmutableList<LanguageCoordinateRecord> languageCoordinateList,
                                                 NavigationCoordinateRecord navigationCoordinate,
                                                 ViewCoordinateRecord viewCoordinateRecord){}

    private static final ConcurrentReferenceHashMap<StampLangNavViewRecord, ViewCalculatorWithCache> SINGLETONS =
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
    public static ViewCalculatorWithCache getCalculator(StampCoordinateRecord stampFilter,
                                                        ImmutableList<LanguageCoordinateRecord> languageCoordinateList,
                                                        NavigationCoordinateRecord navigationCoordinate,
                                                        ViewCoordinateRecord viewCoordinateRecord) {
        return SINGLETONS.computeIfAbsent(new StampLangNavViewRecord(stampFilter, languageCoordinateList,
                        navigationCoordinate, viewCoordinateRecord),
                filterKey -> new ViewCalculatorWithCache(stampFilter,
                        languageCoordinateList, navigationCoordinate, viewCoordinateRecord));
    }

    private final StampCalculatorWithCache stampCalculator;
    private final LanguageCalculator languageCalculator;
    private final NavigationCalculator navigationCalculator;
    private final ViewCoordinateRecord viewCoordinateRecord;

    public ViewCalculatorWithCache(StampCoordinate stampCoordinate,
                                   ImmutableList<LanguageCoordinateRecord> languageCoordinateList,
                                   NavigationCoordinate navigationCoordinate,
                                   ViewCoordinateRecord viewCoordinateRecord) {
        this.stampCalculator = StampCalculatorWithCache.getCalculator(stampCoordinate.toStampCoordinateRecord());
        this.languageCalculator = LanguageCalculatorWithCache.getCalculator(stampCoordinate.toStampCoordinateRecord(),
                languageCoordinateList);
        this.navigationCalculator = NavigationCalculatorWithCache.getCalculator(stampCoordinate.toStampCoordinateRecord(),
                languageCoordinateList, navigationCoordinate.toNavigationCoordinateRecord());
        this.viewCoordinateRecord = viewCoordinateRecord;
    }

    @Override
    public LanguageCalculator languageCalculator() {
        return languageCalculator;
    }

    @Override
    public StampCalculator stampCalculator() {
        return stampCalculator;
    }

    @Override
    public NavigationCalculator navigationCalculator() {
        return navigationCalculator;
    }


}
