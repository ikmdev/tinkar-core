package org.hl7.tinkar.dto.graph;

import org.junit.jupiter.api.Test;

import static org.hl7.tinkar.dto.TestUtil.emptyVertex;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class GraphTest {
    @Test
    public void testGraphDto() {
        VertexDTO rootVertexDTO = emptyVertex();
        DiTreeDTO.Builder builder = DiTreeDTO.builder(rootVertexDTO);

        VertexDTO andSetsVertexDTO = emptyVertex();
        VertexDTO necessarySetVertexDTO = emptyVertex();
        VertexDTO andVertexDTO = emptyVertex();
        VertexDTO conceptVertexDTO = emptyVertex();

        builder.add(andSetsVertexDTO, rootVertexDTO)
               .add(necessarySetVertexDTO, andSetsVertexDTO)
               .add(andVertexDTO, necessarySetVertexDTO)
               .add(conceptVertexDTO, andVertexDTO);

        DiTreeDTO tree1 = builder.build();
        DiTreeDTO tree2 = builder.build();
        assertEquals(tree1, tree2);

        DiGraphDTO.Builder graphBuilder = DiGraphDTO.builder();
        graphBuilder.addRoot(rootVertexDTO);
        graphBuilder.add(andSetsVertexDTO, rootVertexDTO)
                .add(necessarySetVertexDTO, andSetsVertexDTO)
                .add(andVertexDTO, necessarySetVertexDTO)
                .add(conceptVertexDTO, andVertexDTO);
        DiGraphDTO graph1 = graphBuilder.build();
        DiGraphDTO graph2 = graphBuilder.build();
        assertEquals(graph1, graph2);





    }
}
