package org.hl7.tinkar.coordinate.navigation.calculator;

import com.google.auto.service.AutoService;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.hl7.tinkar.collection.ConcurrentReferenceHashMap;
import org.hl7.tinkar.common.id.IntIdList;
import org.hl7.tinkar.common.id.IntIdSet;
import org.hl7.tinkar.common.id.IntIds;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.coordinate.CoordinateUtil;
import org.hl7.tinkar.coordinate.language.LanguageCoordinateRecord;
import org.hl7.tinkar.coordinate.language.calculator.LanguageCalculator;
import org.hl7.tinkar.coordinate.language.calculator.LanguageCalculatorWithCache;
import org.hl7.tinkar.coordinate.navigation.NavigationCoordinateRecord;
import org.hl7.tinkar.coordinate.stamp.StampCoordinateRecord;
import org.hl7.tinkar.coordinate.stamp.calculator.Latest;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculator;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import org.hl7.tinkar.coordinate.view.VertexSortNaturalOrder;
import org.hl7.tinkar.entity.PatternEntityVersion;
import org.hl7.tinkar.entity.SemanticEntityVersion;
import org.hl7.tinkar.terms.ConceptProxy;
import org.hl7.tinkar.terms.TinkarTerm;

import java.util.logging.Logger;

/**
 * TODO: Filter vertex concepts by status values.
 */
public class NavigationCalculatorWithCache implements NavigationCalculator {
    /** The Constant LOG. */
    private static final Logger LOG = CoordinateUtil.LOG;

    private static record StampLangNavRecord(StampCoordinateRecord stampFilter,
                                             ImmutableList<LanguageCoordinateRecord> languageCoordinateList,
                                             NavigationCoordinateRecord navigationCoordinate){}

    private static final ConcurrentReferenceHashMap<StampLangNavRecord, NavigationCalculatorWithCache> SINGLETONS =
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
    public static NavigationCalculatorWithCache getCalculator(StampCoordinateRecord stampFilter,
                                                              ImmutableList<LanguageCoordinateRecord> languageCoordinateList,
                                                              NavigationCoordinateRecord navigationCoordinate) {
        return SINGLETONS.computeIfAbsent(new StampLangNavRecord(stampFilter, languageCoordinateList, navigationCoordinate),
                filterKey -> new NavigationCalculatorWithCache(stampFilter,
                        languageCoordinateList, navigationCoordinate));
    }

    private final StampCalculatorWithCache stampCalculator;
    private final LanguageCalculatorWithCache  languageCalculator;
    private final NavigationCoordinateRecord navigationCoordinate;

    public NavigationCalculatorWithCache(StampCoordinateRecord stampFilter,
                                         ImmutableList<LanguageCoordinateRecord> languageCoordinateList,
                                         NavigationCoordinateRecord navigationCoordinate) {
        this.stampCalculator = StampCalculatorWithCache.getCalculator(stampFilter);
        this.languageCalculator = LanguageCalculatorWithCache.getCalculator(stampFilter, languageCoordinateList);
        this.navigationCoordinate = navigationCoordinate;
    }

    @Override
    public boolean sortVertices() {
        return navigationCoordinate.sortVertices();
    }

    @Override
    public StampCalculator stampCalculator() {
        return stampCalculator;
    }

    @Override
    public LanguageCalculator languageCalculator() {
        return languageCalculator;
    }

    @Override
    public IntIdList unsortedParents(int conceptNid) {
        return getIntIdListForMeaning(conceptNid, TinkarTerm.RELATIONSHIP_ORIGIN);
    }

    @Override
    public IntIdList sortedParents(int conceptNid) {
        return IntIds.list.of(VertexSortNaturalOrder.SINGLETON.sortVertexes(unsortedParents(conceptNid).toArray(), this));
    }

    @Override
    public IntIdList sortedChildren(int conceptNid) {
        return IntIds.list.of(VertexSortNaturalOrder.SINGLETON.sortVertexes(unsortedChildren(conceptNid).toArray(), this));
    }

    public IntIdList unsortedChildren(int conceptNid) {
        return getIntIdListForMeaning(conceptNid, TinkarTerm.RELATIONSHIP_DESTINATION);
    }

    private IntIdList getIntIdListForMeaning(int conceptNid, ConceptProxy fieldMeaning) {
        IntIdSet navigationPatternNids = navigationCoordinate.navigationPatternNids();
        MutableIntSet childrenNidSet = IntSets.mutable.empty();
        navigationPatternNids.forEach(navPatternNid -> {
            Latest<PatternEntityVersion> latestPatternEntityVersion = stampCalculator().latest(navPatternNid);
            latestPatternEntityVersion.ifPresentOrElse(
                    (patternEntityVersion) -> {
                        int indexForMeaning = patternEntityVersion.indexForMeaning(fieldMeaning);
                        int[] semantics = PrimitiveData.get().semanticNidsForComponentOfPattern(conceptNid, navPatternNid);
                        if (semantics.length > 1) {
                            throw new IllegalStateException("More than one navigation semantic for concept: " +
                                    PrimitiveData.text(conceptNid) + " in " + PrimitiveData.text(navPatternNid));
                        } else if (semantics.length == 0) {
                            // Nothing to add...
                        } else {
                            Latest<SemanticEntityVersion> latestNavigationSemantic = stampCalculator().latest(semantics[0]);
                            if (latestNavigationSemantic.isAbsent()) {
                                // Nothing to add...
                            } else {
                                SemanticEntityVersion navigationSemantic = latestNavigationSemantic.get();
                                IntIdSet intIdSet = (IntIdSet) navigationSemantic.fields().get(indexForMeaning);
                                childrenNidSet.addAll(intIdSet.toArray());
                            }
                        }
                    },
                    () -> {throw new IllegalStateException("No active pattern version. " + latestPatternEntityVersion);});
        });
        return IntIds.list.of(childrenNidSet.toArray());
    }

}
