package org.hl7.tinkar.coordinate.language;

import java.util.Objects;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.auto.service.AutoService;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.hl7.tinkar.collection.ConcurrentReferenceHashMap;
import org.hl7.tinkar.common.binary.Decoder;
import org.hl7.tinkar.common.binary.DecoderInput;
import org.hl7.tinkar.common.binary.Encoder;
import org.hl7.tinkar.common.binary.EncoderOutput;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.coordinate.ImmutableCoordinate;
import org.hl7.tinkar.coordinate.stamp.StampFilter;
import org.hl7.tinkar.coordinate.stamp.StampFilterRecord;
import org.hl7.tinkar.terms.State;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.entity.calculator.Latest;
import org.hl7.tinkar.terms.ConceptFacade;
import org.hl7.tinkar.terms.EntityFacade;
import org.hl7.tinkar.terms.PatternFacade;
import org.hl7.tinkar.terms.TinkarTerm;

public final class LanguageCoordinateImmutable implements LanguageCoordinate, ImmutableCoordinate /*ChronologyChangeListener,*/ {

    private static final ConcurrentReferenceHashMap<LanguageCoordinateImmutable, LanguageCoordinateImmutable> SINGLETONS =
            new ConcurrentReferenceHashMap<>(ConcurrentReferenceHashMap.ReferenceType.WEAK,
                    ConcurrentReferenceHashMap.ReferenceType.WEAK);


    @AutoService(CachingService.class)
    public static class CacheProvider implements CachingService {
        @Override
        public void reset() {
            SINGLETONS.clear();
        }
    }

    private static final int marshalVersion = 1;

    final private int languageConceptNid;
    final private ImmutableIntList descriptionPatternList;
    final private ImmutableIntList descriptionTypePreferenceList;
    final private ImmutableIntList dialectAssemblagePreferenceList;
    final private ImmutableIntList modulePreferenceListForLanguage;

    private Cache<StampFilterRecord, Cache<Integer, String>> preferredCaches;

    private Cache<StampFilterRecord, Cache<Integer, String>> fqnCaches;

    private Cache<StampFilterRecord, Cache<Integer, String>> descriptionCaches;

    private Cache<StampFilterRecord, Cache<Integer, String>> definitionCaches;

    private LanguageCoordinateImmutable(ConceptFacade languageConcept,
                                        ImmutableIntList descriptionPatternList,
                                        ImmutableIntList descriptionTypePreferenceList,
                                        ImmutableIntList dialectAssemblagePreferenceList,
                                        ImmutableIntList modulePreferenceListForLanguage) {
        this(languageConcept.nid(), descriptionPatternList, descriptionTypePreferenceList, dialectAssemblagePreferenceList,
                modulePreferenceListForLanguage);
    }

    private LanguageCoordinateImmutable(int languageConceptNid,
                                        ImmutableIntList descriptionPatternList,
                                        ImmutableIntList descriptionTypePreferenceList,
                                        ImmutableIntList dialectAssemblagePreferenceList,
                                        ImmutableIntList modulePreferenceListForLanguage) {
        this.languageConceptNid = languageConceptNid;
        this.descriptionPatternList = descriptionPatternList;
        this.descriptionTypePreferenceList = descriptionTypePreferenceList;
        this.dialectAssemblagePreferenceList = dialectAssemblagePreferenceList == null ? IntLists.immutable.empty() : dialectAssemblagePreferenceList;
        this.modulePreferenceListForLanguage = modulePreferenceListForLanguage == null ? IntLists.immutable.empty() : modulePreferenceListForLanguage;
    }

    private LanguageCoordinateImmutable setupCache() {
        this.preferredCaches =
                Caffeine.newBuilder().maximumSize(128).build();

        this.fqnCaches =
                Caffeine.newBuilder().maximumSize(128).build();

        this.descriptionCaches =
                Caffeine.newBuilder().maximumSize(128).build();

        this.definitionCaches =
                Caffeine.newBuilder().maximumSize(128).build();

        //        Get.commitService().addChangeListener(this);
        return this;
    }
/*
    @Override
    public void handleChange(ConceptChronology cc) {
        // nothing to do
    }

    @Override
    public void handleChange(SemanticEntity sc) {
        this.fqnCaches.clear();
        this.preferredCaches.clear();
        this.descriptionCaches.clear();
    }

    @Override
    public void handleCommit(CommitRecord commitRecord) {
        this.fqnCaches.clear();
        this.preferredCaches.clear();
        this.descriptionCaches.clear();
    }

    @Override
    public UUID getListenerUuid() {
        return getLanguageCoordinateUuid();
    }
*/

    public LanguageCoordinateImmutable(DecoderInput in) {
        this(in.readNid(), IntLists.immutable.of(in.readNidArray()), IntLists.immutable.of(in.readNidArray()), IntLists.immutable.of(in.readNidArray()),
                IntLists.immutable.of(in.readNidArray()));
    }

    @Override
    @Encoder
    public void encode(EncoderOutput out) {
        out.writeNid(this.languageConceptNid);
        out.writeNidArray(this.descriptionTypePreferenceList.toArray());
        out.writeNidArray(this.dialectAssemblagePreferenceList.toArray());
        out.writeNidArray(this.modulePreferenceListForLanguage.toArray());
    }

    @Decoder
    public static LanguageCoordinateImmutable decode(DecoderInput in) {
        int objectMarshalVersion = in.encodingFormatVersion();
        switch (objectMarshalVersion) {
            case marshalVersion:
                return SINGLETONS.computeIfAbsent(new LanguageCoordinateImmutable(in),
                        languageCoordinateImmutable -> languageCoordinateImmutable.setupCache());
            default:
                throw new UnsupportedOperationException("Unsupported version: " + objectMarshalVersion);
        }
    }

    public static LanguageCoordinateImmutable make(ConceptFacade languageConcept,
                                                   ImmutableIntList descriptionPatternList,
                                                   ImmutableIntList descriptionTypePreferenceList,
                                                   ImmutableIntList dialectAssemblagePreferenceList,
                                                   ImmutableIntList modulePreferenceListForLanguage) {
        return SINGLETONS.computeIfAbsent(new LanguageCoordinateImmutable(languageConcept.nid(),
                        descriptionPatternList,
                        descriptionTypePreferenceList, dialectAssemblagePreferenceList,
                        modulePreferenceListForLanguage),
                languageCoordinateImmutable -> languageCoordinateImmutable.setupCache());
    }

    public static LanguageCoordinateImmutable make(ConceptFacade languageConcept,
                                                   PatternFacade patternFacade,
                                                   ImmutableIntList descriptionTypePreferenceList,
                                                   ImmutableIntList dialectAssemblagePreferenceList,
                                                   ImmutableIntList modulePreferenceListForLanguage) {
        return SINGLETONS.computeIfAbsent(new LanguageCoordinateImmutable(languageConcept.nid(),
                        IntLists.immutable.of(patternFacade.nid()),
                        descriptionTypePreferenceList, dialectAssemblagePreferenceList,
                        modulePreferenceListForLanguage),
                languageCoordinateImmutable -> languageCoordinateImmutable.setupCache());
    }


    /**
     * @param languageConceptNid
     * @param descriptionTypePreferenceList
     * @param dialectAssemblagePreferenceList
     * @param modulePreferenceListForLanguage - if null, treated as empty
     * @return
     */
    public static LanguageCoordinateImmutable make(int languageConceptNid,
                                                   ImmutableIntList descriptionPatternList,
                                                   ImmutableIntList descriptionTypePreferenceList,
                                                   ImmutableIntList dialectAssemblagePreferenceList,
                                                   ImmutableIntList modulePreferenceListForLanguage) {
        return SINGLETONS.computeIfAbsent(new LanguageCoordinateImmutable(languageConceptNid,
                        descriptionPatternList,
                        descriptionTypePreferenceList, dialectAssemblagePreferenceList,
                        modulePreferenceListForLanguage),
                languageCoordinateImmutable -> languageCoordinateImmutable.setupCache());
    }


    @Override
    public int[] getDescriptionTypePreferenceList() {
        return this.descriptionTypePreferenceList.toArray();
    }

    @Override
    public ConceptFacade[] getDescriptionTypeFacadePreferenceList() {
        return this.descriptionTypePreferenceList.collect(nid ->
                Entity.getFast(nid)).toArray(new ConceptFacade[this.descriptionTypePreferenceList.size()]);
    }

    @Override
    public int[] getDialectPatternPreferenceList() {
        return this.dialectAssemblagePreferenceList.toArray();
    }

    @Override
    public PatternFacade[] getDialectPatternFacadePreferenceList() {
        return this.dialectAssemblagePreferenceList.collect(nid ->
                Entity.getFast(nid)).toArray(new PatternFacade[this.dialectAssemblagePreferenceList.size()]);
    }

    @Override
    public int[] getModulePreferenceListForLanguage() {
        return this.modulePreferenceListForLanguage.toArray();
    }

    @Override
    public ConceptFacade[] getModuleFacadePreferenceListForLanguage() {
        return this.modulePreferenceListForLanguage.collect(nid ->
                Entity.getFast(nid)).toArray(new ConceptFacade[this.modulePreferenceListForLanguage.size()]);
    }

    @Override
    public int getLanguageConceptNid() {
        return this.languageConceptNid;
    }

    @Override
    public org.hl7.tinkar.terms.ConceptFacade getLanguageConcept() {
        return Entity.getFast(this.languageConceptNid);
    }

    @Override
    public LanguageCoordinateImmutable toLanguageCoordinateImmutable() {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LanguageCoordinateImmutable that)) return false;
        return getLanguageConceptNid() == that.getLanguageConceptNid() &&
                getDescriptionTypePreferenceList().equals(that.getDescriptionTypePreferenceList()) &&
                getDialectPatternPreferenceList().equals(that.getDialectPatternPreferenceList()) &&
                getModulePreferenceListForLanguage().equals(that.getModulePreferenceListForLanguage());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getLanguageConceptNid(), getDescriptionTypePreferenceList(), getDialectPatternPreferenceList(), getModulePreferenceListForLanguage());
    }

    @Override
    public Optional<String> getDescriptionText(int componentNid, StampFilter stampFilter) {
        Cache<Integer, String> descriptionCache = this.descriptionCaches.get(stampFilter.toStampFilterImmutable(),
                stampFilterImmutable -> Caffeine.newBuilder().maximumSize(10240).build());
        return Optional.ofNullable(descriptionCache.get(componentNid, nid -> {
            Latest<SemanticEntityVersion> latestDescription
                        = getDescription(getDescriptionsForComponent(componentNid), stampFilter);
            if (latestDescription.isPresent()) {
                return extractText(stampFilter, latestDescription);
            }
            return null;
          }));
    }

    @Override
    public Optional<String> getRegularDescriptionText(int entityNid, StampFilter stampFilter) {
        Cache<Integer, String> preferredCache = this.preferredCaches.get(stampFilter.toStampFilterImmutable(),
                stampFilterImmutable -> Caffeine.newBuilder().maximumSize(10240).build());

        return Optional.ofNullable(preferredCache.get(entityNid, nid -> {
            Latest<SemanticEntityVersion> latestDescription
                    = getRegularDescription(getDescriptionsForComponent(entityNid), stampFilter);
            if (latestDescription.isPresent()) {
                return extractText(stampFilter, latestDescription);
            }
            return null;
        }));

    }

    private String extractText(StampFilter stampFilter, Latest<SemanticEntityVersion> latestDescription) {
        SemanticEntityVersion descriptionVersion = latestDescription.get();
        PatternEntity pattern = descriptionVersion.pattern();
        PatternEntityVersion patternVersion = stampFilter.stampCalculator().latest(pattern).get();
        String descriptionText = (String) descriptionVersion.fields().get(patternVersion.indexForMeaning(TinkarTerm.TEXT_FOR_DESCRIPTION));
        return descriptionText;
    }

    @Override
    public Optional<String> getFullyQualifiedNameText(int componentNid, StampFilter stampFilter) {
       Cache<Integer, String> fqnCache = this.fqnCaches.get(stampFilter.toStampFilterImmutable(),
                stampFilterImmutable -> Caffeine.newBuilder().maximumSize(10240).build());

        return Optional.ofNullable(fqnCache.get(componentNid, nid -> {
            Latest<SemanticEntityVersion> latestDescription
                    = getFullyQualifiedDescription(getDescriptionsForComponent(componentNid), stampFilter);
            if (latestDescription.isPresent()) {
                return extractText(stampFilter, latestDescription);
            }
            return null;
        }));
    }

    @Override
    public Optional<String> getDefinitionDescriptionText(int componentNid, StampFilter stampFilter) {
        Cache<Integer, String> definitionCache = this.definitionCaches.get(stampFilter.toStampFilterImmutable(),
                stampFilterImmutable -> Caffeine.newBuilder().maximumSize(10240).build());

        return Optional.ofNullable(definitionCache.get(componentNid, nid -> {
            Latest<SemanticEntityVersion> latestDescription
                    = getDefinitionDescription(getDescriptionsForComponent(componentNid), stampFilter);
            if (latestDescription.isPresent()) {
                return extractText(stampFilter, latestDescription);
            }
            return null;
        }));
    }

    /**
     * To string.
     *
     * @return the string
     */
    @Override
    public String toString() {
        return "Language Coordinate{" + PrimitiveData.text(this.languageConceptNid)
                + ", patterns: " + PrimitiveData.textList(this.descriptionPatternList.toArray())
                + ", dialect preference: " + PrimitiveData.textList(this.dialectAssemblagePreferenceList.toArray())
                + ", type preference: " + PrimitiveData.textList(this.descriptionTypePreferenceList.toArray())
                + ", module preference: " + PrimitiveData.textList(this.modulePreferenceListForLanguage.toArray()) + '}';
    }

    @Override
    public int[] getDescriptionPatternList() {
        return this.descriptionPatternList.toArray();
    }

    @Override
    public PatternFacade[] getDescriptionPatternFacadePreferenceList() {
        PatternEntity[] patternEntities = new PatternEntity[this.descriptionPatternList.size()];
        for (int i = 0; i < patternEntities.length; i++) {
            patternEntities[i] = Entity.getFast(this.descriptionPatternList.get(i));
        }
        return patternEntities;
    }

    @Override
    public ImmutableList<SemanticEntity> getDescriptionsForComponent(int componentNid) {
        MutableList<SemanticEntity> descriptionList = Lists.mutable.empty();
        for (int descriptionPatternNid : descriptionPatternList.toArray()) {
            PrimitiveData.get().forEachSemanticNidForComponentOfPattern(componentNid, descriptionPatternNid,
                    semanticNid -> descriptionList.add(Entity.getFast(semanticNid)));
        }
        return descriptionList.toImmutable();
    }

    @Override
    public ImmutableList<SemanticEntityVersion> getDescriptionsForComponentOfType(int componentNid,
                                                                                  int descriptionTypeNid,
                                                                                  StampFilter stampFilter) {
        MutableList<SemanticEntityVersion> descriptionList = Lists.mutable.empty();
        for (int descriptionPatternNid : descriptionPatternList.toArray()) {
            PatternEntity descriptionPattern = Entity.getFast(descriptionPatternNid);
            Latest<PatternEntityVersion> descriptionPatternVersion = stampFilter.stampCalculator().latest(descriptionPattern);
            descriptionPatternVersion.ifPresent(patternEntityVersion -> {
                if (patternEntityVersion.stamp().state() == State.ACTIVE) {
                    int typeIndex = patternEntityVersion.indexForMeaning(TinkarTerm.DESCRIPTION_TYPE);
                    PrimitiveData.get().forEachSemanticNidForComponentOfPattern(componentNid, descriptionPatternNid,
                            semanticNid -> {
                                SemanticEntity descriptionSemantic = Entity.getFast(semanticNid);
                                Latest<SemanticEntityVersion> latestDescriptionVersion =
                                        stampFilter.stampCalculator().latest(descriptionSemantic);
                                latestDescriptionVersion.ifPresent(descriptionVersion -> {
                                    Object fieldValue = descriptionVersion.fields().get(typeIndex);
                                    if (fieldValue instanceof EntityFacade entityFacade) {
                                        if (entityFacade.nid() == descriptionTypeNid) {
                                            descriptionList.add(descriptionVersion);
                                        }
                                    }
                                });

                            });
                }
            });
        }
        return descriptionList.toImmutable();
    }
}
