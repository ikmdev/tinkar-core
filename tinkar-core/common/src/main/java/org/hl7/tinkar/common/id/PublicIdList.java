package org.hl7.tinkar.common.id;

public interface PublicIdList<E extends PublicId> extends PublicIdCollection<E>, IdList {

    static boolean equals(PublicIdList publicIdListOne, PublicIdList publicIdListTwo){
        for(PublicId publicIdOne : publicIdListOne.toIdArray()){
            boolean result = false;
            for(PublicId publicIdTwo : publicIdListTwo.toIdArray()){
                if(PublicId.equals(publicIdOne, publicIdTwo)){
                    result = true;
                }
            }
            if(!result){
                return false;
            }
        }
        return true;
    }

}
