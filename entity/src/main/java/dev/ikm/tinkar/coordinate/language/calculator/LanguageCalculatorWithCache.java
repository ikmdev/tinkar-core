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
package dev.ikm.tinkar.coordinate.language.calculator;


import dev.ikm.tinkar.terms.KernelTerm;
import dev.ikm.tinkar.common.service.internal.EntityStore;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import dev.ikm.tinkar.common.id.LongIdList;
import dev.ikm.tinkar.common.service.CachingService;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.coordinate.language.LanguageCoordinate;
import dev.ikm.tinkar.coordinate.language.LanguageCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.StampCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculator;
import dev.ikm.tinkar.coordinate.stamp.calculator.StampCalculatorWithCache;
import dev.ikm.tinkar.entity.CacheInvalidationSubscriber;
import dev.ikm.tinkar.entity.Entity;
import dev.ikm.tinkar.entity.EntityHandle;
import dev.ikm.tinkar.entity.EntityService;
import dev.ikm.tinkar.entity.EntityVersion;
import dev.ikm.tinkar.entity.Field;
import dev.ikm.tinkar.entity.PatternEntity;
import dev.ikm.tinkar.entity.PatternEntityVersion;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;
import dev.ikm.tinkar.entity.SemanticVersionRecord;
import dev.ikm.tinkar.terms.EntityFacade;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.OptionalInt;

public class LanguageCalculatorWithCache implements LanguageCalculator {
    /**
     * The Constant LOG.
     */
    private static final Logger LOG = LoggerFactory.getLogger(LanguageCalculatorWithCache.class);
    private static final Cache<StampLangRecord, LanguageCalculatorWithCache> SINGLETONS = Caffeine.newBuilder().weakValues().build();
    final StampCalculator stampCalculator;
    final ImmutableList<LanguageCoordinateRecord> languageCoordinateList;
    private final Cache<Long, String> preferredCache =
            Caffeine.newBuilder().maximumSize(10240).build();
    private final Cache<Long, String> fqnCache =
            Caffeine.newBuilder().maximumSize(10240).build();
    private final Cache<Long, String> descriptionCache =
            Caffeine.newBuilder().maximumSize(10240).build();
    private final Cache<Long, String> definitionCache =
            Caffeine.newBuilder().maximumSize(1024).build();
    private final Cache<Long, ImmutableList<SemanticEntity>> descriptionsForComponentCache =
            Caffeine.newBuilder().maximumSize(1024).build();

    private final CacheInvalidationSubscriber cacheInvalidationSubscriber = new CacheInvalidationSubscriber();

    public LanguageCalculatorWithCache(StampCoordinateRecord stampFilter, ImmutableList<LanguageCoordinateRecord> languageCoordinateList) {
        this.stampCalculator = StampCalculatorWithCache.getCalculator(stampFilter);
        this.languageCoordinateList = languageCoordinateList;
        this.cacheInvalidationSubscriber.addCaches(preferredCache, fqnCache, descriptionCache, definitionCache, descriptionsForComponentCache);
        Entity.provider().addSubscriberWithWeakReference(this.cacheInvalidationSubscriber);
    }

    /**
     * Gets the stampCoordinateRecord.
     *
     * @return the stampCoordinateRecord
     */
    public static LanguageCalculatorWithCache getCalculator(StampCoordinateRecord stampFilter,
                                                            ImmutableList<LanguageCoordinateRecord> languageCoordinateList) {
        return SINGLETONS.get(new StampLangRecord(stampFilter, languageCoordinateList),
                filterKey -> new LanguageCalculatorWithCache(stampFilter, languageCoordinateList));
    }

    private Latest<PatternEntityVersion> getPattern(long patternNid) {
        return stampCalculator.latestPatternEntityVersion(patternNid);
    }

    private static record StampLangRecord(StampCoordinateRecord stampFilter,
                                          ImmutableList<LanguageCoordinateRecord> languageCoordinateList) {
    }

    public static class CacheProvider implements CachingService {
        @Override
        public void reset() {
            SINGLETONS.invalidateAll();
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
                KernelTerm.TEXT_FOR_DESCRIPTION.nid());
        if (optionalIndexForText.isPresent()) {
            String text = (String) semanticEntityVersion.fieldValues().get(optionalIndexForText.getAsInt());
            return Optional.of(text);
        }
        return Optional.empty();
    }

    @Override
    public ImmutableList<SemanticEntity> getDescriptionsForComponent(long componentNid) {
        return descriptionsForComponentCache.get(componentNid, nid -> {
            // Need semantics for each pattern in the language coordinate. If none in first priority,
            // repeat for each additional.
            for (LanguageCoordinate languageCoordinate : languageCoordinateList) {
                MutableList<SemanticEntity> descriptionList = Lists.mutable.ofInitialCapacity(16);
                for (long descPatternNid : languageCoordinate.descriptionPatternPreferenceNidList().toArray()) {
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
    public Optional<String> getDescriptionTextForComponentOfType(long entityNid, long descriptionTypeNid) {
        for (SemanticEntityVersion version : getDescriptionsForComponentOfType(entityNid, descriptionTypeNid)) {
            return getTextFromSemanticVersion(version);
        }
        return Optional.empty();
    }

    @Override
    public ImmutableList<SemanticEntityVersion> getDescriptionsForComponentOfType(long componentNid,
                                                                                  long descriptionTypeNid) {
        for (LanguageCoordinate languageCoordinate : languageCoordinateList()) {
            MutableList<SemanticEntityVersion> descriptionList = Lists.mutable.empty();
            for (long descriptionPatternNid : languageCoordinate.descriptionPatternPreferenceNidList().toArray()) {
                OptionalInt optionalTypeIndex = stampCalculator.getIndexForMeaning(descriptionPatternNid,
                        KernelTerm.DESCRIPTION_TYPE.nid());
                if (optionalTypeIndex.isPresent()) {
                    EntityStore.current().forEachSemanticNidForComponentOfPattern(componentNid, descriptionPatternNid,
                            semanticNid -> {
                                SemanticEntity descriptionSemantic = EntityHandle.get(semanticNid).expectSemantic();
                                Latest<SemanticEntityVersion> latestDescriptionVersion =
                                        stampCalculator.latest(descriptionSemantic);
                                latestDescriptionVersion.ifPresent(descriptionVersion -> {
                                    Object fieldValue = descriptionVersion.fieldValues().get(optionalTypeIndex.getAsInt());
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
                                                                 LongIdList descriptionTypePriority) {
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
                                                                  LongIdList descriptionTypePriority,
                                                                  LanguageCoordinate languageCoordinate) {
        final MutableList<SemanticEntityVersion> descriptionsForLanguageOfType = Lists.mutable.empty();
        //Find all descriptions that match the language and description type - moving through the desired description types until
        //we find at least one.
        for (final long descTypeNid : descriptionTypePriority.toArray()) {
            for (SemanticEntity descriptionChronicle : descriptionList) {
                final Latest<SemanticEntityVersion> latestDescription = stampCalculator.latest(descriptionChronicle);

                if (latestDescription.isPresent()) {
                    for (SemanticEntityVersion descriptionVersion : latestDescription.versionList()) {
                        PatternEntityVersion patternEntityVersion = stampCalculator.latestPatternEntityVersion(descriptionVersion.pattern()).get();
                        int languageIndex = patternEntityVersion.indexForMeaning(KernelTerm.LANGUAGE_CONCEPT_NID_FOR_DESCRIPTION);
                        Object languageObject = descriptionVersion.fieldValues().get(languageIndex);
                        int descriptionTypeIndex = patternEntityVersion.indexForMeaning(KernelTerm.DESCRIPTION_TYPE);
                        Object descriptionTypeObject = descriptionVersion.fieldValues().get(descriptionTypeIndex);
                        if (languageObject instanceof EntityFacade languageFacade &&
                                descriptionTypeObject instanceof EntityFacade descriptionTypeFacade) {
                            if ((languageFacade.nid() == languageCoordinate.languageConceptNid() ||
                                    languageCoordinate.languageConceptNid() == KernelTerm.LANGUAGE.nid()) // any language
                                    && descriptionTypeFacade.nid() == descTypeNid) {
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
            for (long dialectPatternNid : languageCoordinate.dialectPatternPreferenceNidList().toArray()) {
                if (preferredForDialect.isAbsent()) {
                    stampCalculator.latest(dialectPatternNid).ifPresent(versionObject -> {
                        if (versionObject instanceof PatternEntityVersion patternEntityVersion) {
                            int acceptabilityIndex = patternEntityVersion.indexForPurpose(KernelTerm.DESCRIPTION_ACCEPTABILITY);
                            for (SemanticEntityVersion description : descriptionsForLanguageOfType) {
                                stampCalculator.forEachSemanticVersionForComponentOfPattern(description.nid(), dialectPatternNid,
                                        (semanticEntityVersion, entityVersion, patternVersion) -> {
                                            if (semanticEntityVersion.fieldValues().get(acceptabilityIndex) instanceof EntityFacade accceptabilityFacade) {
                                                if (accceptabilityFacade.nid() == KernelTerm.PREFERRED.nid()) {
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
            for (long preference : languageCoordinate.modulePreferenceNidListForLanguage().toArray()) {
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
    public Optional<String> getDescriptionText(long componentNid) {
        return Optional.ofNullable(descriptionCache.get(componentNid, nid -> {
            Latest<SemanticEntityVersion> latestDescription
                    = getDescription(getDescriptionsForComponent(componentNid));
            if (latestDescription.isPresent()) {
                return extractText(latestDescription);
            }
            Latest<? extends EntityVersion> latestEntity = stampCalculator.latest(nid);
            if (latestEntity.isPresent()) {
                EntityVersion ev = latestEntity.get();
                if (ev instanceof SemanticVersionRecord semanticVersionRecord) {
                    Optional<String> text = getTextFromSemanticVersion(semanticVersionRecord);
                    if (text.isPresent()) {
                        return text.get();
                    }
                }
                return null;
            }
            return null;
        }));
    }

    @Override
    public Optional<String> getRegularDescriptionText(long entityNid) {
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
        PatternEntityVersion patternVersion = stampCalculator.latestPatternEntityVersion(descriptionVersion.pattern()).get();
        String descriptionText = (String) descriptionVersion.fieldValues().get(patternVersion.indexForMeaning(KernelTerm.TEXT_FOR_DESCRIPTION));
        return descriptionText;
    }

    @Override
    public Optional<String> getFullyQualifiedNameText(long componentNid) {
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
    public Optional<String> getDefinitionDescriptionText(long componentNid) {
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
    public Optional<String> getSemanticText(long nid) {
        Latest<Field<String>> textField = stampCalculator.getFieldForSemantic(nid,
                KernelTerm.TEXT_FOR_DESCRIPTION.nid(),
                StampCalculator.FieldCriterion.MEANING);
        if (textField.isPresent()) {
            return Optional.ofNullable(textField.get().value());
        }
        Entity entity = EntityHandle.get(nid).orNull();
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
