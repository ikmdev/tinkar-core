package org.hl7.tinkar.entity.load;

import org.hl7.tinkar.entity.Entity;
import org.hl7.tinkar.protobuf.PBConcept;
import org.hl7.tinkar.protobuf.PBConceptChronology;
import org.hl7.tinkar.protobuf.PBTinkarMsg;

import java.util.Optional;

public final class PBEntityFactory {

    public static Entity make(PBTinkarMsg pbTinkarMsg){
        Optional<Entity> optionalEntity = Optional.empty();

        try {
            switch (pbTinkarMsg.getValueCase()) {
                case CONCEPTVALUE: //PBConcept ConceptValue = 10;
                    PBConcept pbConcept = pbTinkarMsg.getConceptValue();

                    break;
                case CONCEPTCHRONOLOGYVALUE:
                    break;
                case CONCEPTVERSIONVALUE:
                    break;
                case SEMANTICVALUE:
                    break;
                case SEMANTICCHRONOLOGYVALUE:
                    break;
                case SEMANTICVERSIONVALUE:
                    break;
                case PATTERNVALUE:
                    break;
                case PATTERNCHRONOLOGYVALUE:
                    break;
                case PATTERNVERSIONVALUE:
                    break;
                default:
                    break;
            }
        }catch (Exception e){
            e.printStackTrace();
        }

        return optionalEntity.get();
    }


}
