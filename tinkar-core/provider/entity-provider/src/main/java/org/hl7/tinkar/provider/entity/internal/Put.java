package org.hl7.tinkar.provider.entity.internal;

import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.component.ConceptChronology;
import org.hl7.tinkar.component.PatternForSemanticChronology;
import org.hl7.tinkar.component.SemanticChronology;
import org.hl7.tinkar.entity.ConceptEntity;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.PatternForSemanticEntity;
import org.hl7.tinkar.entity.SemanticEntity;

public class Put {

    public static void dto(Chronology chronology) {
        if (chronology instanceof SemanticChronology semanticChronology) {
            SemanticEntity semanticEntity = SemanticEntity.make(semanticChronology);
            Put.entity(semanticEntity);
        } else if (chronology instanceof ConceptChronology conceptChronology) {
            ConceptEntity conceptEntity = ConceptEntity.make(conceptChronology);
            Put.entity(conceptEntity);
        } else if (chronology instanceof PatternForSemanticChronology definitionForSemanticChronology) {
            PatternForSemanticEntity definitionForSemanticEntity = PatternForSemanticEntity.make(definitionForSemanticChronology);
            Put.entity(definitionForSemanticEntity);
        } else {
            throw new UnsupportedOperationException("Can't handle instance: " + chronology);
        }
    }

    public static void entity(Entity entity) {
        Get.dataService();
    }

}
