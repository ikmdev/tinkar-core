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
