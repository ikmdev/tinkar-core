package org.hl7.tinkar.entity.transfom;

public class EntityTransformFactory {
    public static EntityTransform getTransform(TransformDataType source, TransformDataType target){
        if(source == TransformDataType.PROTOCOL_BUFFERS && target == TransformDataType.ENTITY){
            return new ProtocolBuffersToEntityTransform();
        }else if(source == TransformDataType.ENTITY && target == TransformDataType.PROTOCOL_BUFFERS){
            return new EntityToProtocolBuffersTransform();
        }else {
            throw new UnsupportedOperationException();
        }
    }
}
