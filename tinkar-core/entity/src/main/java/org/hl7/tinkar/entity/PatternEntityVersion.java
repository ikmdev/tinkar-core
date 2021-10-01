package org.hl7.tinkar.entity;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.component.Concept;
import org.hl7.tinkar.component.PatternVersion;
import org.hl7.tinkar.terms.ConceptFacade;

public interface PatternEntityVersion extends EntityVersion, PatternVersion<FieldDefinitionForEntity> {
    int semanticPurposeNid();

    int semanticMeaningNid();

    default <T> T getFieldWithMeaning(ConceptFacade fieldMeaning, SemanticEntityVersion version) {
        return (T) version.fields().get(indexForMeaning(fieldMeaning));
    }

    @Override
    ImmutableList<FieldDefinitionForEntity> fieldDefinitions();

    default <T> T getFieldWithPurpose(ConceptFacade fieldPurpose, SemanticEntityVersion version) {
        return (T) version.fields().get(indexForPurpose(fieldPurpose));
    }

    // TODO: should allow more than one index for purpose?
    // TODO: Note the stamp calculator caches these indexes. Consider how to optimize, and eliminate unoptimized calls?
    default int indexForPurpose(int purposeNid) {
        for (int i = 0; i < fieldDefinitions().size(); i++) {
            if (fieldDefinitions().get(i).purposeNid == purposeNid) {
                return i;
            }
        }
        return -1;
    }

    // TODO: should allow more than one index for meaning?
    // TODO: Note the stamp calculator caches these indexes. Consider how to optimize, and eliminate unoptimized calls?
    default int indexForMeaning(int meaningNid) {
        for (int i = 0; i < fieldDefinitions().size(); i++) {
            if (fieldDefinitions().get(i).meaningNid == meaningNid) {
                return i;
            }
        }
        return -1;
    }

    @Override
    default ConceptEntity semanticPurpose() {
        return EntityService.get().getEntityFast(semanticPurposeNid());
    }

    @Override
    default ConceptEntity semanticMeaning() {
        return EntityService.get().getEntityFast(semanticMeaningNid());
    }

     default int indexForMeaning(ConceptFacade meaning) {
        return indexForMeaning(meaning.nid());
    }

    default int indexForPurpose(ConceptFacade purpose) {
        return indexForPurpose(purpose.nid());
    }

}
