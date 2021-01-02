package org.hl7.tinkar.provider.entity.internal;

import org.hl7.tinkar.component.*;
import org.hl7.tinkar.entity.ConceptEntity;
import org.hl7.tinkar.entity.DefinitionForSemanticEntity;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.SemanticEntity;

public class Put {

    public static void dto(Chronology chronology) {
        if (chronology instanceof SemanticChronology) {
            SemanticChronology semanticChronology = (SemanticChronology) chronology;
            SemanticEntity semanticEntity = SemanticEntity.make(semanticChronology);
            Put.entity(semanticEntity);
        } else if (chronology instanceof ConceptChronology) {
            ConceptChronology conceptChronology = (ConceptChronology) chronology;
            ConceptEntity conceptEntity = ConceptEntity.make(conceptChronology);
            Put.entity(conceptEntity);
        } else if (chronology instanceof DefinitionForSemanticChronology) {
            DefinitionForSemanticChronology definitionForSemanticChronology = (DefinitionForSemanticChronology) chronology;
            DefinitionForSemanticEntity definitionForSemanticEntity = DefinitionForSemanticEntity.make(definitionForSemanticChronology);
            Put.entity(definitionForSemanticEntity);
        } else {
            throw new UnsupportedOperationException("Can't handle instance: " + chronology);
        }
    }

    public static void entity(Entity entity) {
        Get.dataService();
    }

}
