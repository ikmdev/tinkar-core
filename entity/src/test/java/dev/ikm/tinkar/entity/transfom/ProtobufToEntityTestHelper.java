package dev.ikm.tinkar.entity.transfom;

import com.google.protobuf.ByteString;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import dev.ikm.tinkar.component.Concept;
import dev.ikm.tinkar.schema.PBPublicId;

public class ProtobufToEntityTestHelper {

    public static PBPublicId createPBPublicId(){
        return PBPublicId.newBuilder().build();
    }
    public static PBPublicId createPBPublicId(Concept concept){
        return createPBPublicId(concept.publicId());
    }
    public static PBPublicId createPBPublicId(PublicId publicId){
        ByteString byteString = ByteString.copyFrom(UuidUtil.getRawBytes(publicId.asUuidList().get(0)));
        return PBPublicId.newBuilder().addId(byteString).build();
    }
}
