package org.hl7.tinkar.provider.entity.internal;

import org.hl7.tinkar.component.Chronology;
import org.hl7.tinkar.component.ConceptChronology;
import org.hl7.tinkar.component.TypePatternChronology;
import org.hl7.tinkar.component.SemanticChronology;
import org.hl7.tinkar.entity.ConceptEntity;
import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.entity.TypePatternEntity;
import org.hl7.tinkar.entity.SemanticEntity;

public class Put {

    public static void dto(Chronology chronology) {
        if (chronology instanceof SemanticChronology semanticChronology) {
            SemanticEntity semanticEntity = SemanticEntity.make(semanticChronology);
            Put.entity(semanticEntity);
        } else if (chronology instanceof ConceptChronology conceptChronology) {
            ConceptEntity conceptEntity = ConceptEntity.make(conceptChronology);
            Put.entity(conceptEntity);
        } else if (chronology instanceof TypePatternChronology definitionhronology) {
            TypePatternEntity definitionEntity = TypePatternEntity.make(definitionhronology);
            Put.entity(definitionEntity);
        } else {
            throw new UnsupportedOperationException("Can't handle instance: " + chronology);
        }
    }

    public static void entity(Entity entity) {
        Get.dataService();
    }

}
