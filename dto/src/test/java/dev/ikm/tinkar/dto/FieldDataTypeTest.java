/*
 * Copyright © 2015 Integrated Knowledge Management (support@ikm.dev)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.ikm.tinkar.dto;
import dev.ikm.tinkar.component.FieldDataType;
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
