package org.hl7.tinkar.json;
import org.hl7.tinkar.dto.FieldDataType;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;

public class FieldDataTypeTest {
    @Test
    public void testEnums() {
        Assertions.assertTrue(FieldDataType.STRING == FieldDataType.fromToken(FieldDataType.STRING.token));
        Assertions.assertTrue(FieldDataType.BOOLEAN == FieldDataType.fromToken(FieldDataType.BOOLEAN.token));
        Assertions.assertTrue(FieldDataType.INTEGER == FieldDataType.fromToken(FieldDataType.INTEGER.token));
        Assertions.assertTrue(FieldDataType.BYTE_ARRAY == FieldDataType.fromToken(FieldDataType.BYTE_ARRAY.token));
        Assertions.assertTrue(FieldDataType.DIGRAPH == FieldDataType.fromToken(FieldDataType.DIGRAPH.token));
        Assertions.assertTrue(FieldDataType.FLOAT == FieldDataType.fromToken(FieldDataType.FLOAT.token));
        Assertions.assertTrue(FieldDataType.IDENTIFIED_THING == FieldDataType.fromToken(FieldDataType.IDENTIFIED_THING.token));
        Assertions.assertTrue(FieldDataType.OBJECT_ARRAY == FieldDataType.fromToken(FieldDataType.OBJECT_ARRAY.token));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> FieldDataType.fromToken((byte) 255));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> FieldDataType.getFieldDataType(new URI("test")));
    }
}
