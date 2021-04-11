package org.hl7.tinkar.coordinate.view;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.set.primitive.ImmutableIntSet;
import org.eclipse.collections.api.set.primitive.MutableIntSet;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.hl7.tinkar.common.id.IntIdList;
import org.hl7.tinkar.common.id.IntIdListFactory;
import org.hl7.tinkar.common.id.IntIdSet;
import org.hl7.tinkar.common.id.IntIdSetFactory;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.common.util.functional.TriConsumer;
import org.hl7.tinkar.coordinate.language.LanguageCoordinate;
import org.hl7.tinkar.coordinate.language.LanguageCoordinateImmutable;
import org.hl7.tinkar.coordinate.logic.LogicCoordinate;
import org.hl7.tinkar.coordinate.logic.LogicCoordinateImmutable;
import org.hl7.tinkar.coordinate.navigation.NavigationCoordinate;
import org.hl7.tinkar.coordinate.navigation.NavigationCoordinateImmutable;
import org.hl7.tinkar.coordinate.stamp.StampCalculator;
import org.hl7.tinkar.entity.EntityVersion;
import org.hl7.tinkar.entity.Field;
import org.hl7.tinkar.entity.PatternEntityVersion;
import org.hl7.tinkar.entity.SemanticEntityVersion;
import org.hl7.tinkar.entity.calculator.Latest;
import org.hl7.tinkar.terms.ConceptProxy;
import org.hl7.tinkar.terms.TinkarTerm;

import java.util.Optional;

public class ViewProvider implements View {

    final StampCalculator calculator;
    final LanguageCoordinateImmutable languageCoordinate;
    final LogicCoordinateImmutable logicCoordinate;
    final NavigationCoordinateImmutable navigationCoordinate;

    private ViewProvider(StampCalculator calculator,
                         LanguageCoordinate languageCoordinate,
                         LogicCoordinate logicCoordinate,
                         NavigationCoordinate navigationCoordinate) {
        this.calculator = calculator;
        this.languageCoordinate = languageCoordinate.toLanguageCoordinateImmutable();
        this.logicCoordinate = logicCoordinate.toLogicCoordinateImmutable();
        this.navigationCoordinate = navigationCoordinate.toNavigationCoordinateImmutable();
    }

    public static View make(StampCalculator calculator,
                            LanguageCoordinate languageCoordinate,
                            LogicCoordinate logicCoordinate,
                            NavigationCoordinate navigationCoordinate) {
        return new ViewProvider(calculator, languageCoordinate, logicCoordinate, navigationCoordinate);
    }

    @Override
    public StampCalculator stampCalculator() {
        return calculator;
    }

    @Override
    public LanguageCoordinateImmutable language() {
        return languageCoordinate;
    }

    @Override
    public LogicCoordinateImmutable logic() {
        return logicCoordinate;
    }

    @Override
    public NavigationCoordinateImmutable navigation() {
        return navigationCoordinate;
    }

    Cache<Integer, Latest<PatternEntityVersion>> patternVersionCache =
                Caffeine.newBuilder().maximumSize(128).build();

    private Latest<PatternEntityVersion> latestPatternEntityVersionFromCache(int patternNid) {
        return patternVersionCache.get(patternNid, nid -> stampCalculator().latest(patternNid));
    }

    public void forEachSemanticVersionWithFieldsForComponent(int componentNid,
                                                              TriConsumer<SemanticEntityVersion, ImmutableList<Field>, EntityVersion> procedure) {
        stampCalculator().forEachSemanticVersionForComponent(componentNid,
                (semanticEntityVersion, entityVersion) -> {
                    Latest<PatternEntityVersion> latestPatternEntityVersion = latestPatternEntityVersionFromCache(semanticEntityVersion.patternNid());
                    latestPatternEntityVersion.ifPresent(patternEntityVersion -> {
                        procedure.accept(semanticEntityVersion, semanticEntityVersion.fields(patternEntityVersion), entityVersion);
                    });
                });
    }

    public IntIdSet children(int conceptNid) {
        return getIntIdSetForMeaning(conceptNid, TinkarTerm.RELATIONSHIP_DESTINATION);
    }

    private IntIdSet getIntIdSetForMeaning(int conceptNid, ConceptProxy fieldMeaning) {
        ImmutableIntSet navigationPatternNids = navigation().getNavigationConceptNids();
        MutableIntSet childrenNidSet = IntSets.mutable.empty();
        navigationPatternNids.forEach(navPatternNid -> {
            Latest<PatternEntityVersion> latestPatternEntityVersion = latestPatternEntityVersionFromCache(navPatternNid);
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
        return IntIdSetFactory.INSTANCE.ofAlreadySorted(childrenNidSet.toSortedArray());
    }

    @Override
    public IntIdSet parents(int conceptNid) {
        return getIntIdSetForMeaning(conceptNid, TinkarTerm.RELATIONSHIP_ORIGIN);
    }

    @Override
    public IntIdList sortedParents(int conceptNid) {
        return IntIdListFactory.INSTANCE.of(VertexSortNaturalOrder.SINGLETON.sortVertexes(parents(conceptNid).toArray(), this));
    }

    @Override
    public IntIdList sortedChildren(int conceptNid) {
        return IntIdListFactory.INSTANCE.of(VertexSortNaturalOrder.SINGLETON.sortVertexes(children(conceptNid).toArray(), this));
    }
}
