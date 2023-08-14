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
package dev.ikm.tinkar.dto.graph;

import dev.ikm.tinkar.dto.ConceptDTO;
import dev.ikm.tinkar.dto.TestUtil;
import dev.ikm.tinkar.dto.binary.Marshalable;
import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class VertexTest {

    @Test
    public void testVertexDto() {
        // Vertex no properties...
        VertexDTO vertexDTO = TestUtil.emptyVertex();
        assertEquals(vertexDTO, vertexDTO);
        assertNotEquals(vertexDTO, "String");

        VertexDTO newerComponent = Marshalable.make(VertexDTO.class, vertexDTO.marshal());
        assertEquals(vertexDTO, newerComponent);

        // Vertex with concept properties (similar to a role type...)
        MutableMap<ConceptDTO, Object> propertyMap = Maps.mutable.empty();
        propertyMap.put(VertexDTO.abstractObject(TestUtil.makeConceptChronology()),
                VertexDTO.abstractObject(TestUtil.makeConceptChronology()));
        propertyMap.put(VertexDTO.abstractObject(TestUtil.makeConceptChronology()),
                "Test String");

        ConceptDTO aKey = VertexDTO.abstractObject(TestUtil.makeConceptChronology());
        propertyMap.put(aKey,
                1);
        propertyMap.put(VertexDTO.abstractObject(TestUtil.makeConceptChronology()),
                1.1f);
        propertyMap.put(VertexDTO.abstractObject(TestUtil.makeConceptChronology()),
                new byte[] {1,2,3});
        VertexDTO vertexWithConceptPropertyDTO = TestUtil.vertexWithProperties(propertyMap);

        VertexDTO newerComponentWithConceptProperty = Marshalable.make(VertexDTO.class, vertexWithConceptPropertyDTO.marshal());
        assertEquals(vertexWithConceptPropertyDTO, newerComponentWithConceptProperty);

    }
}
