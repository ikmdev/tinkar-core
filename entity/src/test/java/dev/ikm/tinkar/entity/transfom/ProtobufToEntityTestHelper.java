package dev.ikm.tinkar.entity.transfom;

import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import dev.ikm.tinkar.component.Concept;
import dev.ikm.tinkar.schema.PBPublicId;

import java.time.Instant;

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

    public static long nowEpochSeconds() {
        return Instant.now().getEpochSecond();
    }
    public static Timestamp createTimestamp(long epochSeconds) {
        return Timestamp.newBuilder().setSeconds(epochSeconds).build();
    }
    public static Timestamp nowTimestamp() {
        return createTimestamp(nowEpochSeconds());
    }

}
