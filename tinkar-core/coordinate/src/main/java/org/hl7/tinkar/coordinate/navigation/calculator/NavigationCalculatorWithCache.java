package org.hl7.tinkar.coordinate.navigation.calculator;

import com.google.auto.service.AutoService;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.map.primitive.MutableIntObjectMap;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntObjectMaps;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.hl7.tinkar.collection.ConcurrentReferenceHashMap;
import org.hl7.tinkar.common.id.IntIdList;
import org.hl7.tinkar.common.id.IntIdSet;
import org.hl7.tinkar.common.id.IntIds;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.coordinate.language.LanguageCoordinateRecord;
import org.hl7.tinkar.coordinate.language.calculator.LanguageCalculator;
import org.hl7.tinkar.coordinate.language.calculator.LanguageCalculatorWithCache;
import org.hl7.tinkar.coordinate.navigation.NavigationCoordinateRecord;
import org.hl7.tinkar.coordinate.stamp.StampCoordinateRecord;
import org.hl7.tinkar.coordinate.stamp.StateSet;
import org.hl7.tinkar.coordinate.stamp.calculator.Latest;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculator;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import org.hl7.tinkar.coordinate.view.VertexSortNaturalOrder;
import org.hl7.tinkar.entity.PatternEntityVersion;
import org.hl7.tinkar.entity.SemanticEntityVersion;
import org.hl7.tinkar.terms.EntityProxy;
import org.hl7.tinkar.terms.TinkarTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/**
 * TODO: Filter vertex concepts by status values.
 * TODO: Sort based on patterns in addition to natural order
 */
public class NavigationCalculatorWithCache implements NavigationCalculator {
    /**
     * The Constant LOG.
     */
    private static final Logger LOG = LoggerFactory.getLogger(NavigationCalculatorWithCache.class);
    private static final ConcurrentReferenceHashMap<StampLangNavRecord, NavigationCalculatorWithCache> SINGLETONS =
            new ConcurrentReferenceHashMap<>(ConcurrentReferenceHashMap.ReferenceType.WEAK,
                    ConcurrentReferenceHashMap.ReferenceType.WEAK);
    private final StampCalculatorWithCache stampCalculator;
    private final StampCalculatorWithCache vertexStampCalculator;
    private final LanguageCalculatorWithCache languageCalculator;
    private final NavigationCoordinateRecord navigationCoordinate;

    public NavigationCalculatorWithCache(StampCoordinateRecord stampFilter,
                                         ImmutableList<LanguageCoordinateRecord> languageCoordinateList,
                                         NavigationCoordinateRecord navigationCoordinate) {
        this.stampCalculator = StampCalculatorWithCache.getCalculator(stampFilter);
        this.languageCalculator = LanguageCalculatorWithCache.getCalculator(stampFilter, languageCoordinateList);
        this.navigationCoordinate = navigationCoordinate;
        this.vertexStampCalculator = StampCalculatorWithCache.getCalculator(stampFilter.withAllowedStates(navigationCoordinate.vertexStates()));
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

    @Override
    public ImmutableList<LanguageCoordinateRecord> languageCoordinateList() {
        return languageCalculator.languageCoordinateList();
    }

    @Override
    public LanguageCalculator languageCalculator() {
        return languageCalculator;
    }

    private void addDescendents(int conceptNid, MutableIntSet nidSet) {
        if (!nidSet.contains(conceptNid)) {
            childrenOf(conceptNid).forEach(childNid -> {
                addDescendents(childNid, nidSet);
                nidSet.add(childNid);
            });
        }
    }

    private void addAncestors(int conceptNid, MutableIntSet nidSet) {
        if (!nidSet.contains(conceptNid)) {
            parentsOf(conceptNid).forEach(parentNid -> {
                addAncestors(parentNid, nidSet);
                nidSet.add(parentNid);
            });
        }
    }

    @Override
    public StampCalculatorWithCache vertexStampCalculator() {
        return this.vertexStampCalculator;
    }

    @Override
    public StateSet allowedVertexStates() {
        return vertexStampCalculator.allowedStates();
    }

    @Override
    public boolean sortVertices() {
        return navigationCoordinate.sortVertices();
    }

    @Override
    public IntIdList sortedParentsOf(int conceptNid) {
        return IntIds.list.of(VertexSortNaturalOrder.SINGLETON.sortVertexes(unsortedParentsOf(conceptNid).toArray(), this));
    }

    @Override
    public IntIdList unsortedParentsOf(int conceptNid) {
        return getIntIdListForMeaning(conceptNid, TinkarTerm.RELATIONSHIP_ORIGIN);
    }

    @Override
    public IntIdSet descendentsOf(int conceptNid) {
        MutableIntSet nidSet = IntSets.mutable.empty();
        addDescendents(conceptNid, nidSet);
        return IntIds.set.of(nidSet.toArray());
    }

    @Override
    public IntIdSet ancestorsOf(int conceptNid) {
        MutableIntSet nidSet = IntSets.mutable.empty();
        addAncestors(conceptNid, nidSet);
        return IntIds.set.of(nidSet.toArray());
    }

    @Override
    public IntIdSet kindOf(int conceptNid) {
        MutableIntSet kindOfSet = IntSets.mutable.of(conceptNid);
        kindOfSet.addAll(descendentsOf(conceptNid).toArray());
        return IntIds.set.of(kindOfSet.toArray());
    }

    @Override
    public ImmutableList<Edge> sortedChildEdges(int conceptNid) {
        return VertexSortNaturalOrder.SINGLETON.sortEdges(unsortedChildEdges(conceptNid), this);
    }

    @Override
    public ImmutableList<Edge> unsortedChildEdges(int conceptNid) {
        return getEdges(conceptNid, TinkarTerm.RELATIONSHIP_DESTINATION);
    }

    @Override
    public ImmutableList<Edge> sortedParentEdges(int conceptNid) {
        return VertexSortNaturalOrder.SINGLETON.sortEdges(unsortedParentEdges(conceptNid), this);
    }

    @Override
    public ImmutableList<Edge> unsortedParentEdges(int conceptNid) {
        return getEdges(conceptNid, TinkarTerm.RELATIONSHIP_ORIGIN);
    }

    @Override
    public IntIdList sortedChildrenOf(int conceptNid) {
        return IntIds.list.of(VertexSortNaturalOrder.SINGLETON.sortVertexes(unsortedChildrenOf(conceptNid).toArray(), this));
    }

    public IntIdList unsortedChildrenOf(int conceptNid) {
        return getIntIdListForMeaning(conceptNid, TinkarTerm.RELATIONSHIP_DESTINATION);
    }

    @Override
    public IntIdList toSortedList(IntIdList inputList) {
        // TODO add pattern sort...
        return IntIds.list.of(VertexSortNaturalOrder.SINGLETON.sortVertexes(inputList.toArray(), this));
    }

    @Override
    public NavigationCoordinateRecord navigationCoordinate() {
        return this.navigationCoordinate;
    }

    @Override
    public IntIdList unsortedParentsOf(int conceptNid, int patternNid) {
        return getIntIdListForMeaningFromPattern(conceptNid, TinkarTerm.RELATIONSHIP_ORIGIN, patternNid);
    }

    private ImmutableList<Edge> getEdges(int conceptNid, EntityProxy.Concept relationshipDirection) {
        MutableIntObjectMap<MutableEdge> edges = IntObjectMaps.mutable.empty();
        for (int patternNid : navigationCoordinate.navigationPatternNids().toArray()) {
            stampCalculator.latestPatternEntityVersion(patternNid).ifPresent(patternEntityVersion -> {
                int typeNid = patternEntityVersion.semanticMeaningNid();
                IntIdList parents = getIntIdListForMeaningFromPattern(conceptNid, relationshipDirection, patternNid);
                for (int parentNid : parents.toArray()) {
                    edges.updateValue(parentNid, () -> new MutableEdge(IntSets.mutable.empty(), parentNid), mutableEdge -> {
                        mutableEdge.types.add(typeNid);
                        return mutableEdge;
                    });
                }
            });
        }
        return Lists.immutable.ofAll(edges.stream().map(mutableEdge -> mutableEdge.toEdge()).toList());
    }

    private IntIdList getIntIdListForMeaningFromPattern(int referencedComponentNid, EntityProxy.Concept fieldMeaning, int patternNid) {
        MutableIntSet nidsInList = IntSets.mutable.empty();
        IntIdListForMeaningFromPattern(referencedComponentNid, fieldMeaning, patternNid, nidsInList, navigationCoordinate.vertexStates());
        return IntIds.list.of(nidsInList.toArray());
    }

    private IntIdList getIntIdListForMeaning(int referencedComponentNid, EntityProxy.Concept fieldMeaning) {
        IntIdSet navigationPatternNids = navigationCoordinate.navigationPatternNids();
        MutableIntSet nidsInList = IntSets.mutable.empty();
        navigationPatternNids.forEach(navPatternNid -> {
            IntIdListForMeaningFromPattern(referencedComponentNid, fieldMeaning, navPatternNid, nidsInList, navigationCoordinate.vertexStates());
        });
        return IntIds.list.of(nidsInList.toArray());
    }

    private void IntIdListForMeaningFromPattern(int referencedComponentNid, EntityProxy.Concept fieldMeaning, int patternNid, MutableIntSet nidsInList, StateSet states) {
        Latest<PatternEntityVersion> latestPatternEntityVersion = stampCalculator().latest(patternNid);
        latestPatternEntityVersion.ifPresentOrElse(
                (patternEntityVersion) -> {
                    int indexForMeaning = patternEntityVersion.indexForMeaning(fieldMeaning);
                    int[] semantics = PrimitiveData.get().semanticNidsForComponentOfPattern(referencedComponentNid, patternNid);
                    if (semantics.length > 1) {
                        throw new IllegalStateException("More than one navigation semantic for concept: " +
                                PrimitiveData.text(referencedComponentNid) + " in " + PrimitiveData.text(patternNid));
                    } else if (semantics.length == 0) {
                        // Nothing to add...
                    } else {
                        Latest<SemanticEntityVersion> latestNavigationSemantic = stampCalculator().latest(semantics[0]);
                        latestNavigationSemantic.ifPresent(semanticEntityVersion -> {
                            SemanticEntityVersion navigationSemantic = latestNavigationSemantic.get();
                            IntIdSet intIdSet = (IntIdSet) navigationSemantic.fields().get(indexForMeaning);
                            // Filter here by allowed vertex state...
                            if (states == StateSet.ACTIVE_INACTIVE_AND_WITHDRAWN) {
                                nidsInList.addAll(intIdSet.toArray());
                            } else {
                                intIdSet.forEach(nid ->
                                        vertexStampCalculator.latest(nid).ifPresent(entityVersion -> nidsInList.add(entityVersion.nid()))
                                );
                            }
                        });
                    }
                },
                () -> {
                    throw new IllegalStateException("No active pattern version. " + latestPatternEntityVersion);
                });
    }

    @Override
    public StampCalculator stampCalculator() {
        return stampCalculator;
    }

    private static record StampLangNavRecord(StampCoordinateRecord stampFilter,
                                             ImmutableList<LanguageCoordinateRecord> languageCoordinateList,
                                             NavigationCoordinateRecord navigationCoordinate) {
    }

    @AutoService(CachingService.class)
    public static class CacheProvider implements CachingService {
        @Override
        public void reset() {
            SINGLETONS.clear();
        }
    }

    record MutableEdge(MutableIntSet types, int destinationNid) {
        EdgeRecord toEdge() {
            return new EdgeRecord(IntIds.set.of(types.toArray()), destinationNid);
        }
    }

}
