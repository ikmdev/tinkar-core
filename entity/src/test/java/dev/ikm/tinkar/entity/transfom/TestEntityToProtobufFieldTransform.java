package dev.ikm.tinkar.entity.transfom;

import dev.ikm.tinkar.schema.PBField;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import static dev.ikm.tinkar.entity.transfom.ProtobufToEntityTestHelper.openSession;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestEntityToProtobufFieldTransform {

    @Test
    @DisplayName("Transform a Entity Field With a String Value Present")
    public void testProtobufToEntityFieldTransformWithAStringValue() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given a string value object
            String actualStringValue = "Testing Field Transformation with a string.";
            Object actualEntityFieldString = actualStringValue;

            PBField expectedPBFieldString = PBField.newBuilder()
                    .setStringValue(actualStringValue)
                    .build();

            // When we transform our Protobuf String Field value
            PBField actualPBFieldString = EntityToTinkarSchemaTransformer.getInstance().createPBField(actualEntityFieldString);

            // Then the resulting Object should match the original passed in string value.
            assertEquals(expectedPBFieldString.toString(),actualPBFieldString.toString(), "The transformed string value does not match the expected.");
            assertTrue(expectedPBFieldString.equals(actualPBFieldString), "The transformed string object does not match that of the expected.");
        });
    }

    @Test
    @DisplayName("Transform a Entity Field With a Boolean Value Present")
    public void testProtobufToEntityFieldTransformWithABooleanValue() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given a boolean value object
            Boolean actualBooleanValue = false;
            Object actualEntityFieldBoolean = actualBooleanValue;

            PBField expectedPBFieldBoolean= PBField.newBuilder()
                    .setBoolValue(actualBooleanValue)
                    .build();

            // When we transform our Protobuf Boolean Field value
            PBField actualPBFieldBoolean = EntityToTinkarSchemaTransformer.getInstance().createPBField(actualEntityFieldBoolean);

            // Then the resulting Object should match the original passed in boolean value.
            assertEquals(expectedPBFieldBoolean,actualPBFieldBoolean, "The transformed boolean value does not match the expected.");
            assertTrue(expectedPBFieldBoolean.equals(actualPBFieldBoolean), "The transformed boolean object does not match that of the expected.");
        });
    }

    @Test
    @DisplayName("Transform a Entity Field With a Integer Value Present")
    public void testProtobufToEntityFieldTransformWithAIntegerValue() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given a string value object
            Integer actualIntegerValue = 25464564;
            Object actualEntityFieldInteger = actualIntegerValue;

            PBField expectedPBFieldInteger= PBField.newBuilder()
                    .setIntValue(actualIntegerValue)
                    .build();

            // When we transform our Protobuf Integer Field value
            PBField actualPBFieldInteger = EntityToTinkarSchemaTransformer.getInstance().createPBField(actualEntityFieldInteger);

            // Then the resulting Object should match the original passed in integer value.
            assertEquals(expectedPBFieldInteger.toString(),actualPBFieldInteger.toString(), "The transformed integer value does not match the expected.");
            assertTrue(expectedPBFieldInteger.equals(actualPBFieldInteger), "The transformed integer object does not match that of the expected.");
        });
    }

    @Test
    @DisplayName("Transform a Entity Field With a Float Value Present")
    public void testProtobufToEntityFieldTransformWithAFloatValue() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given a float value object
            Float actualFloatValue = 14534.34f;
            Object actualEntityFloatInteger = actualFloatValue;

            PBField expectedPBFieldFloat= PBField.newBuilder()
                    .setFloatValue(actualFloatValue)
                    .build();

            // When we transform our Protobuf Float Field value
            PBField actualPBFieldFloat = EntityToTinkarSchemaTransformer.getInstance().createPBField(actualEntityFloatInteger);

            // Then the resulting Object should match the original passed in float value.
            assertEquals(expectedPBFieldFloat.toString(),actualPBFieldFloat.toString(), "The transformed float value does not match the expected.");
            assertTrue(expectedPBFieldFloat.equals(actualPBFieldFloat), "The transformed float object does not match that of the expected.");
        });
    }

}
