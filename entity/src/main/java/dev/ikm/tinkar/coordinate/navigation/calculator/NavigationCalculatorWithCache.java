/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.coordinate.navigation.calculator;

import org.eclipse.collections.api.list.primitive.MutableLongList;

import dev.ikm.tinkar.terms.KernelTerm;
import dev.ikm.tinkar.common.service.internal.EntityStore;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.ikm.tinkar.common.id.LongIdCollection;
import dev.ikm.tinkar.common.id.LongIdList;
import dev.ikm.tinkar.common.id.LongIdSet;
import dev.ikm.tinkar.common.id.LongIds;
import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.common.service.DiagnosticText;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.coordinate.language.LanguageCoordinateRecord;
import dev.ikm.tinkar.coordinate.language.calculator.LanguageCalculator;
import dev.ikm.tinkar.coordinate.language.calculator.LanguageCalculatorWithCache;
import dev.ikm.tinkar.coordinate.navigation.NavigationCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StampCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StateSet;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import dev.ikm.tinkar.coordinate.view.VertexSortNaturalOrder;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.PatternEntityVersion;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.terms.EntityProxy;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.primitive.MutableLongList;
import org.eclipse.collections.api.map.primitive.MutableLongObjectMap;
import org.eclipse.collections.api.set.primitive.MutableLongSet;
import org.eclipse.collections.impl.factory.primitive.LongLists;
import org.eclipse.collections.impl.factory.primitive.LongObjectMaps;
import org.eclipse.collections.impl.factory.primitive.LongSets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.UUID;


/**
 * TODO: Filter vertex concepts by status values.
 * TODO: Sort based on patterns in addition to natural order
 *  TODO: add cache of descendents, ancestors, and similar.
 */
public class NavigationCalculatorWithCache implements NavigationCalculator {
    /**
     * The Constant LOG.
     */
    private static final Logger LOG = LoggerFactory.getLogger(NavigationCalculatorWithCache.class);
    private static final Cache<StampLangNavRecord, NavigationCalculatorWithCache> SINGLETONS = Caffeine.newBuilder().weakValues().build();
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
        return SINGLETONS.get(new StampLangNavRecord(stampFilter, languageCoordinateList, navigationCoordinate),
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

    private void addDescendents(long conceptNid, MutableLongSet nidSet) {
        if (!nidSet.contains(conceptNid)) {
            childrenOf(conceptNid).forEach(childNid -> {
                addDescendents(childNid, nidSet);
                nidSet.add(childNid);
            });
        }
    }

    private void addAncestors(long conceptNid, MutableLongSet nidSet) {
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
    public LongIdList sortedParentsOf(long conceptNid) {
        return LongIds.list.of(VertexSortNaturalOrder.SINGLETON.sortVertexes(unsortedParentsOf(conceptNid).toArray(), this));
    }

    @Override
    public LongIdList unsortedParentsOf(long conceptNid) {
        return getIntIdListForMeaning(conceptNid, KernelTerm.RELATIONSHIP_ORIGIN);
    }

    @Override
    public LongIdSet descendentsOf(long conceptNid) {
        MutableLongSet nidSet = LongSets.mutable.empty();
        addDescendents(conceptNid, nidSet);
        return LongIds.set.of(nidSet.toArray());
    }

    @Override
    public LongIdSet ancestorsOf(long conceptNid) {
        MutableLongSet nidSet = LongSets.mutable.empty();
        addAncestors(conceptNid, nidSet);
        return LongIds.set.of(nidSet.toArray());
    }

    @Override
    public LongIdSet kindOf(long conceptNid) {
        MutableLongSet kindOfSet = LongSets.mutable.of(conceptNid);
        kindOfSet.addAll(descendentsOf(conceptNid).toArray());
        return LongIds.set.of(kindOfSet.toArray());
    }

    @Override
    public ImmutableList<Edge> sortedChildEdges(long conceptNid) {
        return VertexSortNaturalOrder.SINGLETON.sortEdges(unsortedChildEdges(conceptNid), this);
    }

    @Override
    public ImmutableList<Edge> unsortedChildEdges(long conceptNid) {
        return getEdges(conceptNid, KernelTerm.RELATIONSHIP_DESTINATION);
    }

    @Override
    public ImmutableList<Edge> sortedParentEdges(long conceptNid) {
        return VertexSortNaturalOrder.SINGLETON.sortEdges(unsortedParentEdges(conceptNid), this);
    }

    @Override
    public ImmutableList<Edge> unsortedParentEdges(long conceptNid) {
        return getEdges(conceptNid, KernelTerm.RELATIONSHIP_ORIGIN);
    }

    @Override
    public LongIdList sortedChildrenOf(long conceptNid) {
        return LongIds.list.of(VertexSortNaturalOrder.SINGLETON.sortVertexes(unsortedChildrenOf(conceptNid).toArray(), this));
    }

    @Override
    public LongIdList unsortedChildrenOf(long conceptNid) {
        return getIntIdListForMeaning(conceptNid, KernelTerm.RELATIONSHIP_DESTINATION);
    }
    @Override
    public LongIdList unsortedUnversionedChildrenOf(long conceptNid) {
        return getIntIdListForMeaningUnversioned(conceptNid, KernelTerm.RELATIONSHIP_DESTINATION);
    }
    @Override
    public LongIdList unsortedUnversionedParentsOf(long conceptNid) {
        return getIntIdListForMeaningUnversioned(conceptNid, KernelTerm.RELATIONSHIP_ORIGIN);
    }

    @Override
    public LongIdList toSortedList(LongIdList inputList) {
        // TODO add pattern sort...
        return LongIds.list.of(VertexSortNaturalOrder.SINGLETON.sortVertexes(inputList.toArray(), this));
    }

    @Override
    public NavigationCoordinateRecord navigationCoordinate() {
        return this.navigationCoordinate;
    }

    @Override
    public LongIdList unsortedParentsOf(long conceptNid, long patternNid) {
        return getIntIdListForMeaningFromPattern(conceptNid, KernelTerm.RELATIONSHIP_ORIGIN, patternNid);
    }

    private ImmutableList<Edge> getEdges(long conceptNid, EntityProxy.Concept relationshipDirection) {
        MutableLongObjectMap<MutableEdge> edges = LongObjectMaps.mutable.empty();
        for (long patternNid : navigationCoordinate.navigationPatternNids().toArray()) {
            stampCalculator.latestPatternEntityVersion(patternNid).ifPresent(patternEntityVersion -> {
                long typeNid = patternEntityVersion.semanticMeaningNid();
                LongIdList parents = getIntIdListForMeaningFromPattern(conceptNid, relationshipDirection, patternNid);
                for (long parentNid : parents.toArray()) {
                    edges.updateValue(parentNid, () -> new MutableEdge(LongSets.mutable.empty(), parentNid, this.languageCalculator), mutableEdge -> {
                        mutableEdge.types.add(typeNid);
                        return mutableEdge;
                    });
                }
            });
        }
        return Lists.immutable.ofAll(edges.stream().map(mutableEdge -> mutableEdge.toEdge()).toList());
    }

    private LongIdList getIntIdListForMeaningFromPattern(long referencedComponentNid, EntityProxy.Concept fieldMeaning, long patternNid) {
        MutableLongSet nidsInList = LongSets.mutable.empty();
        intIdListForMeaningFromPattern(referencedComponentNid, fieldMeaning, patternNid, nidsInList, navigationCoordinate.vertexStates(), true);
        return LongIds.list.of(nidsInList.toArray());
    }

    private LongIdList getIntIdListForMeaning(long referencedComponentNid, EntityProxy.Concept fieldMeaning) {
        LongIdSet navigationPatternNids = navigationCoordinate.navigationPatternNids();
        MutableLongSet nidsInList = LongSets.mutable.empty();
        navigationPatternNids.forEach(navPatternNid -> {
            intIdListForMeaningFromPattern(referencedComponentNid, fieldMeaning, navPatternNid, nidsInList, navigationCoordinate.vertexStates(), true);
        });
        return LongIds.list.of(nidsInList.toArray());
    }

    private LongIdList getIntIdListForMeaningUnversioned(long referencedComponentNid, EntityProxy.Concept fieldMeaning) {
        LongIdSet navigationPatternNids = navigationCoordinate.navigationPatternNids();
        MutableLongSet nidsInList = LongSets.mutable.empty();
        navigationPatternNids.forEach(navPatternNid -> {
            intIdListForMeaningFromPattern(referencedComponentNid, fieldMeaning, navPatternNid, nidsInList, navigationCoordinate.vertexStates(), false);
        });
        return LongIds.list.of(nidsInList.toArray());
    }

    private void intIdListForMeaningFromPattern(long referencedComponentNid, EntityProxy.Concept fieldMeaning,
                                                long patternNid, MutableLongSet nidsInList, StateSet states, boolean versioned) {
        Latest<PatternEntityVersion> latestPatternEntityVersion = stampCalculator().latest(patternNid);
        latestPatternEntityVersion.ifPresentOrElse(
                (patternEntityVersion) -> {
                    int indexForMeaning = patternEntityVersion.indexForMeaning(fieldMeaning);
                    long[] semantics = EntityStore.current().semanticNidsForComponentOfPattern(referencedComponentNid, patternNid);
                    if (semantics.length > 1) {
                        LOG.warn("More than one navigation semantic for concept: " +
                                PrimitiveData.text(referencedComponentNid) + " in " + PrimitiveData.text(patternNid) +
                                ". Using semantic with correct single semantic UUID.");

                        // Generate the correct single semantic UUID
                        UUID expectedUuid = dev.ikm.tinkar.common.util.uuid.UuidT5Generator.singleSemanticUuid(
                                EntityHandle.get(patternNid).expectPattern(),
                                EntityHandle.get(referencedComponentNid).expectEntity()
                        );

                        // Find the semantic with the matching UUID
                        long correctSemanticNid = -1;
                        MutableLongList incorrectSemanticNids = LongLists.mutable.empty();
                        for (long semanticNid : semantics) {
                            if (EntityHandle.get(semanticNid).expectSemantic().publicId().contains(expectedUuid)) {
                                correctSemanticNid = semanticNid;
                            } else {
                                incorrectSemanticNids.add(semanticNid);
                            }
                        }

                        incorrectSemanticNids.forEach(incorrectSemanticNid -> {
                            LOG.warn("Ignoring incorrect (wrong semantic single uuid generation) semantic: " + EntityHandle.get(incorrectSemanticNid));
                        });

                        if (correctSemanticNid != -1) {
                            Latest<SemanticEntityVersion> latestNavigationSemantic = stampCalculator().latest(correctSemanticNid);
                            latestNavigationSemantic.ifPresent(semanticEntityVersion -> {
                                LOG.warn("Processing correct semantic: " + semanticEntityVersion);
                                SemanticEntityVersion navigationSemantic = latestNavigationSemantic.get();
                                LongIdCollection intIdSet = (LongIdCollection) navigationSemantic.fieldValues().get(indexForMeaning);
                                // Filter here by allowed vertex state...
                                if (versioned && states != StateSet.ACTIVE_INACTIVE_AND_WITHDRAWN) {
                                    intIdSet.forEach(nid ->
                                            vertexStampCalculator.latest(nid).ifPresent(entityVersion -> nidsInList.add(entityVersion.nid())));
                                } else {
                                    nidsInList.addAll(intIdSet.toArray());
                                }
                            });
                        } else {
                            LOG.error("Could not find semantic with expected UUID: " + expectedUuid +
                                    " for concept: " + PrimitiveData.text(referencedComponentNid));
                        }
                    } else if (semantics.length == 0) {
                        // Nothing to add...
                    } else {
                        Latest<SemanticEntityVersion> latestNavigationSemantic = stampCalculator().latest(semantics[0]);
                        latestNavigationSemantic.ifPresent(semanticEntityVersion -> {
                            SemanticEntityVersion navigationSemantic = latestNavigationSemantic.get();
                            LongIdCollection intIdSet = (LongIdCollection) navigationSemantic.fieldValues().get(indexForMeaning);
                            // Filter here by allowed vertex state...
                            if (versioned && states != StateSet.ACTIVE_INACTIVE_AND_WITHDRAWN) {
                                intIdSet.forEach(nid ->
                                        vertexStampCalculator.latest(nid).ifPresent(entityVersion -> nidsInList.add(entityVersion.nid())));
                            } else {
                                nidsInList.addAll(intIdSet.toArray());
                            }
                        });
                    }
                },
                () -> {
                    throw new IllegalStateException("No active pattern version. " + DiagnosticText.component(patternNid));
                });
    }

    @Override
    public StampCalculator stampCalculator() {
        return stampCalculator;
    }

    public static class CacheProvider implements CachingService {
        // TODO: this has implicit assumption that no one will hold on to a calculator... Should we be defensive?
        @Override
        public void reset() {
            SINGLETONS.invalidateAll();
        }
    }

    private record StampLangNavRecord(StampCoordinateRecord stampFilter,
                                      ImmutableList<LanguageCoordinateRecord> languageCoordinateList,
                                      NavigationCoordinateRecord navigationCoordinate) {
    }

    record MutableEdge(MutableLongSet types, long destinationNid, LanguageCalculator languageCalculator) {
        EdgeRecord toEdge() {
            return new EdgeRecord(LongIds.set.of(types.toArray()), destinationNid, languageCalculator);
        }
    }

}
