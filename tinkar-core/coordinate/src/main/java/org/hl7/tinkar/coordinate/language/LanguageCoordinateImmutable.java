package org.hl7.tinkar.coordinate.language;

import java.util.Objects;
import java.util.Optional;

import com.github.benmanes.caffeine.cache.Cache;
import com.google.auto.service.AutoService;
import org.eclipse.collections.api.list.primitive.ImmutableIntList;
import org.eclipse.collections.impl.factory.primitive.IntLists;
import org.hl7.tinkar.collection.ConcurrentReferenceHashMap;
import org.hl7.tinkar.common.binary.Decoder;
import org.hl7.tinkar.common.binary.DecoderInput;
import org.hl7.tinkar.common.binary.Encoder;
import org.hl7.tinkar.common.binary.EncoderOutput;
import org.hl7.tinkar.common.service.CachingService;
import org.hl7.tinkar.coordinate.ImmutableCoordinate;
import org.hl7.tinkar.coordinate.stamp.StampFilter;
import org.hl7.tinkar.coordinate.stamp.StampFilterImmutable;
import org.hl7.tinkar.entity.ConceptEntity;
import org.hl7.tinkar.entity.DefaultDescriptionText;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.component.Concept;

//This class is not treated as a service, however, it needs the annotation, so that the reset() gets fired at appropriate times.
@AutoService(CachingService.class)
public final class LanguageCoordinateImmutable implements LanguageCoordinate, ImmutableCoordinate, /*ChronologyChangeListener,*/ CachingService {

    private static final ConcurrentReferenceHashMap<LanguageCoordinateImmutable, LanguageCoordinateImmutable> SINGLETONS =
            new ConcurrentReferenceHashMap<>(ConcurrentReferenceHashMap.ReferenceType.WEAK,
                    ConcurrentReferenceHashMap.ReferenceType.WEAK);

    private static final int marshalVersion = 1;
    public static final String UNKNOWN_COMPONENT_TYPE = "Unknown component type";

    final private int languageConceptNid;
    final private ImmutableIntList descriptionTypePreferenceList;
    final private ImmutableIntList dialectAssemblagePreferenceList;
    final private ImmutableIntList modulePreferenceListForLanguage;

    private ConcurrentReferenceHashMap<StampFilterImmutable, Cache<Integer, String>> preferredCaches;

    private ConcurrentReferenceHashMap<StampFilterImmutable, Cache<Integer, String>> fqnCaches;

    private ConcurrentReferenceHashMap<StampFilterImmutable, Cache<Integer, String>> descriptionCaches;

    private LanguageCoordinateImmutable(ConceptEntity languageConcept,
                                        ImmutableIntList descriptionTypePreferenceList,
                                        ImmutableIntList dialectAssemblagePreferenceList,
                                        ImmutableIntList modulePreferenceListForLanguage) {
        this(languageConcept.nid(), descriptionTypePreferenceList, dialectAssemblagePreferenceList,
                modulePreferenceListForLanguage);
    }

    private LanguageCoordinateImmutable(int languageConceptNid,
                                       ImmutableIntList descriptionTypePreferenceList,
                                       ImmutableIntList dialectAssemblagePreferenceList,
                                       ImmutableIntList modulePreferenceListForLanguage) {
        this.languageConceptNid = languageConceptNid;
        this.descriptionTypePreferenceList = descriptionTypePreferenceList;
        this.dialectAssemblagePreferenceList = dialectAssemblagePreferenceList == null ? IntLists.immutable.empty() : dialectAssemblagePreferenceList;
        this.modulePreferenceListForLanguage = modulePreferenceListForLanguage == null ? IntLists.immutable.empty() : modulePreferenceListForLanguage;
    }
    
    private LanguageCoordinateImmutable() {
        // No arg constructor for HK2 managed instance
        // This instance just enables reset functionality...
        this.languageConceptNid = Integer.MAX_VALUE;
        this.descriptionTypePreferenceList = null;
        this.dialectAssemblagePreferenceList = null;
        this.modulePreferenceListForLanguage = null;
    }

    private LanguageCoordinateImmutable setupCache() {
        this.preferredCaches =
                new ConcurrentReferenceHashMap<>(ConcurrentReferenceHashMap.ReferenceType.WEAK,
                        ConcurrentReferenceHashMap.ReferenceType.WEAK);

        this.fqnCaches =
                new ConcurrentReferenceHashMap<>(ConcurrentReferenceHashMap.ReferenceType.WEAK,
                        ConcurrentReferenceHashMap.ReferenceType.WEAK);

        this.descriptionCaches =
                new ConcurrentReferenceHashMap<>(ConcurrentReferenceHashMap.ReferenceType.WEAK,
                        ConcurrentReferenceHashMap.ReferenceType.WEAK);

        throw new UnsupportedOperationException();
//        Get.commitService().addChangeListener(this);
//        return this;
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

    @Override
    public void reset() {
        SINGLETONS.clear();
    }

    public LanguageCoordinateImmutable(DecoderInput in) {
        this(in.readNid(), IntLists.immutable.of(in.readNidArray()), IntLists.immutable.of(in.readNidArray()),
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

    public static LanguageCoordinateImmutable make(ConceptEntity languageConcept,
                                                   ImmutableIntList descriptionTypePreferenceList,
                                                   ImmutableIntList dialectAssemblagePreferenceList,
                                                   ImmutableIntList modulePreferenceListForLanguage)  {
        return SINGLETONS.computeIfAbsent(new LanguageCoordinateImmutable(languageConcept.nid(),
                        descriptionTypePreferenceList, dialectAssemblagePreferenceList,
                        modulePreferenceListForLanguage),
                languageCoordinateImmutable -> languageCoordinateImmutable.setupCache());
    }

    /**
     * 
     * @param languageConceptNid
     * @param descriptionTypePreferenceList
     * @param dialectAssemblagePreferenceList
     * @param modulePreferenceListForLanguage - if null, treated as empty
     * @return
     */
    public static LanguageCoordinateImmutable make(int languageConceptNid,
                                                   ImmutableIntList descriptionTypePreferenceList,
                                                   ImmutableIntList dialectAssemblagePreferenceList,
                                                   ImmutableIntList modulePreferenceListForLanguage)  {
        return SINGLETONS.computeIfAbsent(new LanguageCoordinateImmutable(languageConceptNid,
                        descriptionTypePreferenceList, dialectAssemblagePreferenceList,
                        modulePreferenceListForLanguage),
                languageCoordinateImmutable -> languageCoordinateImmutable.setupCache());
    }


    @Override
    public int[] getDescriptionTypePreferenceList() {
        return this.descriptionTypePreferenceList.toArray();
    }

    @Override
    public Concept[] getDescriptionTypeSpecPreferenceList() {
        return this.descriptionTypePreferenceList.collect(nid ->
                Entity.getFast(nid)).toArray(new Concept[this.descriptionTypePreferenceList.size()]);
    }

    @Override
    public int[] getDialectAssemblagePreferenceList() {
        return this.dialectAssemblagePreferenceList.toArray();
    }

    @Override
    public Concept[] getDialectAssemblageSpecPreferenceList() {
         return this.dialectAssemblagePreferenceList.collect(nid ->
                Entity.getFast(nid)).toArray(new Concept[this.dialectAssemblagePreferenceList.size()]);
    }

    @Override
    public int[] getModulePreferenceListForLanguage() {
        return this.modulePreferenceListForLanguage.toArray();
    }

    @Override
    public Concept[] getModuleSpecPreferenceListForLanguage() {
        return this.modulePreferenceListForLanguage.collect(nid ->
                Entity.getFast(nid)).toArray(new Concept[this.modulePreferenceListForLanguage.size()]);
    }

    @Override
    public int getLanguageConceptNid() {
        return this.languageConceptNid;
    }

    @Override
    public Concept getLanguageConcept() {
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
                getDialectAssemblagePreferenceList().equals(that.getDialectAssemblagePreferenceList()) &&
                getModulePreferenceListForLanguage().equals(that.getModulePreferenceListForLanguage());
    }

    @Override
    public int hashCode() {
        return Objects.hash(getLanguageConceptNid(), getDescriptionTypePreferenceList(), getDialectAssemblagePreferenceList(), getModulePreferenceListForLanguage());
    }
    @Override
    public Optional<String> getDescriptionText(int componentNid, StampFilter stampFilter) {
        throw new UnsupportedOperationException();
//        Cache<Integer, String> descriptionCache = this.descriptionCaches.computeIfAbsent(stampFilter.toStampFilterImmutable(),
//                stampFilterImmutable -> Caffeine.newBuilder().maximumSize(100000).build());
//        String descriptionText = descriptionCache.getIfPresent(componentNid);
//        if (descriptionText == null) {
//            descriptionText = getDescriptionTextForCache(componentNid, stampFilter);
//            if (descriptionText != null) {
//                descriptionCache.put(componentNid, descriptionText);
//            }
//        }
//        return Optional.ofNullable(descriptionText);
    }
    private String getDescriptionTextForCache(int componentNid, StampFilter stampFilter) {
        throw new UnsupportedOperationException();
//        switch (Get.identifierService().getObjectTypeForComponent(componentNid)) {
//            case CONCEPT: {
//                LatestVersion<SemanticEntityVersion> latestDescription
//                        = getDescription(Get.conceptService().getConceptDescriptions(componentNid), stampFilter);
//                return latestDescription.isPresent() ? latestDescription.get().getText() : null;
//            }
//            case SEMANTIC: {
//                return getSemanticString(componentNid, stampFilter);
//            }
//            case UNKNOWN:
//            default:
//                return UNKNOWN_COMPONENT_TYPE;
//        }
    }
    @Override
    public Optional<String> getRegularDescriptionText(int componentNid, StampFilter stampFilter) {
        throw new UnsupportedOperationException();
//        Cache<Integer, String> preferredCache = this.preferredCaches.computeIfAbsent(stampFilter.toStampFilterImmutable(),
//                stampFilterImmutable -> Caffeine.newBuilder().maximumSize(100000).build());
//
//        String preferredDescriptionText = preferredCache.getIfPresent(componentNid);
//        if (preferredDescriptionText == null) {
//            preferredDescriptionText = getPreferredDescriptionTextForCache(componentNid, stampFilter);
//            if (preferredDescriptionText != null) {
//                preferredCache.put(componentNid, preferredDescriptionText);
//            }
//        }
//        return Optional.ofNullable(preferredDescriptionText);
    }
    private String getPreferredDescriptionTextForCache(int componentNid, StampFilter stampFilter) {
        throw new UnsupportedOperationException();
//        switch (Get.identifierService().getObjectTypeForComponent(componentNid)) {
//            case CONCEPT: {
//                LatestVersion<SemanticEntityVersion> latestDescription
//                        = getRegularDescription(Get.conceptService().getConceptDescriptions(componentNid), stampFilter);
//                return latestDescription.isPresent() ? latestDescription.get().getText() : null;
//            }
//            case SEMANTIC: {
//                return getSemanticString(componentNid, stampFilter);
//            }
//            case UNKNOWN:
//            default:
//                return UNKNOWN_COMPONENT_TYPE;
//        }
    }
    @Override
    public Optional<String> getFullyQualifiedNameText(int componentNid, StampFilter stampFilter) {
        throw new UnsupportedOperationException();
//        Cache<Integer, String> fqnCache = this.fqnCaches.computeIfAbsent(stampFilter.toStampFilterImmutable(),
//                stampFilterImmutable -> Caffeine.newBuilder().maximumSize(100000).build());
//
//        String fullyQualifiedNameText = fqnCache.getIfPresent(componentNid);
//        if (fullyQualifiedNameText == null) {
//            fullyQualifiedNameText = getFullyQualifiedNameTextForCache(componentNid, stampFilter);
//            if (fullyQualifiedNameText != null) {
//                fqnCache.put(componentNid, fullyQualifiedNameText);
//            }
//        }
//        return Optional.ofNullable(fullyQualifiedNameText);
    }

    private String getFullyQualifiedNameTextForCache(int componentNid, StampFilter stampFilter) {
        throw new UnsupportedOperationException();
//        switch (Get.identifierService().getObjectTypeForComponent(componentNid)) {
//            case CONCEPT: {
//                LatestVersion<SemanticEntityVersion> latestDescription
//                        = getFullyQualifiedDescription(Get.conceptService().getConceptDescriptions(componentNid), stampFilter);
//                return latestDescription.isPresent() ? latestDescription.get().getText() : null;
//            }
//            case SEMANTIC: {
//                return getSemanticString(componentNid, stampFilter);
//            }
//            case UNKNOWN:
//            default:
//                return UNKNOWN_COMPONENT_TYPE;
//        }
    }

    private String getSemanticString(int componentNid, StampFilter stampFilter) {
        throw new UnsupportedOperationException();
//        SemanticEntity sc = Get.assemblageService().getSemanticEntity(componentNid);
//        if (sc.getVersionType() == VersionType.DESCRIPTION) {
//            LatestVersion<SemanticEntityVersion> latestDescription = sc.getLatestVersion(stampFilter);
//            if (latestDescription.isPresent()) {
//                return latestDescription.get().getText();
//            }
//            return "INACTIVE: " + ((SemanticEntityVersion) sc.getVersionList().get(0)).getText();
//        }
//        return Get.assemblageService().getSemanticEntity(componentNid).getVersionType().toString();
    }

    /**
     * To string.
     *
     * @return the string
     */
    @Override
    public String toString() {
        return "Language Coordinate{" + DefaultDescriptionText.get(this.languageConceptNid)
                + ", dialect preference: " + DefaultDescriptionText.getList(this.dialectAssemblagePreferenceList.toArray())
                + ", type preference: " + DefaultDescriptionText.getList(this.descriptionTypePreferenceList.toArray())
                + ", module preference: " + DefaultDescriptionText.getList(this.modulePreferenceListForLanguage.toArray()) + '}';
    }
}
