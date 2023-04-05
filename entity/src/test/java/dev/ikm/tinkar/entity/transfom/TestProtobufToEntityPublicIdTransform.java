package dev.ikm.tinkar.entity.transfom;

import com.google.protobuf.ByteString;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.id.PublicIds;
import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import dev.ikm.tinkar.schema.PBPublicId;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestProtobufToEntityPublicIdTransform extends AbstractTestProtobufTransform {

    @BeforeAll
    public void init() {
        super.init();
    }

    /**
     * Testing the transformation of an empty Public ID Protobuf object to an Entity
     */
    @Test
    public void publicIdTransformWithNoUUID() {
        // Given a PBPublic ID with no UUID
        PBPublicId pbPublicId = PBPublicId.newBuilder().build();

        // When I try to transform it into a public ID protobuf message

        // Then we will throw a Runtime exception
        assertThrows(Throwable.class, () -> ProtobufTransformer.transformPublicId(pbPublicId), "Not allowed to have empty UUID");
    }

    /**
     * Testing the transformation of a Public ID Protobuf object to an Entity
     */
    @Test
    public void publicIdTransformWithSingleUUID() {
        // Given a PBPublic ID with one UUID
        PublicId expectedPublicId = testConcept.publicId();
        ByteString byteString = ByteString.copyFrom(UuidUtil.getRawBytes(expectedPublicId.asUuidList().get(0)));
        PBPublicId pbPublicId = PBPublicId.newBuilder().addId(byteString).build();

        // When I try to transform it into a public ID protobuf message
        PublicId actualPublicId = ProtobufTransformer.transformPublicId(pbPublicId);

        // Then we will check to verify that the transformed UUID (public ID) matches that of the original.
        assertEquals(actualPublicId, expectedPublicId, "Public ID's do not match.");
        assertEquals(actualPublicId.publicIdHash(), expectedPublicId.publicIdHash(), "Public ID's do not match.");
        assertEquals(actualPublicId.idString(), expectedPublicId.idString(), "Public ID's do not match.");
        assertEquals(actualPublicId.asUuidList().get(0), expectedPublicId.asUuidList().get(0), "Public ID's do not match.");
    }

    /**
     * Testing the transformation of two Public ID Protobuf objects to Entities
     */
    @Test
    public void publicIdTransformWithTwoUUIDS() {
        // Given two PBPublic ID with two UUID
        PublicId actualOnePublicId = testConcept.publicId();
        PublicId actualTwoPublicId = testConcept.publicId();
        PublicId expectedCombinedSource = PublicIds.of(actualOnePublicId.asUuidList().get(0), actualTwoPublicId.asUuidList().get(0));
        ByteString byteStringOne = ByteString.copyFrom(UuidUtil.getRawBytes(actualOnePublicId.asUuidList().get(0)));
        ByteString byteStringTwo = ByteString.copyFrom(UuidUtil.getRawBytes(actualTwoPublicId.asUuidList().get(0)));
        PBPublicId pbPublicId = PBPublicId.newBuilder().addId(byteStringOne).addId(byteStringTwo).build();

        // When I try to transform them into a public ID protobuf message
        PublicId actualPublicId = ProtobufTransformer.transformPublicId(pbPublicId);

        // Then we will check to verify that the transformed UUIDs (public ID) matches that of the original.
        assertEquals(expectedCombinedSource, actualPublicId, "Public ID's do not match.");
        assertEquals(expectedCombinedSource.publicIdHash(), actualPublicId.publicIdHash(), "Public ID's hashes do not match.");
        assertEquals(expectedCombinedSource.idString(), actualPublicId.idString(), "Public ID's ID string do not match.");
        assertEquals(2, actualPublicId.asUuidList().size(), "Public ID's size do not match.");
        assertEquals(expectedCombinedSource.asUuidList().get(0), actualPublicId.asUuidList().get(0), "Public ID's UUID lists from index 0 do not match.");
        assertEquals(expectedCombinedSource.asUuidList().get(1), actualPublicId.asUuidList().get(1), "Public ID's UUID lists do not match from index 1.");
    }
}
