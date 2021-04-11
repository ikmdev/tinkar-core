package org.hl7.tinkar.coordinate.view;


import org.hl7.tinkar.common.binary.*;
import org.hl7.tinkar.common.service.PrimitiveData;
import org.hl7.tinkar.coordinate.manifold.ManifoldCoordinateImmutable;
import org.hl7.tinkar.coordinate.language.LanguageCoordinate;
import org.hl7.tinkar.coordinate.stamp.StampFilter;

import java.util.UUID;

public class VertexSortNone implements VertexSort, Encodable {

    private static final int marshalVersion = 1;

    private static final UUID VERTEX_SORT_UUID = UUID.fromString("9e21329f-da07-4a15-8664-7a08ebdad987");

    public static final VertexSortNone SINGLETON = new VertexSortNone();

    private VertexSortNone() {
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
    public String getVertexLabel(int vertexConceptNid, LanguageCoordinate languageCoordinate, StampFilter stampFilter) {
        return languageCoordinate.getDescriptionText(vertexConceptNid, stampFilter).orElse(PrimitiveData.text(vertexConceptNid));
    }

    @Override
    public int[] sortVertexes(int[] vertexConceptNids, View view) {
        return vertexConceptNids;
    }

    @Decoder
    public static VertexSortNone decode(DecoderInput in) {
        int objectMarshalVersion = in.encodingFormatVersion();
        switch (objectMarshalVersion) {
            case marshalVersion:
                // Using a static method rather than a constructor eliminates the need for
                // a readResolve method, but allows the implementation to decide how
                // to handle special cases. This is the equivalent of readresolve, since it
                // returns an existing object always.
                return SINGLETON;
            default:
                throw new UnsupportedOperationException("Unsupported version: " + objectMarshalVersion);
        }
    }

    @Override
    @Encoder
    public void encode(EncoderOutput out) {
        // No fields...
    }
}
