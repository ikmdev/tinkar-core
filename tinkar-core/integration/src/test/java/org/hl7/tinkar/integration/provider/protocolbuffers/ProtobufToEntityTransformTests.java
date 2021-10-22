package org.hl7.tinkar.integration.provider.protocolbuffers;

import com.google.protobuf.ByteString;
import org.hl7.tinkar.common.id.PublicId;
import org.hl7.tinkar.common.id.PublicIds;
import org.hl7.tinkar.common.util.uuid.UuidUtil;
import org.hl7.tinkar.entity.transfom.ProtocolBuffersToEntityTransform;
import org.hl7.tinkar.protobuf.PBConcept;
import org.hl7.tinkar.protobuf.PBPublicId;
import org.hl7.tinkar.protobuf.PBPublicIdList;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class ProtobufToEntityTransformTests {

    private static Logger LOG = Logger.getLogger(ProtobufToEntityTransformTests.class.getName());
    private final ProtocolBuffersToEntityTransform transformer = new ProtocolBuffersToEntityTransform();

    @BeforeTest
    public void beforeTest(){
        LOG.info("Before Test: " + this.getClass().getSimpleName());
    }

    @AfterTest
    public void afterTest() {
        LOG.info("After Test: " + this.getClass().getSimpleName());
    }

    @Test
    public void transformSinglePublicId(){
        UUID uuid1 = UUID.randomUUID();
        byte[] uuidBytes = UuidUtil.getRawBytes(uuid1);
        ByteString byteString = ByteString.copyFrom(uuidBytes);
        PBPublicId pbPublicId = PBPublicId.newBuilder()
                .addId(byteString)
                .build();

        assert TestHelper.comparePublicIds(
                PublicIds.of(uuid1),
                transformer.createPublicId(pbPublicId));
    }

    @Test
    public void transformMultiplePublicId(){
        List<ByteString> uuidByteString = new ArrayList<>();
        UUID[] uuids = new UUID[4];
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        UUID uuid3 = UUID.randomUUID();
        UUID uuid4 = UUID.randomUUID();
        uuids[0] = uuid1;
        uuids[1] = uuid2;
        uuids[2] = uuid3;
        uuids[3] = uuid4;
        uuidByteString.add(ByteString.copyFrom(UuidUtil.getRawBytes(uuid1)));
        uuidByteString.add(ByteString.copyFrom(UuidUtil.getRawBytes(uuid2)));
        uuidByteString.add(ByteString.copyFrom(UuidUtil.getRawBytes(uuid3)));
        uuidByteString.add(ByteString.copyFrom(UuidUtil.getRawBytes(uuid4)));
        PBPublicId pbPublicId = PBPublicId.newBuilder()
                .addAllId(uuidByteString)
                .build();

        assert TestHelper.comparePublicIds(
                PublicIds.of(uuids),
                transformer.createPublicId(pbPublicId));
    }

    @Test(enabled = true)
    public void transformPublicIdList(){
        List<PublicId> publicIds = new ArrayList<>();
        UUID uuid1 = UUID.randomUUID();
        byte[] uuidBytes1 = UuidUtil.getRawBytes(uuid1);
        ByteString byteString1 = ByteString.copyFrom(uuidBytes1);
        PBPublicId pbPublicId1 = PBPublicId.newBuilder()
                .addId(byteString1)
                .build();
        publicIds.add(PublicIds.of(uuid1));

        UUID uuid2 = UUID.randomUUID();
        byte[] uuidBytes2 = UuidUtil.getRawBytes(uuid2);
        ByteString byteString2 = ByteString.copyFrom(uuidBytes2);
        PBPublicId pbPublicId2 = PBPublicId.newBuilder()
                .addId(byteString2)
                .build();
        publicIds.add(PublicIds.of(uuid2));

        UUID uuid3 = UUID.randomUUID();
        byte[] uuidBytes3 = UuidUtil.getRawBytes(uuid3);
        ByteString byteString3 = ByteString.copyFrom(uuidBytes3);
        PBPublicId pbPublicId3 = PBPublicId.newBuilder()
                .addId(byteString3)
                .build();
        publicIds.add(PublicIds.of(uuid3));

        PBPublicIdList pbPublicIdList = PBPublicIdList.newBuilder()
                .addPublicIds(pbPublicId1)
                .addPublicIds(pbPublicId2)
                .addPublicIds(pbPublicId3)
                .build();

        assert TestHelper.comparePublicIdList(
                PublicIds.list.of(PublicIds.of(uuid1), PublicIds.of(uuid2)),
                transformer.createPublicIdList(pbPublicIdList));
    }

    @Test(enabled = false)
    public void transformConceptSingleUUID(){
        UUID uuid = UUID.randomUUID();
        int nid = -123456;
        UUID uuid1 = UUID.randomUUID();
        byte[] uuidBytes = UuidUtil.getRawBytes(uuid1);
        ByteString byteString = ByteString.copyFrom(uuidBytes);
        PBPublicId pbPublicId = PBPublicId.newBuilder()
                .addId(byteString)
                .build();

//        ConceptEntity<ConceptEntityVersion> controlConcept = .builder()
//                .leastSignificantBits(uuid.getLeastSignificantBits())
//                .mostSignificantBits(uuid.getMostSignificantBits())
//                .nid(nid)
//                .build();

        PBConcept hypothesisConcept = PBConcept.newBuilder()
                .setPublicId(pbPublicId)
                .build();

//        assert  TestHelper.compareConceptEntity(
//                controlConcept,
//                transformer.createConceptEntity(hypothesisConcept));
    }

    @Test
    public void transformConceptMultipleUUID(){
        List<ByteString> uuidByteString = new ArrayList<>();
        int nid = -123456;
        UUID[] uuids = new UUID[3];
        UUID uuid1 = UUID.randomUUID();
        UUID uuid2 = UUID.randomUUID();
        UUID uuid3 = UUID.randomUUID();
        UUID uuid4 = UUID.randomUUID();
        uuids[0] = uuid2;
        uuids[1] = uuid3;
        uuids[2] = uuid4;
        uuidByteString.add(ByteString.copyFrom(UuidUtil.getRawBytes(uuid1)));
        uuidByteString.add(ByteString.copyFrom(UuidUtil.getRawBytes(uuid2)));
        uuidByteString.add(ByteString.copyFrom(UuidUtil.getRawBytes(uuid3)));
        uuidByteString.add(ByteString.copyFrom(UuidUtil.getRawBytes(uuid4)));

        PBPublicId pbPublicId = PBPublicId.newBuilder()
                .addAllId(uuidByteString)
                .build();

//        ConceptEntity<ConceptEntityVersion> controlConcept = ConceptRecordBuilder.builder()
//                .leastSignificantBits(uuid1.getLeastSignificantBits())
//                .mostSignificantBits(uuid1.getMostSignificantBits())
//                .additionalUuidLongs(UuidUtil.asArray(Arrays.copyOfRange(conceptPublicId.asUuidArray(),
//                        1, conceptPublicId.uuidCount())))
//                .nid(nid)
//                .build();
    }
}
