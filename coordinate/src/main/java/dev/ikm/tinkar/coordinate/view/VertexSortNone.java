package dev.ikm.tinkar.coordinate.view;


import dev.ikm.tinkar.common.binary.*;
import org.eclipse.collections.api.collection.ImmutableCollection;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import dev.ikm.tinkar.common.service.PrimitiveData;
import dev.ikm.tinkar.coordinate.language.calculator.LanguageCalculator;
import dev.ikm.tinkar.coordinate.navigation.calculator.Edge;
import dev.ikm.tinkar.coordinate.navigation.calculator.NavigationCalculator;

import java.util.UUID;

public class VertexSortNone implements VertexSort, Encodable {

    public static final VertexSortNone SINGLETON = new VertexSortNone();
    private static final UUID VERTEX_SORT_UUID = UUID.fromString("9e21329f-da07-4a15-8664-7a08ebdad987");

    private VertexSortNone() {
    }

    @Decoder
    public static VertexSortNone decode(DecoderInput in) {
        switch (Encodable.checkVersion(in)) {
            default:
                // Using a static method rather than a constructor eliminates the need for
                // a readResolve method, but allows the implementation to decide how
                // to handle special cases. This is the equivalent of readresolve, since it
                // returns an existing object always.
                return SINGLETON;
        }
    }

    @Override
    public UUID getVertexSortUUID() {
        return VERTEX_SORT_UUID;
    }

    @Override
    public String getVertexSortName() {
        return "No sort order";
    }

    @Override
    public String getVertexLabel(int vertexConceptNid, LanguageCalculator languageCalculator) {
        return languageCalculator.getDescriptionText(vertexConceptNid).orElse(PrimitiveData.text(vertexConceptNid));
    }

    @Override
    public int[] sortVertexes(int[] vertexConceptNids, NavigationCalculator navigationCalculator) {
        return vertexConceptNids;
    }

    @Override
    public ImmutableList<Edge> sortEdges(ImmutableCollection<Edge> edges, NavigationCalculator navigationCalculator) {
        return Lists.immutable.ofAll(edges);
    }

    @Override
    @Encoder
    public void encode(EncoderOutput out) {
        // No fieldValues...
    }
}
