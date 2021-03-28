package org.hl7.tinkar.coordinate;

import java.util.List;
import java.util.logging.Logger;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.auto.service.AutoService;
import org.eclipse.collections.api.factory.Sets;
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
        public static LanguageCoordinateImmutable AnyLanguageRegularName(boolean regularNameOnly) {
            throw new UnsupportedOperationException();
//            return LanguageCoordinateImmutable.make(
//                    TinkarTerm.LANGUAGE,
//                    regularNameOnly ? IntLists.immutable.of(expandDescriptionTypePreferenceList(null, TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE.nid()))
//                        : IntLists.immutable.of(expandDescriptionTypePreferenceList(null, TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE.nid(),
//                            TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid(), TinkarTerm.DEFINITION_DESCRIPTION_TYPE.nid())),
//                    IntLists.immutable.empty(),
//                    IntLists.immutable.empty()
//            );
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
        public static LanguageCoordinateImmutable AnyLanguageFullyQualifiedName(boolean fqnOnly) {
            throw new UnsupportedOperationException();
//            return LanguageCoordinateImmutable.make(
//                    TinkarTerm.LANGUAGE,
//                    fqnOnly ? IntLists.immutable.of(expandDescriptionTypePreferenceList(null, TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid()))
//                            : IntLists.immutable.of(expandDescriptionTypePreferenceList(null, TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid(),
//                                TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE.nid(), TinkarTerm.DEFINITION_DESCRIPTION_TYPE.nid())),
//                    IntLists.immutable.empty(),
//                    IntLists.immutable.empty()
//            );
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
        public static LanguageCoordinateImmutable AnyLanguageDefinition(boolean defOnly) {
            throw new UnsupportedOperationException();
//            return LanguageCoordinateImmutable.make(
//                    TinkarTerm.LANGUAGE,
//                    defOnly ? IntLists.immutable.of(expandDescriptionTypePreferenceList(null, TinkarTerm.DEFINITION_DESCRIPTION_TYPE.nid()))
//                            : IntLists.immutable.of(expandDescriptionTypePreferenceList(null, TinkarTerm.DEFINITION_DESCRIPTION_TYPE.nid(),
//                                TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE.nid(), TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid())),
//                    IntLists.immutable.empty(),
//                    IntLists.immutable.empty()
//            );
        }

        /**
         * @return US English language coordinate, preferring FQNs, but allowing regular names, if no FQN is found.
         */
        public static LanguageCoordinateImmutable UsEnglishFullyQualifiedName() {
            throw new UnsupportedOperationException();
//            return LanguageCoordinateImmutable.make(
//                    TinkarTerm.ENGLISH_LANGUAGE.nid(),
//                    IntLists.immutable.of(expandDescriptionTypePreferenceList(null, TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid(),
//                            TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE.nid())),
//                    IntLists.immutable.of(TinkarTerm.US_DIALECT_ASSEMBLAGE.nid(), TinkarTerm.GB_DIALECT_ASSEMBLAGE.nid()),
//                    IntLists.immutable.of(TinkarTerm.SCT_CORE_MODULE.nid(), TinkarTerm.SOLOR_OVERLAY_MODULE.nid(), TinkarTerm.SOLOR_MODULE.nid()),
//                    AnyLanguageFullyQualifiedName(false)
//            );
        }

        /**
         * @return US English language coordinate, preferring regular name, but allowing FQN names is no regular name is found
         */
        public static LanguageCoordinateImmutable UsEnglishRegularName() {
            throw new UnsupportedOperationException();
//            return LanguageCoordinateImmutable.make(
//                    TinkarTerm.ENGLISH_LANGUAGE.nid(),
//                    IntLists.immutable.of(expandDescriptionTypePreferenceList(null, TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE.nid(),
//                            TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid())),
//                    IntLists.immutable.of(TinkarTerm.US_DIALECT_ASSEMBLAGE.nid(), TinkarTerm.GB_DIALECT_ASSEMBLAGE.nid()),
//                    IntLists.immutable.of(TinkarTerm.SCT_CORE_MODULE.nid(), TinkarTerm.SOLOR_OVERLAY_MODULE.nid(), TinkarTerm.SOLOR_MODULE.nid()),
//                    AnyLanguageRegularName(false)
//            );
        }

        public static LanguageCoordinateImmutable GbEnglishFullyQualifiedName() {
            throw new UnsupportedOperationException();
//            return LanguageCoordinateImmutable.make(
//                    TinkarTerm.ENGLISH_LANGUAGE.nid(),
//                    IntLists.immutable.of(expandDescriptionTypePreferenceList(null, TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid(),
//                            TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE.nid())),
//                    IntLists.immutable.of(TinkarTerm.GB_DIALECT_ASSEMBLAGE.nid(),
//                            TinkarTerm.US_DIALECT_ASSEMBLAGE.nid()),
//                    IntLists.immutable.of(TinkarTerm.SCT_CORE_MODULE.nid(), TinkarTerm.SOLOR_OVERLAY_MODULE.nid(), TinkarTerm.SOLOR_MODULE.nid()),
//                    AnyLanguageFullyQualifiedName(false)
//            );
        }

        public static LanguageCoordinateImmutable GbEnglishPreferredName() {
            throw new UnsupportedOperationException();
//            return LanguageCoordinateImmutable.make(
//                    TinkarTerm.ENGLISH_LANGUAGE.nid(),
//                    IntLists.immutable.of(expandDescriptionTypePreferenceList(null, TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE.nid(),
//                            TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid())),
//                    IntLists.immutable.of(TinkarTerm.GB_DIALECT_ASSEMBLAGE.nid(),
//                            TinkarTerm.US_DIALECT_ASSEMBLAGE.nid()),
//                    IntLists.immutable.of(TinkarTerm.SCT_CORE_MODULE.nid(), TinkarTerm.SOLOR_OVERLAY_MODULE.nid(), TinkarTerm.SOLOR_MODULE.nid()),
//                    AnyLanguageRegularName(false)
//            );
        }

        public static LanguageCoordinateImmutable SpanishFullyQualifiedName() {
            throw new UnsupportedOperationException();
//            return LanguageCoordinateImmutable.make(
//                    TinkarTerm.SPANISH_LANGUAGE.nid(),
//                    IntLists.immutable.of(expandDescriptionTypePreferenceList(null, TinkarTerm.FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE.nid(),
//                            TinkarTerm.REGULAR_NAME_DESCRIPTION_TYPE.nid())),
//                    IntLists.immutable.of(TinkarTerm.SPANISH_LATIN_AMERICA_DIALECT_ASSEMBLAGE.nid()),
//                    IntLists.immutable.of(TinkarTerm.SCT_CORE_MODULE.nid(), TinkarTerm.SOLOR_OVERLAY_MODULE.nid(), TinkarTerm.SOLOR_MODULE.nid()),
//                    // Adding next priority language coordinate to be available for testing, and fallback.
//                    UsEnglishFullyQualifiedName()
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
//                    // Adding next priority language coordinate to be available for testing, and fallback.
//                    UsEnglishFullyQualifiedName()
//            );
        }
        
        /**
         * Take in a list of the description type prefs, such as {@link TinkarTerm#FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE}, {@link TinkarTerm#REGULAR_NAME_DESCRIPTION_TYPE}
         * and include any non-core description types that are linked to these core types, in the right order, so that the LanguageCoordinates can include the 
         * non-core description types in the appropriate places when looking for descriptions.
         * @param descriptionTypePreferenceList the starting list - should only consist of core description types - 
         * {@link TinkarTerm#FULLY_QUALIFIED_NAME_DESCRIPTION_TYPE}, {@link TinkarTerm#REGULAR_NAME_DESCRIPTION_TYPE}, {@link TinkarTerm#DEFINITION_DESCRIPTION_TYPE}
         * @param stampFilter - optional - if not provided, uses {@link sh.isaac.api.coordinate.Coordinates.Filter#DevelopmentLatestActiveOnly()}
         * @return the initial list, plus any equivalent non-core types in the appropriate order.  See {@link DynamicConstants#DYNAMIC_DESCRIPTION_CORE_TYPE}
         */
        public static int[] expandDescriptionTypePreferenceList(StampFilter stampFilter, int ... descriptionTypePreferenceList) {
            throw new UnsupportedOperationException();
//            LOG.trace("Expand desription types requested");
//            StampFilter filter = stampFilter == null ? Coordinates.Filter.DevelopmentLatestActiveOnly() : stampFilter;
//            int requestKey = filter.hashCode();
//            for (int nid : descriptionTypePreferenceList) {
//                requestKey = 97 * requestKey + nid;
//            }
//
//            return LANG_EXPAND_CACHE.get(requestKey, keyAgain -> {
//                long time = System.currentTimeMillis();
//
//                if (ccl == null) {
//                    ccl = new ChronologyChangeListener() {
//                        UUID me = UUID.randomUUID();
//                        {
//                            Get.commitService().addChangeListener(this);
//                        }
//
//                        @Override
//                        public void handleCommit(CommitRecord commitRecord) {
//                            // ignore
//                        }
//
//                        @Override
//                        public void handleChange(SemanticEntity sc) {
//                            LANG_EXPAND_CACHE.invalidateAll();
//                        }
//
//                        @Override
//                        public void handleChange(ConceptChronology cc) {
//                            LANG_EXPAND_CACHE.invalidateAll();
//                        }
//
//                        @Override
//                        public UUID getListenerUuid() {
//                            return me;
//                        }
//                    };
//                }
//                MutableIntObjectMap<MutableIntSet> equivalentTypes = MutableIntObjectMapFactoryImpl.INSTANCE.empty();
//
//                //Collect the mappings from core types -> non core types
//                IntStream nids = Get.identifierService().getNidsForAssemblage(DynamicConstants.get().DYNAMIC_DESCRIPTION_CORE_TYPE.nid(), false);
//                nids.forEach(nid -> {
//                    SemanticEntity sc = Get.assemblageService().getSemanticEntity(nid);
//                    DynamicVersion dv = (DynamicVersion) sc.getLatestVersion(filter).get();
//                    int coreType = Get.identifierService().getNidForUuids(((DynamicUUID) dv.getData(0)).getDataUUID());
//                    MutableIntSet mapped = equivalentTypes.get(coreType);
//                    if (mapped == null) {
//                        mapped = MutableIntSetFactoryImpl.INSTANCE.empty();
//                        equivalentTypes.put(coreType, mapped);
//                    }
//                    mapped.add(sc.getReferencedComponentNid());
//                });
//
//                if (equivalentTypes.isEmpty()) {
//                    //this method is a noop
//                    LOG.trace("Expanded description types call is a noop in {}ms", System.currentTimeMillis() - time);
//                    return descriptionTypePreferenceList;
//                }
//
//                MutableIntList result = IntLists.mutable.empty();
//                IntList startNids = IntLists.immutable.of(descriptionTypePreferenceList);
//                for (int coreType : descriptionTypePreferenceList) {
//                    if (!result.contains(coreType)) {
//                        result.add(coreType);
//                    }
//                    MutableIntSet nonCoreTypes = equivalentTypes.get(coreType);
//                    if (nonCoreTypes != null) {
//                        nonCoreTypes.forEach(type -> {
//                            if (!result.contains(type)) {
//                                result.add(type);
//                            }
//                        });
//                    }
//                }
//                LOG.info("Expanded language type list from {} to {} in {}ms", startNids, result, System.currentTimeMillis() - time);
//                return result.toArray(new int[result.size()]);
//            });
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
