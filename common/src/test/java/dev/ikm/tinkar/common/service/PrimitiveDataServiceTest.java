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
package dev.ikm.tinkar.common.service;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;

import static dev.ikm.tinkar.common.service.PrimitiveDataService.STAMP_DATA_TYPE;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class PrimitiveDataServiceTest {

    @Test
    @Order(1)
    /*
    Test comparing CONSTANT value
     */
    public void testStampDataType() {
        byte actual_stamp_data_type = 7;

        assertEquals(STAMP_DATA_TYPE, actual_stamp_data_type);
    }

    @Test
    @Order(2)
    /*
    Test comparing CONSTANT value
     */
    public void testStampDataTypeFail() {
        byte actual_stamp_data_type = 6;

        assertNotEquals(STAMP_DATA_TYPE, actual_stamp_data_type);
    }

}
