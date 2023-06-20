package dev.ikm.tinkar.entity.transfom;

import com.google.protobuf.ByteString;
import dev.ikm.tinkar.common.id.PublicId;
import dev.ikm.tinkar.common.util.uuid.UuidUtil;
import dev.ikm.tinkar.component.Concept;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static dev.ikm.tinkar.entity.transfom.ProtobufToEntityTestHelper.TEST_CONCEPT_NAME;
import static dev.ikm.tinkar.entity.transfom.ProtobufToEntityTestHelper.openSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestEntityToProtobufPublicIdTransform {

    @Test
    @DisplayName("Transform a Entity Public ID into a Protobuf message with no Public ID.")
    public void publicIdEntityTransformWithNoPublicID() {
        openSession(this, (mockedEntityService, conceptMap) -> {

            // Given a Public ID with no public id
            PublicId emptyPublicId = null;

            // When I try to transform it into a public ID message

            // Then we will throw a Runtime exception
            assertThrows(Throwable.class, () -> EntityToTinkarSchemaTransformer.getInstance().createPBPublicId(emptyPublicId), "Not allowed to have empty UUID");
        });
    }

    @Test
    @DisplayName("Transform a Entity Public ID into a Protobuf message with one Public ID.")
    public void publicIdEntityTransformWithAPublicId() {
        // Given a Public ID with a UUID
        openSession(this, (mockedEntityService, conceptMap) -> {

            // Given a Public ID with one public id
            Concept testConcept = conceptMap.get(TEST_CONCEPT_NAME);
            PublicId actualPublicId = testConcept.publicId();
            //Creating a Protobuf with the Expected value
            ByteString byteString = ByteString.copyFrom(UuidUtil.getRawBytes(actualPublicId.asUuidList().get(0)));
            dev.ikm.tinkar.schema.PublicId expectedPBPublicId = dev.ikm.tinkar.schema.PublicId.newBuilder().addId(byteString).build();

            // When I try to transform it into a public ID protobuf message
            dev.ikm.tinkar.schema.PublicId actualPBPublicId = EntityToTinkarSchemaTransformer.getInstance().createPBPublicId(actualPublicId);

            // Then we will check to verify that the transformed public ID matches that of the original.
            assertEquals(expectedPBPublicId, actualPBPublicId, "Protobuf Public ID's do not match.");
            assertEquals(expectedPBPublicId.hashCode(), actualPBPublicId.hashCode(), "Protobuf Public ID's hash codes not match.");
            assertEquals(expectedPBPublicId.getIdList(), actualPBPublicId.getIdList(), "Protobuf Public ID's lists not match.");
        });
    }

    //TODO: Finish unit testing coverage here
    @Test
    @Disabled
    @DisplayName("Transform a Entity Public ID into a Protobuf message with a list of public ID's.")
    public void publicIdEntityTransformWithPublicIDList() {
        // Given two Public ID's

        // When I try to transform it into a public ID protobuf message

        // Then we will check to verify that the transformed UUID (public ID) matches that of the original.
    }
}
