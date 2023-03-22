package org.hl7.tinkar.entity;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.component.PatternVersion;
import org.hl7.tinkar.terms.ConceptFacade;

public interface PatternEntityVersion extends EntityVersion, PatternVersion {
    default <T> T getFieldWithMeaning(ConceptFacade fieldMeaning, SemanticEntityVersion version) {
        return (T) version.fieldValues().get(indexForMeaning(fieldMeaning));
    }

    default int indexForMeaning(ConceptFacade meaning) {
        return indexForMeaning(meaning.nid());
    }

    // TODO: should allow more than one index for meaning?
    // TODO: Note the stamp calculator caches these indexes. Consider how to optimize, and eliminate unoptimized calls?
    default int indexForMeaning(int meaningNid) {
        for (int i = 0; i < fieldDefinitions().size(); i++) {
            if (fieldDefinitions().get(i).meaningNid() == meaningNid) {
                return i;
            }
        }
        return -1;
    }

    @Override
    ImmutableList<? extends FieldDefinitionForEntity> fieldDefinitions();

    @Override
    default ConceptEntity semanticPurpose() {
        return EntityService.get().getEntityFast(semanticPurposeNid());
    }

    int semanticPurposeNid();

    @Override
    default ConceptEntity semanticMeaning() {
        return EntityService.get().getEntityFast(semanticMeaningNid());
    }

    int semanticMeaningNid();

    default <T> T getFieldWithPurpose(ConceptFacade fieldPurpose, SemanticEntityVersion version) {
        return (T) version.fieldValues().get(indexForPurpose(fieldPurpose));
    }

    default int indexForPurpose(ConceptFacade purpose) {
        return indexForPurpose(purpose.nid());
    }

    // TODO: should allow more than one index for purpose?
    // TODO: Note the stamp calculator caches these indexes. Consider how to optimize, and eliminate unoptimized calls?
    default int indexForPurpose(int purposeNid) {
        for (int i = 0; i < fieldDefinitions().size(); i++) {
            if (fieldDefinitions().get(i).purposeNid() == purposeNid) {
                return i;
            }
        }
        return -1;
    }

}
