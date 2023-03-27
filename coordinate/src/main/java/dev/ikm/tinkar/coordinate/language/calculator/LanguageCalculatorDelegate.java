package dev.ikm.tinkar.coordinate.language.calculator;

import dev.ikm.tinkar.coordinate.language.LanguageCoordinateRecord;
import dev.ikm.tinkar.coordinate.stamp.calculator.Latest;
import org.eclipse.collections.api.list.ImmutableList;
import dev.ikm.tinkar.common.id.IntIdList;
import dev.ikm.tinkar.entity.SemanticEntity;
import dev.ikm.tinkar.entity.SemanticEntityVersion;

import java.util.Optional;

public interface LanguageCalculatorDelegate extends LanguageCalculator {
    @Override
    default ImmutableList<LanguageCoordinateRecord> languageCoordinateList() {
        return languageCalculator().languageCoordinateList();
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
    default Optional<String> getRegularDescriptionText(int entityNid) {
        return languageCalculator().getRegularDescriptionText(entityNid);
    }

    @Override
    default Optional<String> getSemanticText(int nid) {
        return languageCalculator().getSemanticText(nid);
    }

    @Override
    default Optional<String> getDescriptionTextForComponentOfType(int entityNid, int descriptionTypeNid) {
        return languageCalculator().getDescriptionTextForComponentOfType(entityNid, descriptionTypeNid);
    }

    @Override
    default Optional<String> getDescriptionText(int componentNid) {
        return languageCalculator().getDescriptionText(componentNid);
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
    default Latest<SemanticEntityVersion> getSpecifiedDescription(ImmutableList<SemanticEntity> descriptionList) {
        return languageCalculator().getSpecifiedDescription(descriptionList);
    }

    @Override
    default Optional<String> getTextFromSemanticVersion(SemanticEntityVersion semanticEntityVersion) {
        return languageCalculator().getTextFromSemanticVersion(semanticEntityVersion);
    }

    @Override
    default Latest<SemanticEntityVersion> getSpecifiedDescription(ImmutableList<SemanticEntity> descriptionList, IntIdList descriptionTypePriority) {
        return languageCalculator().getSpecifiedDescription(descriptionList, descriptionTypePriority);
    }

    LanguageCalculator languageCalculator();
}
