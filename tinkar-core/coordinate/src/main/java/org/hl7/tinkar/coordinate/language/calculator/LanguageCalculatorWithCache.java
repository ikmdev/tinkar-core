package org.hl7.tinkar.coordinate.language.calculator;


import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.auto.service.AutoService;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.hl7.tinkar.collection.ConcurrentReferenceHashMap;
import org.hl7.tinkar.common.id.IntIdList;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.coordinate.language.LanguageCoordinate;
import org.hl7.tinkar.coordinate.language.LanguageCoordinateRecord;
import org.hl7.tinkar.coordinate.stamp.StampCoordinateRecord;
import org.hl7.tinkar.coordinate.stamp.calculator.Latest;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculator;
import org.hl7.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.terms.EntityFacade;
import org.hl7.tinkar.terms.TinkarTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.OptionalInt;

public class LanguageCalculatorWithCache implements LanguageCalculator {
    /**
     * The Constant LOG.
     */
    private static final Logger LOG = LoggerFactory.getLogger(LanguageCalculatorWithCache.class);
    private static final ConcurrentReferenceHashMap<StampLangRecord, LanguageCalculatorWithCache> SINGLETONS =
            new ConcurrentReferenceHashMap<>(ConcurrentReferenceHashMap.ReferenceType.WEAK,
                    ConcurrentReferenceHashMap.ReferenceType.WEAK);
    final StampCalculator stampCalculator;
    final ImmutableList<LanguageCoordinateRecord> languageCoordinateList;
    private final Cache<Integer, String> preferredCache =
            Caffeine.newBuilder().maximumSize(10240).build();
    private final Cache<Integer, String> fqnCache =
            Caffeine.newBuilder().maximumSize(10240).build();
    private final Cache<Integer, String> descriptionCache =
            Caffeine.newBuilder().maximumSize(10240).build();
    private final Cache<Integer, String> definitionCache =
            Caffeine.newBuilder().maximumSize(1024).build();
    private final Cache<Integer, ImmutableList<SemanticEntity>> descriptionsForComponentCache =
            Caffeine.newBuilder().maximumSize(1024).build();

    public LanguageCalculatorWithCache(StampCoordinateRecord stampFilter, ImmutableList<LanguageCoordinateRecord> languageCoordinateList) {
        this.stampCalculator = StampCalculatorWithCache.getCalculator(stampFilter);
        this.languageCoordinateList = languageCoordinateList;
    }

    /**
     * Gets the stampCoordinateRecord.
     *
     * @return the stampCoordinateRecord
     */
    public static LanguageCalculatorWithCache getCalculator(StampCoordinateRecord stampFilter,
                                                            ImmutableList<LanguageCoordinateRecord> languageCoordinateList) {
        return SINGLETONS.computeIfAbsent(new StampLangRecord(stampFilter, languageCoordinateList),
                filterKey -> new LanguageCalculatorWithCache(stampFilter, languageCoordinateList));
    }

    private Latest<PatternEntityVersion> getPattern(int patternNid) {
        return stampCalculator.latestPatternEntityVersion(patternNid);
    }

    private static record StampLangRecord(StampCoordinateRecord stampFilter,
                                          ImmutableList<LanguageCoordinateRecord> languageCoordinateList) {
    }

    @AutoService(CachingService.class)
    public static class CacheProvider implements CachingService {
        @Override
        public void reset() {
            SINGLETONS.clear();
        }
    }

    @Override
    public ImmutableList<LanguageCoordinateRecord> languageCoordinateList() {
        return languageCoordinateList;
    }

    @Override
    public Optional<String> getUserText() {
        // TODO
        throw new UnsupportedOperationException();
    }


    public Optional<String> getTextFromSemanticVersion(SemanticEntityVersion semanticEntityVersion) {
        OptionalInt optionalIndexForText = stampCalculator.getIndexForMeaning(semanticEntityVersion.patternNid(),
                TinkarTerm.TEXT_FOR_DESCRIPTION.nid());
        if (optionalIndexForText.isPresent()) {
            String text = (String) semanticEntityVersion.fields().get(optionalIndexForText.getAsInt());
            return Optional.of(text);
        }
        return Optional.empty();
    }

    @Override
    public ImmutableList<SemanticEntity> getDescriptionsForComponent(int componentNid) {
        return descriptionsForComponentCache.get(componentNid, nid -> {
            // Need semantics for each pattern in the language coordinate. If none in first priority,
            // repeat for each additional.
            for (LanguageCoordinate languageCoordinate : languageCoordinateList) {
                MutableList<SemanticEntity> descriptionList = Lists.mutable.ofInitialCapacity(16);
                for (int descPatternNid : languageCoordinate.descriptionPatternPreferenceNidList().toArray()) {
                    EntityService.get().forEachSemanticForComponentOfPattern(componentNid, descPatternNid, semanticEntity -> {
                        descriptionList.add(semanticEntity);
                    });
                    if (descriptionList.notEmpty()) {
                        break;
                    }
                }
                if (descriptionList.notEmpty()) {
                    return descriptionList.toImmutable();
                }
            }
            return Lists.immutable.empty();
        });
    }


    @Override
    public Optional<String> getAnyName(int componentNid) {
        return Optional.empty();
    }


    @Override
    public Optional<String> getDescriptionTextForComponentOfType(int entityNid, int descriptionTypeNid) {
        for (SemanticEntityVersion version : getDescriptionsForComponentOfType(entityNid, descriptionTypeNid)) {
            return getTextFromSemanticVersion(version);
        }
        return Optional.empty();
    }

    @Override
    public ImmutableList<SemanticEntityVersion> getDescriptionsForComponentOfType(int componentNid,
                                                                                  int descriptionTypeNid) {
        for (LanguageCoordinate languageCoordinate : languageCoordinateList()) {
            MutableList<SemanticEntityVersion> descriptionList = Lists.mutable.empty();
            for (int descriptionPatternNid : languageCoordinate.descriptionPatternPreferenceNidList().toArray()) {
                OptionalInt optionalTypeIndex = stampCalculator.getIndexForMeaning(descriptionPatternNid,
                        TinkarTerm.DESCRIPTION_TYPE.nid());
                if (optionalTypeIndex.isPresent()) {
                    PrimitiveData.get().forEachSemanticNidForComponentOfPattern(componentNid, descriptionPatternNid,
                            semanticNid -> {
                                SemanticEntity descriptionSemantic = Entity.getFast(semanticNid);
                                Latest<SemanticEntityVersion> latestDescriptionVersion =
                                        stampCalculator.latest(descriptionSemantic);
                                latestDescriptionVersion.ifPresent(descriptionVersion -> {
                                    Object fieldValue = descriptionVersion.fields().get(optionalTypeIndex.getAsInt());
                                    if (fieldValue instanceof EntityFacade entityFacade) {
                                        if (entityFacade.nid() == descriptionTypeNid) {
                                            descriptionList.add(descriptionVersion);
                                        }
                                    }
                                });

                            });
                }
            }
            if (descriptionList.notEmpty()) {
                return descriptionList.toImmutable();
            }
        }
        return Lists.immutable.empty();
    }

    @Override
    public Latest<SemanticEntityVersion> getSpecifiedDescription(ImmutableList<SemanticEntity> descriptionList) {
        for (LanguageCoordinate languageCoordinate : languageCoordinateList()) {
            Latest<SemanticEntityVersion> latestDescription = getSpecifiedDescription(descriptionList,
                    languageCoordinate.descriptionTypePreferenceNidList(), languageCoordinate);
            if (latestDescription.isPresent()) {
                return latestDescription;
            }
        }
        //Didn't find any that matched any of the allowed description types.
        return new Latest<>();
    }

    public Latest<SemanticEntityVersion> getSpecifiedDescription(ImmutableList<SemanticEntity> descriptionList,
                                                                 IntIdList descriptionTypePriority) {
        for (LanguageCoordinate languageCoordinate : languageCoordinateList()) {
            Latest<SemanticEntityVersion> latestDescription = getSpecifiedDescription(descriptionList,
                    descriptionTypePriority, languageCoordinate);
            if (latestDescription.isPresent()) {
                return latestDescription;
            }
        }
        //Didn't find any that matched any of the allowed description types.
        return new Latest<>();
    }

    private Latest<SemanticEntityVersion> getSpecifiedDescription(ImmutableList<SemanticEntity> descriptionList,
                                                                  IntIdList descriptionTypePriority,
                                                                  LanguageCoordinate languageCoordinate) {
        final MutableList<SemanticEntityVersion> descriptionsForLanguageOfType = Lists.mutable.empty();
        //Find all descriptions that match the language and description type - moving through the desired description types until
        //we find at least one.
        for (final int descType : descriptionTypePriority.toArray()) {
            for (SemanticEntity descriptionChronicle : descriptionList) {
                final Latest<SemanticEntityVersion> latestDescription = stampCalculator.latest(descriptionChronicle);

                if (latestDescription.isPresent()) {
                    for (SemanticEntityVersion descriptionVersion : latestDescription.versionList()) {
                        PatternEntity<PatternEntityVersion> patternEntity = descriptionVersion.pattern();
                        PatternEntityVersion patternEntityVersion = stampCalculator.latest(patternEntity).get();
                        int languageIndex = patternEntityVersion.indexForMeaning(TinkarTerm.LANGUAGE_CONCEPT_NID_FOR_DESCRIPTION);
                        Object languageObject = descriptionVersion.fields().get(languageIndex);
                        int descriptionTypeIndex = patternEntityVersion.indexForMeaning(TinkarTerm.DESCRIPTION_TYPE);
                        Object descriptionTypeObject = descriptionVersion.fields().get(descriptionTypeIndex);
                        if (languageObject instanceof EntityFacade languageFacade &&
                                descriptionTypeObject instanceof EntityFacade descriptionTypeFacade) {
                            if ((languageFacade.nid() == languageCoordinate.languageConceptNid() ||
                                    languageCoordinate.languageConceptNid() == TinkarTerm.LANGUAGE.nid()) // any language
                                    && descriptionTypeFacade.nid() == descType) {
                                descriptionsForLanguageOfType.add(descriptionVersion);
                            }
                        } else {
                            throw new IllegalStateException("Language object not instanceof EntityFacade: " + languageObject +
                                    " or Description type object not instance of EntityFacade: " + descriptionTypeObject);
                        }
                    }
                }
            }
            if (descriptionsForLanguageOfType.notEmpty()) {
                //If we found at least one that matches the language and type, go on to rank by dialect
                break;
            }
        }

        if (descriptionsForLanguageOfType.isEmpty()) {
            //Didn't find any that matched any of the allowed description types.
            return new Latest<>();
        }

        // handle dialect...
        final Latest<SemanticEntityVersion> preferredForDialect = new Latest<>(SemanticEntityVersion.class);

        if (languageCoordinate.dialectPatternPreferenceNidList() != null) {
            for (int dialectPatternNid : languageCoordinate.dialectPatternPreferenceNidList().toArray()) {
                if (preferredForDialect.isAbsent()) {
                    stampCalculator.latest(dialectPatternNid).ifPresent(versionObject -> {
                        if (versionObject instanceof PatternEntityVersion patternEntityVersion) {
                            int acceptabilityIndex = patternEntityVersion.indexForPurpose(TinkarTerm.DESCRIPTION_ACCEPTABILITY);
                            for (SemanticEntityVersion description : descriptionsForLanguageOfType) {
                                stampCalculator.forEachSemanticVersionForComponentOfPattern(description.nid(), dialectPatternNid,
                                        (semanticEntityVersion, entityVersion, patternVersion) -> {
                                            if (semanticEntityVersion.fields().get(acceptabilityIndex) instanceof EntityFacade accceptabilityFacade) {
                                                if (accceptabilityFacade.nid() == TinkarTerm.PREFERRED.nid()) {
                                                    preferredForDialect.addLatest(description);
                                                }
                                            }
                                        });
                            }
                        }
                    });
                }
            }
        }

        //If none matched the dialect rank list, just ignore the dialect, and keep all that matched the type and language.
        if (!preferredForDialect.isPresent()) {
            descriptionsForLanguageOfType.forEach((description) -> {
                preferredForDialect.addLatest(description);
            });
        }

        // add in module preferences if there is more than one.
        if (languageCoordinate.modulePreferenceNidListForLanguage() != null && languageCoordinate.modulePreferenceNidListForLanguage().notEmpty()) {
            for (int preference : languageCoordinate.modulePreferenceNidListForLanguage().toArray()) {
                for (SemanticEntityVersion descriptionVersion : preferredForDialect.versionList()) {
                    if (descriptionVersion.stamp().moduleNid() == preference) {
                        Latest<SemanticEntityVersion> preferredForModule = new Latest<>(descriptionVersion);
                        for (SemanticEntityVersion alternateVersion : preferredForDialect.versionList()) {
                            if (alternateVersion != preferredForModule.get()) {
                                preferredForModule.addLatest(alternateVersion);
                            }
                        }
                        preferredForModule.sortByState();
                        return preferredForModule;
                    }
                }
            }
        }
        preferredForDialect.sortByState();
        return preferredForDialect;
    }

    @Override
    public Optional<String> getDescriptionText(int componentNid) {
        return Optional.ofNullable(descriptionCache.get(componentNid, nid -> {
            Latest<SemanticEntityVersion> latestDescription
                    = getDescription(getDescriptionsForComponent(componentNid));
            if (latestDescription.isPresent()) {
                return extractText(latestDescription);
            }
            return null;
        }));
    }

    @Override
    public Optional<String> getRegularDescriptionText(int entityNid) {
        return Optional.ofNullable(preferredCache.get(entityNid, nid -> {
            Latest<SemanticEntityVersion> latestDescription
                    = getRegularDescription(getDescriptionsForComponent(entityNid));
            if (latestDescription.isPresent()) {
                return extractText(latestDescription);
            }
            return null;
        }));

    }

    private String extractText(Latest<SemanticEntityVersion> latestDescription) {
        SemanticEntityVersion descriptionVersion = latestDescription.get();
        PatternEntity<PatternEntityVersion> pattern = descriptionVersion.pattern();
        PatternEntityVersion patternVersion = stampCalculator.latest(pattern).get();
        String descriptionText = (String) descriptionVersion.fields().get(patternVersion.indexForMeaning(TinkarTerm.TEXT_FOR_DESCRIPTION));
        return descriptionText;
    }

    @Override
    public Optional<String> getFullyQualifiedNameText(int componentNid) {
        return Optional.ofNullable(fqnCache.get(componentNid, nid -> {
            Latest<SemanticEntityVersion> latestDescription
                    = getFullyQualifiedDescription(getDescriptionsForComponent(componentNid));
            if (latestDescription.isPresent()) {
                return extractText(latestDescription);
            }
            return null;
        }));
    }

    @Override
    public Optional<String> getDefinitionDescriptionText(int componentNid) {
        return Optional.ofNullable(definitionCache.get(componentNid, nid -> {
            Latest<SemanticEntityVersion> latestDescription
                    = getDefinitionDescription(getDescriptionsForComponent(componentNid));
            if (latestDescription.isPresent()) {
                return extractText(latestDescription);
            }
            return null;
        }));
    }

    @Override
    public Optional<String> getSemanticText(int nid) {
        Latest<Field<String>> textField = stampCalculator.getFieldForSemantic(nid,
                TinkarTerm.TEXT_FOR_DESCRIPTION.nid(),
                StampCalculator.FieldCriterion.MEANING);
        if (textField.isPresent()) {
            return Optional.ofNullable(textField.get().value());
        }
        Entity entity = Entity.getFast(nid);
        if (entity instanceof SemanticEntity semanticEntity) {
            Latest<PatternEntityVersion> latestPatternVersion = stampCalculator.latestPatternEntityVersion(semanticEntity.patternNid());
            if (latestPatternVersion.isPresent()) {
                PatternEntityVersion patternVersion = latestPatternVersion.get();
                StringBuilder sb = new StringBuilder("[");
                sb.append(getPreferredDescriptionTextWithFallbackOrNid(patternVersion.semanticMeaningNid()));
                sb.append("] of <");
                sb.append(getPreferredDescriptionTextWithFallbackOrNid(semanticEntity.referencedComponentNid()));
                sb.append("> for [");
                sb.append(getPreferredDescriptionTextWithFallbackOrNid(patternVersion.semanticPurposeNid()));
                sb.append("]");
                return Optional.of(sb.toString());
            }
        }

        return Optional.empty();
    }
}
