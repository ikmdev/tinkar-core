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
package dev.ikm.tinkar.entity.transform;

import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.schema.Field;
import dev.ikm.tinkar.schema.FieldArray;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Round-trip coverage for the protobuf field forms the Data Type Defaults Pattern
 * exercises for the first time (IKE-Network/ike-issues#885): object arrays (the
 * {@link FieldArray} message, both directions) and the pre-inception instant sentinel
 * (historically "premundane"; it must map to {@link PrimitiveData#PRE_INCEPTION_TIME}
 * rather than overflow {@code Instant.toEpochMilli()}).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class TestFieldArrayAndSentinelTransform {

    @Test
    @DisplayName("Transform an Entity Field with an Object array to a FieldArray and back")
    public void testObjectArrayFieldRoundTrip() {
        // Given an object array field value (the Array data type's loud default shape).
        Object[] actualArray = new Object[]{"UNINITIALIZED"};

        // When we transform it to protobuf form.
        Field pbField = EntityToTinkarSchemaTransformer.getInstance().createPBField(actualArray);

        // Then the protobuf form carries the elements in order.
        assertEquals(Field.ValueCase.OBJECT_ARRAY, pbField.getValueCase(),
                "An Object[] field must transform to the FieldArray form.");
        assertEquals(1, pbField.getObjectArray().getValuesCount(),
                "The FieldArray must carry exactly the array's elements.");
        assertEquals("UNINITIALIZED", pbField.getObjectArray().getValues(0).getStringValue(),
                "The FieldArray element must carry the original string.");

        // And transforming back yields the original array.
        Object transformedBack = TinkarSchemaToEntityTransformer.getInstance().transformField(pbField);
        assertInstanceOf(Object[].class, transformedBack,
                "A FieldArray must transform back to an Object[].");
        assertArrayEquals(actualArray, (Object[]) transformedBack,
                "The round-tripped array must equal the original element for element.");
    }

    @Test
    @DisplayName("Transform a heterogeneous Object array field and back")
    public void testHeterogeneousObjectArrayFieldRoundTrip() {
        // Given a heterogeneous object array.
        Object[] actualArray = new Object[]{"first", Boolean.TRUE, 777_777_777};

        // When we round-trip it through the protobuf form.
        Field pbField = EntityToTinkarSchemaTransformer.getInstance().createPBField(actualArray);
        Object transformedBack = TinkarSchemaToEntityTransformer.getInstance().transformField(pbField);

        // Then every element survives, in order.
        assertArrayEquals(actualArray, (Object[]) transformedBack,
                "The round-tripped heterogeneous array must equal the original.");
    }

    @Test
    @DisplayName("Transform the pre-inception Instant sentinel to its epoch-ms sentinel and back")
    public void testPreInceptionInstantRoundTrip() {
        // Given the pre-inception instant (the Instant data type's loud default).
        Instant preInception = PrimitiveData.PRE_INCEPTION_INSTANT;

        // When we transform it to protobuf form — this used to overflow toEpochMilli().
        Field pbField = EntityToTinkarSchemaTransformer.getInstance().createPBField(preInception);

        // Then the wire value is the pre-inception time sentinel, not an overflow.
        assertEquals(PrimitiveData.PRE_INCEPTION_TIME, pbField.getTimeValue(),
                "The pre-inception instant must map to the pre-inception epoch-ms sentinel.");

        // And transforming back yields the pre-inception instant exactly.
        Object transformedBack = TinkarSchemaToEntityTransformer.getInstance().transformField(pbField);
        assertEquals(preInception, transformedBack,
                "The round-tripped pre-inception instant must equal the sentinel instant.");
    }
}
