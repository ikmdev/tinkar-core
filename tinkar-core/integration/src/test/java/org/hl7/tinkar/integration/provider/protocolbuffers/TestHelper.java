package org.hl7.tinkar.integration.provider.protocolbuffers;

import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.id.PublicIdList;
import org.hl7.tinkar.entity.ConceptEntity;
import org.hl7.tinkar.entity.ConceptEntityVersion;

import java.util.Arrays;

public class TestHelper {

    protected static boolean comparePublicIds(PublicId control, PublicId hypothesis){
        return PublicId.equals(control, hypothesis);
    }

    protected static boolean comparePublicIdList(PublicIdList control, PublicIdList hypothesis){
        for(PublicId publicIdControl : control.toIdArray()){
            boolean result = false;
            for(PublicId publicIdHypothesis : hypothesis.toIdArray()){
                if(comparePublicIds(publicIdControl, publicIdHypothesis)){
                    result = true;
                }
            }
            if(!result){
                return false;
            }
        }
        return true;
    }

    protected static boolean compareConceptEntity(ConceptEntity<ConceptEntityVersion> control, ConceptEntity<ConceptEntityVersion> hypothesis){
        return comparePublicIds(control.publicId(), hypothesis.publicId())
                && control.nid() == hypothesis.nid()
                && control.leastSignificantBits() == hypothesis.leastSignificantBits()
                && control.mostSignificantBits() == hypothesis.mostSignificantBits()
                && Arrays.equals(control.additionalUuidLongs(), hypothesis.additionalUuidLongs());
    }

}
