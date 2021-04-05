package org.hl7.tinkar.coordinate;

import java.util.List;
import java.util.logging.Logger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.auto.service.AutoService;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.eclipse.collections.impl.factory.primitive.IntSets;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.coordinate.edit.Activity;
import org.hl7.tinkar.coordinate.edit.EditCoordinateImmutable;
import org.hl7.tinkar.coordinate.language.LanguageCoordinate;
import org.hl7.tinkar.coordinate.language.LanguageCoordinateImmutable;
import org.hl7.tinkar.coordinate.logic.LogicCoordinateImmutable;
import org.hl7.tinkar.coordinate.manifold.ManifoldCoordinateImmutable;
import org.hl7.tinkar.coordinate.stamp.*;
import org.hl7.tinkar.terms.TinkarTerm;
//Even though this class is static, needs to be a service, so that the reset() gets fired at appropriate times.
@AutoService(CachingService.class)
public class Coordinates implements CachingService  {

    private static final Logger LOG = CoordinateUtil.LOG;
    
    //private static ChronologyChangeListener ccl;

    private static final Cache<Integer, int[]> LANG_EXPAND_CACHE = Caffeine.newBuilder().maximumSize(100).build();
    
    public static class  Edit {
        public static EditCoordinateImmutable Default() {
            return EditCoordinateImmutable.make(
            TinkarTerm.USER.nid(), TinkarTerm.SOLOR_OVERLAY_MODULE.nid(),
                TinkarTerm.DEVELOPMENT_PATH.nid(),
                TinkarTerm.SOLOR_OVERLAY_MODULE.nid()
            );
        }
    }

    @Override
    public void reset() {

    }

    public static class Logic {
        public static LogicCoordinateImmutable ElPlusPlus() {
            throw new UnsupportedOperationException();
//            return LogicCoordinateImmutable.make(TinkarTerm.SNOROCKET_CLASSIFIER,
//                    TinkarTerm.EL_PLUS_PLUS_LOGIC_PROFILE,
//                    TinkarTerm.EL_PLUS_PLUS_INFERRED_ASSEMBLAGE,
//                    TinkarTerm.EL_PLUS_PLUS_STATED_ASSEMBLAGE,
//                    TinkarTerm.SOLOR_CONCEPT_ASSEMBLAGE,
//                    TinkarTerm.EL_PLUS_PLUS_DIGRAPH,
//                    TinkarTerm.SOLOR_ROOT);
        }
    }

    public static class Language {
        /**
         * A coordinate that completely ignores language - descriptions ranked by this coordinate will only be ranked by
         * description type and module preference.  This coordinate is primarily useful as a fallback coordinate for the final
         * {@link LanguageCoordinate#getNextPriorityLanguageCoordinate()} in a chain
         *
         * See {@link LanguageCoordinateService#getSpecifiedDescription(StampFilter, List, LanguageCoordinate)}
         * @param regularNameOnly if true,  only return regularname.  If false, prefer regular name, but will
         *     return a FQN or definition if regular name isn't available. 
         * @return the language coordinate
         *
         */
        public static LanguageCoordinateImmutable AnyLanguageRegularName() {
            return LanguageCoordinateImmutable.make(
                    TinkarTerm.LANGUAGE,
                    IntLists.immutable.of(TinkarTerm.DESCRIPTION_PATTERN.nid()),
                    IntLists.immutable.of(TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE.nid()),
                    IntLists.immutable.empty(),
                    IntLists.immutable.empty()
            );
        }

        /**
         * A coordinate that completely ignores language - descriptions ranked by this coordinate will only be ranked by
         * description type and module preference.  This coordinate is primarily useful as a fallback coordinate for the final
         * {@link LanguageCoordinate#getNextPriorityLanguageCoordinate()} in a chain
         *
         * See {@link LanguageCoordinateService#getSpecifiedDescription(StampFilter, List, LanguageCoordinate)}
         * @param fqnOnly if true,  only return fully qualified name.  If false, prefer fully qualified name, but will
         *     return a regular name or definition if fqn name isn't available. 
         * @return the language coordinate
         */
        public static LanguageCoordinateImmutable AnyLanguageFullyQualifiedName() {
            return LanguageCoordinateImmutable.make(
                    TinkarTerm.LANGUAGE,
                    IntLists.immutable.of(TinkarTerm.DESCRIPTION_PATTERN.nid()),
                    IntLists.immutable.of(TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid()),
                    IntLists.immutable.empty(),
                    IntLists.immutable.empty()
            );
        }

        /**
         * A coordinate that completely ignores language - descriptions ranked by this coordinate will only be ranked by
         * description type and module preference.  This coordinate is primarily useful as a fallback coordinate for the final
         * {@link LanguageCoordinate#getNextPriorityLanguageCoordinate()} in a chain
         *
         * See {@link LanguageCoordinateService#getSpecifiedDescription(StampFilter, List, LanguageCoordinate)}
         * @param defOnly if true,  only return definition name.  If false, prefer definition name, but will
         *     return a regular name or fqn if definition name isn't available. 
         * @return a coordinate that prefers definitions, of arbitrary language.
         * type
         */
        public static LanguageCoordinateImmutable AnyLanguageDefinition() {
            return LanguageCoordinateImmutable.make(
                    TinkarTerm.LANGUAGE,
                    IntLists.immutable.of(TinkarTerm.DESCRIPTION_PATTERN.nid()),
                    IntLists.immutable.of(TinkarTerm.DEFINITION_DESCRIPTION_TYPE.nid()),
                   IntLists.immutable.empty(),
                    IntLists.immutable.empty()
            );
        }

        /**
         * @return US English language coordinate, preferring FQNs, but allowing regular names, if no FQN is found.
         */
        public static LanguageCoordinateImmutable UsEnglishFullyQualifiedName() {
            return LanguageCoordinateImmutable.make(
                    TinkarTerm.ENGLISH_LANGUAGE.nid(),
                    IntLists.immutable.of(TinkarTerm.DESCRIPTION_PATTERN.nid()),
                    IntLists.immutable.of(TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid(),
                            TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE.nid()),
                    IntLists.immutable.of(TinkarTerm.US_DIALECT_PATTERN.nid(), TinkarTerm.GB_DIALECT_PATTERN.nid()),
                    IntLists.immutable.of(TinkarTerm.SOLOR_OVERLAY_MODULE.nid(), TinkarTerm.SOLOR_MODULE.nid())
            );
        }

        /**
         * @return US English language coordinate, preferring regular name, but allowing FQN names is no regular name is found
         */
        public static LanguageCoordinateImmutable UsEnglishRegularName() {
             return LanguageCoordinateImmutable.make(
                    TinkarTerm.ENGLISH_LANGUAGE.nid(),
                     IntLists.immutable.of(TinkarTerm.DESCRIPTION_PATTERN.nid()),
                    IntLists.immutable.of(TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE.nid(),
                            TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid()),
                    IntLists.immutable.of(TinkarTerm.US_DIALECT_PATTERN.nid(), TinkarTerm.GB_DIALECT_PATTERN.nid()),
                    IntLists.immutable.of(TinkarTerm.SOLOR_OVERLAY_MODULE.nid(), TinkarTerm.SOLOR_MODULE.nid())
            );
        }

        public static LanguageCoordinateImmutable GbEnglishFullyQualifiedName() {
            return LanguageCoordinateImmutable.make(
                    TinkarTerm.ENGLISH_LANGUAGE.nid(),
                    IntLists.immutable.of(TinkarTerm.DESCRIPTION_PATTERN.nid()),
                    IntLists.immutable.of(TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid(),
                            TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE.nid()),
                    IntLists.immutable.of(TinkarTerm.GB_DIALECT_PATTERN.nid(),
                            TinkarTerm.US_DIALECT_PATTERN.nid()),
                    IntLists.immutable.of(TinkarTerm.SOLOR_OVERLAY_MODULE.nid(), TinkarTerm.SOLOR_MODULE.nid())
             );
        }

        public static LanguageCoordinateImmutable GbEnglishPreferredName() {
            return LanguageCoordinateImmutable.make(
                    TinkarTerm.ENGLISH_LANGUAGE.nid(),
                    IntLists.immutable.of(TinkarTerm.DESCRIPTION_PATTERN.nid()),
                    IntLists.immutable.of(TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE.nid(),
                            TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid()),
                    IntLists.immutable.of(TinkarTerm.GB_DIALECT_PATTERN.nid(),
                            TinkarTerm.US_DIALECT_PATTERN.nid()),
                    IntLists.immutable.of(TinkarTerm.SOLOR_OVERLAY_MODULE.nid(), TinkarTerm.SOLOR_MODULE.nid())
            );
        }

        public static LanguageCoordinateImmutable SpanishFullyQualifiedName() {
            throw new UnsupportedOperationException();
//            return LanguageCoordinateImmutable.make(
//                    TinkarTerm.SPANISH_LANGUAGE.nid(),
//                    IntLists.immutable.of(expandDescriptionTypePreferenceList(null, TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid(),
//                            TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE.nid())),
//                    IntLists.immutable.of(TinkarTerm.SPANISH_LATIN_AMERICA_DIALECT_ASSEMBLAGE.nid()),
//                    IntLists.immutable.of(TinkarTerm.SCT_CORE_MODULE.nid(), TinkarTerm.SOLOR_OVERLAY_MODULE.nid(), TinkarTerm.SOLOR_MODULE.nid()),
//            );
        }

        public static LanguageCoordinateImmutable SpanishPreferredName() {
            throw new UnsupportedOperationException();
//            return LanguageCoordinateImmutable.make(
//                    TinkarTerm.SPANISH_LANGUAGE.nid(),
//                    IntLists.immutable.of(expandDescriptionTypePreferenceList(null, TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE.nid(),
//                            TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid())),
//                    IntLists.immutable.of(TinkarTerm.SPANISH_LATIN_AMERICA_DIALECT_ASSEMBLAGE.nid()),
//                    IntLists.immutable.of(TinkarTerm.SCT_CORE_MODULE.nid(), TinkarTerm.SOLOR_OVERLAY_MODULE.nid(), TinkarTerm.SOLOR_MODULE.nid()),
//            );
        }
    }

    public static class Filter {

        public static StampFilterRecord DevelopmentLatest() {
            return StampFilterRecord.make(StateSet.ACTIVE_AND_INACTIVE,
                    Position.LatestOnDevelopment(),
                    IntSets.immutable.empty());
        }

        public static StampFilterRecord DevelopmentLatestActiveOnly() {
            return StampFilterRecord.make(StateSet.ACTIVE_ONLY,
                    Position.LatestOnDevelopment(),
                    IntSets.immutable.empty());
        }

        public static StampFilterRecord MasterLatest() {
            return StampFilterRecord.make(StateSet.ACTIVE_AND_INACTIVE,
                    Position.LatestOnMaster(),
                    IntSets.immutable.empty());
        }

        public static StampFilterRecord MasterLatestActiveOnly() {
            return StampFilterRecord.make(StateSet.ACTIVE_ONLY,
                    Position.LatestOnMaster(),
                    IntSets.immutable.empty());
        }
    }

    public static class Position {
        public static StampPositionImmutable LatestOnDevelopment() {
            return StampPositionImmutable.make(Long.MAX_VALUE, TinkarTerm.DEVELOPMENT_PATH);
        }
        public static StampPositionImmutable LatestOnMaster() {
            return StampPositionImmutable.make(Long.MAX_VALUE, TinkarTerm.MASTER_PATH);
        }
    }

    public static class Path {

        public static StampPathImmutable Master() {
            return StampPathImmutable.make(TinkarTerm.MASTER_PATH, Sets.immutable.of(StampPositionImmutable.make(Long.MAX_VALUE, TinkarTerm.PRIMORDIAL_PATH.nid())));
        }

        public static StampPathImmutable Development() {
            return StampPathImmutable.make(TinkarTerm.DEVELOPMENT_PATH, Sets.immutable.of(StampPositionImmutable.make(Long.MAX_VALUE, TinkarTerm.PRIMORDIAL_PATH.nid())));
        }
    }

    public static class Manifold {
        public static final ManifoldCoordinateImmutable DevelopmentInferredRegularNameSort() {
            return ManifoldCoordinateImmutable.makeInferred(
                    Path.Development().getStampFilter(),
                    Language.UsEnglishRegularName(),
                    Logic.ElPlusPlus(), Activity.DEVELOPING, Edit.Default());
        }
        public static final ManifoldCoordinateImmutable DevelopmentStatedRegularNameSort() {
            return ManifoldCoordinateImmutable.makeStated(
                    Path.Development().getStampFilter(),
                    Language.UsEnglishRegularName(),
                    Logic.ElPlusPlus(), Activity.DEVELOPING, Edit.Default());
        }
    }
    
//
//    @Override
//    public void reset() {
//       LANG_EXPAND_CACHE.invalidateAll();
//    }
}
