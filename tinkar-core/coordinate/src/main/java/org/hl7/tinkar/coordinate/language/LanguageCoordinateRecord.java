package org.hl7.tinkar.coordinate.language;

import java.util.Objects;

import io.soabase.recordbuilder.core.RecordBuilder;
import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.binary.Decoder;
import org.hl7.tinkar.common.binary.DecoderInput;
import org.hl7.tinkar.common.binary.Encoder;
import org.hl7.tinkar.common.binary.EncoderOutput;
import org.hl7.tinkar.common.id.IntIdList;
import org.hl7.tinkar.common.id.IntIds;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.coordinate.ImmutableCoordinate;
import org.hl7.tinkar.entity.*;
import org.hl7.tinkar.terms.ConceptFacade;
import org.hl7.tinkar.terms.PatternFacade;

@RecordBuilder
public final record LanguageCoordinateRecord(int languageConceptNid,
                                             IntIdList descriptionPatternNidList,
                                             IntIdList descriptionTypePreferenceNidList,
                                             IntIdList dialectPatternPreferenceNidList,
                                             IntIdList modulePreferenceNidListForLanguage)
        implements LanguageCoordinate, ImmutableCoordinate, LanguageCoordinateRecordBuilder.With {

    private static final int marshalVersion = 1;

    public LanguageCoordinateRecord(DecoderInput in) {
        this(in.readNid(), IntIds.list.of(in.readNidArray()), IntIds.list.of(in.readNidArray()), IntIds.list.of(in.readNidArray()),
                IntIds.list.of(in.readNidArray()));
    }

    @Override
    @Encoder
    public void encode(EncoderOutput out) {
        out.writeNid(this.languageConceptNid);
        out.writeNidArray(this.descriptionTypePreferenceNidList.toArray());
        out.writeNidArray(this.dialectPatternPreferenceNidList.toArray());
        out.writeNidArray(this.modulePreferenceNidListForLanguage.toArray());
    }

    @Decoder
    public static LanguageCoordinateRecord decode(DecoderInput in) {
        int objectMarshalVersion = in.encodingFormatVersion();
        switch (objectMarshalVersion) {
            case marshalVersion:
                return new LanguageCoordinateRecord(in);
            default:
                throw new UnsupportedOperationException("Unsupported version: " + objectMarshalVersion);
        }
    }

    public static LanguageCoordinateRecord make(ConceptFacade languageConcept,
                                                IntIdList descriptionPatternList,
                                                IntIdList descriptionTypePreferenceList,
                                                IntIdList dialectAssemblagePreferenceList,
                                                IntIdList modulePreferenceListForLanguage) {
        return new LanguageCoordinateRecord(languageConcept.nid(),
                descriptionPatternList,
                descriptionTypePreferenceList, dialectAssemblagePreferenceList,
                modulePreferenceListForLanguage);
    }

    public static LanguageCoordinateRecord make(ConceptFacade languageConcept,
                                                PatternFacade patternFacade,
                                                IntIdList descriptionTypePreferenceList,
                                                IntIdList dialectAssemblagePreferenceList,
                                                IntIdList modulePreferenceListForLanguage) {
        return new LanguageCoordinateRecord(languageConcept.nid(),
                IntIds.list.of(patternFacade.nid()),
                descriptionTypePreferenceList, dialectAssemblagePreferenceList,
                modulePreferenceListForLanguage);
    }

    /**
     * @param languageConceptNid
     * @param descriptionPatternNidList
     * @param descriptionTypePreferenceNidList
     * @param dialectPatternPreferenceNidList
     * @param modulePreferenceNidListForLanguage
     * @return
     */
    public static LanguageCoordinateRecord make(int languageConceptNid,
                                                IntIdList descriptionPatternNidList,
                                                IntIdList descriptionTypePreferenceNidList,
                                                IntIdList dialectPatternPreferenceNidList,
                                                IntIdList modulePreferenceNidListForLanguage) {

        return new LanguageCoordinateRecord(languageConceptNid,
                descriptionPatternNidList,
                descriptionTypePreferenceNidList,
                dialectPatternPreferenceNidList,
                modulePreferenceNidListForLanguage);
    }

    @Override
    public ImmutableList<ConceptFacade> descriptionTypePreferenceList() {
        return this.descriptionTypePreferenceNidList.map(ConceptFacade::make);
    }

    @Override
    public ImmutableList<PatternFacade> dialectPatternPreferenceList() {
        return this.dialectPatternPreferenceNidList.map(PatternFacade::make);
    }

    @Override
    public ImmutableList<ConceptFacade> modulePreferenceListForLanguage() {
        return this.modulePreferenceNidListForLanguage.map(ConceptFacade::make);
    }

    @Override
    public int languageConceptNid() {
        return this.languageConceptNid;
    }

    @Override
    public org.hl7.tinkar.terms.ConceptFacade languageConcept() {
        return Entity.getFast(this.languageConceptNid);
    }

    @Override
    public LanguageCoordinateRecord toLanguageCoordinateRecord() {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LanguageCoordinateRecord that)) return false;
        return languageConceptNid() == that.languageConceptNid() &&
                descriptionTypePreferenceNidList().equals(that.descriptionTypePreferenceNidList()) &&
                dialectPatternPreferenceNidList().equals(that.dialectPatternPreferenceNidList()) &&
                modulePreferenceNidListForLanguage().equals(that.modulePreferenceNidListForLanguage());
    }

    @Override
    public IntIdList descriptionPatternPreferenceNidList() {
        return this.descriptionPatternNidList;
    }

    @Override
    public PatternFacade[] descriptionPatternPreferenceArray() {
        PatternEntity[] patternEntities = new PatternEntity[this.descriptionPatternNidList.size()];
        for (int i = 0; i < patternEntities.length; i++) {
            patternEntities[i] = Entity.getFast(this.descriptionPatternNidList.get(i));
        }
        return patternEntities;
    }

    @Override
    public int hashCode() {
        return Objects.hash(languageConceptNid(), descriptionTypePreferenceNidList(), dialectPatternPreferenceNidList(), modulePreferenceNidListForLanguage());
    }

    /**
     * To string.
     *
     * @return the string
     */
    @Override
    public String toString() {
        return "Language Coordinate{" + PrimitiveData.text(this.languageConceptNid)
                + ", patterns: " + PrimitiveData.textList(this.descriptionPatternNidList.toArray())
                + ", dialect preference: " + PrimitiveData.textList(this.dialectPatternPreferenceNidList.toArray())
                + ", type preference: " + PrimitiveData.textList(this.descriptionTypePreferenceNidList.toArray())
                + ", module preference: " + PrimitiveData.textList(this.modulePreferenceNidListForLanguage.toArray()) + '}';
    }

}
