package org.hl7.tinkar.lombok.dto.json.graph;

import org.eclipse.collections.api.factory.Maps;
import org.eclipse.collections.api.map.MutableMap;
import org.hl7.tinkar.lombok.dto.binary.Marshalable;
import org.hl7.tinkar.lombok.dto.graph.VertexDTO;
import org.hl7.tinkar.lombok.dto.ConceptDTO;
import org.hl7.tinkar.lombok.dto.json.TestUtil;
import org.junit.jupiter.api.Test;

import static org.hl7.tinkar.lombok.dto.json.TestUtil.emptyVertex;
import static org.hl7.tinkar.lombok.dto.json.TestUtil.vertexWithProperties;
import static org.junit.jupiter.api.Assertions.*;

public class VertexTest {

    @Test
    public void testVertexDto() {
        // Vertex no properties...
        VertexDTO vertexDTO = emptyVertex();
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
        VertexDTO vertexWithConceptPropertyDTO = vertexWithProperties(propertyMap);

        VertexDTO newerComponentWithConceptProperty = Marshalable.make(VertexDTO.class, vertexWithConceptPropertyDTO.marshal());
        assertEquals(vertexWithConceptPropertyDTO, newerComponentWithConceptProperty);

    }
}
