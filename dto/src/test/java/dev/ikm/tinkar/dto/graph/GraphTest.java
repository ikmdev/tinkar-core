package dev.ikm.tinkar.dto.graph;

import dev.ikm.tinkar.dto.TestUtil;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class GraphTest {
    @Test
    public void testGraphDto() {
        VertexDTO rootVertexDTO = TestUtil.emptyVertex();
        DiTreeDTO.Builder builder = DiTreeDTO.builder(rootVertexDTO);

        VertexDTO andSetsVertexDTO = TestUtil.emptyVertex();
        VertexDTO necessarySetVertexDTO = TestUtil.emptyVertex();
        VertexDTO andVertexDTO = TestUtil.emptyVertex();
        VertexDTO conceptVertexDTO = TestUtil.emptyVertex();

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
