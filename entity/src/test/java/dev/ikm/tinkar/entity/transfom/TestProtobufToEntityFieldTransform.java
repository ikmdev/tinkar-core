package dev.ikm.tinkar.entity.transfom;

import com.google.protobuf.ByteString;
import dev.ikm.tinkar.entity.Field;
import dev.ikm.tinkar.schema.PBField;
import dev.ikm.tinkar.schema.PBFieldDefinition;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import static dev.ikm.tinkar.entity.transfom.ProtobufToEntityTestHelper.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestProtobufToEntityFieldTransform {
    @Test
    @DisplayName("Transform a Field With a String Value Present")
    public void testProtobufToEntityFieldTransformWithAStringValue() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given a string value
            String expectedStringValue = "Testing Field Transformation with a string.";
            PBField pbFieldString = PBField.newBuilder()
                    .setStringValue(expectedStringValue)
                    .build();

            // When we transform our String Field value
            Object actualFieldString = ProtobufTransformer.getInstance().transformField(pbFieldString);

            // Then the resulting Object should match the original passed in string value.
            assertEquals(expectedStringValue,actualFieldString.toString(), "The transformed string value does not match the expected.");
            assertEquals(expectedStringValue.toUpperCase(),actualFieldString.toString().toUpperCase(), "The transformed string uppercase value does not match the expected.");
            assertEquals(expectedStringValue.toLowerCase(),actualFieldString.toString().toLowerCase(), "The transformed string lowercase value does not match the expected.");
        });
    }

    @Test
    @DisplayName("Transform a Field With a Boolean Value Present")
    public void testProtobufToEntityFieldTransformWithABoolValue() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given a boolean value
            Boolean expectedBoolValue = true;
            PBField pbFieldBool = PBField.newBuilder()
                    .setBoolValue(expectedBoolValue)
                    .build();

            // When we transform our Boolean Field value
            Object actualFieldBool = ProtobufTransformer.getInstance().transformField(pbFieldBool);

            // Then the resulting Object should match the original passed in boolean value.
            assertEquals(expectedBoolValue, actualFieldBool, "The transformed boolean value does not match the expected.");
        });
    }

    @Test
    @DisplayName("Transform a Field With a Integer Value Present")
    public void testProtobufToEntityFieldTransformWithAIntValue() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given a integer value
            Integer expectedIntValue = 1568;
            PBField pbFieldInt = PBField.newBuilder()
                    .setIntValue(expectedIntValue)
                    .build();

            // When we transform our Integer Field value
            Object actualFieldInt = ProtobufTransformer.getInstance().transformField(pbFieldInt);

            // Then the resulting Object should match the original passed in Integer value.
            assertEquals(expectedIntValue, actualFieldInt, "The transformed integer value does not match the expected.");
        });
    }

    @Test
    @DisplayName("Transform a Field With a Float Value Present")
    public void testProtobufToEntityFieldTransformWithAFloatValue() {
        openSession(this, (mockedEntityService, conceptMap) -> {
            // Given a float value
            Float expectedFloatValue = 1534.34f;
            PBField pbFieldFloat = PBField.newBuilder()
                    .setFloatValue(expectedFloatValue)
                    .build();

            // When we transform our Float Field value
            Object actualFieldFloat = ProtobufTransformer.getInstance().transformField(pbFieldFloat);

            // Then the resulting Object should match the original passed in float value.
            assertEquals(expectedFloatValue, actualFieldFloat, "The transformed float value does not match the expected.");
        });
    }
}
