package org.hl7.tinkar.coordinate.language.calculator;

import org.eclipse.collections.api.list.ImmutableList;
import org.hl7.tinkar.common.id.IntIdList;
import org.hl7.tinkar.coordinate.language.LanguageCoordinateRecord;
import org.hl7.tinkar.entity.SemanticEntity;
import org.hl7.tinkar.entity.SemanticEntityVersion;
import org.hl7.tinkar.coordinate.stamp.calculator.Latest;

import java.util.Optional;

public interface LanguageCalculatorDelegate extends LanguageCalculator {
    LanguageCalculator languageCalculator();

    @Override
    default Optional<String> getRegularDescriptionText(int entityNid) {
        return languageCalculator().getRegularDescriptionText(entityNid);
    }

    @Override
    default ImmutableList<SemanticEntity> getDescriptionsForComponent(int componentNid) {
        return languageCalculator().getDescriptionsForComponent(componentNid);
    }

    @Override
    default ImmutableList<SemanticEntityVersion> getDescriptionsForComponentOfType(int componentNid, int descriptionTypeNid) {
        return languageCalculator().getDescriptionsForComponentOfType(componentNid, descriptionTypeNid);
    }

    @Override
    default Optional<String> getDescriptionTextForComponentOfType(int entityNid, int descriptionTypeNid) {
        return languageCalculator().getDescriptionTextForComponentOfType(entityNid, descriptionTypeNid);
    }

    @Override
    default Optional<String> getAnyName(int componentNid) {
        return languageCalculator().getAnyName(componentNid);
    }

    @Override
    default Optional<String> getUserText() {
        return languageCalculator().getUserText();
    }

    @Override
    default Optional<String> getDescriptionText(int componentNid) {
        return languageCalculator().getDescriptionText(componentNid);
    }

    @Override
    default Optional<String> getTextFromSemanticVersion(SemanticEntityVersion semanticEntityVersion) {
        return languageCalculator().getTextFromSemanticVersion(semanticEntityVersion);
    }

    @Override
    default Latest<SemanticEntityVersion> getSpecifiedDescription(ImmutableList<SemanticEntity> descriptionList) {
        return languageCalculator().getSpecifiedDescription(descriptionList);
    }

    @Override
    default Latest<SemanticEntityVersion> getSpecifiedDescription(ImmutableList<SemanticEntity> descriptionList, IntIdList descriptionTypePriority) {
        return languageCalculator().getSpecifiedDescription(descriptionList, descriptionTypePriority);
    }
}
