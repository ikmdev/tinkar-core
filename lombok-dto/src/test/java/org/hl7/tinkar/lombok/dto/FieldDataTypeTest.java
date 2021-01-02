package org.hl7.tinkar.lombok.dto;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.net.URI;

public class FieldDataTypeTest {
    @Test
    public void testEnums() {

        for (FieldDataType fieldDataType: FieldDataType.values()) {
            Assertions.assertTrue(fieldDataType == FieldDataType.fromToken(fieldDataType.token));
        }
        Assertions.assertTrue(FieldDataType.FLOAT == FieldDataType.getFieldDataType(Double.parseDouble("1.0")));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> FieldDataType.fromToken(Byte.MIN_VALUE));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> FieldDataType.getFieldDataType(new URI("test")));
    }
}
