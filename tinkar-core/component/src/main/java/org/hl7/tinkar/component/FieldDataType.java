package org.hl7.tinkar.component;

import org.eclipse.collections.impl.list.mutable.primitive.ByteArrayList;
import org.hl7.tinkar.common.id.IdList;
import org.hl7.tinkar.common.id.IdSet;
import org.hl7.tinkar.component.graph.DiGraph;
import org.hl7.tinkar.component.graph.DiTree;
import org.hl7.tinkar.component.graph.Vertex;
import org.hl7.tinkar.component.location.PlanarPoint;
import org.hl7.tinkar.component.location.SpatialPoint;

import java.time.Instant;

/**
 * Note that Double objects will be converted to Float objects by the serialization mechanisms.
 * <p>
 * The underlying intent is to keep the implementation simple by using the common types,
 * with precision dictated by domain of use, and that long and double are more granular than
 * typically required, and they waste more memory/bandwidth.
 * <p>
 * If there is compelling use for a more precise data type (such as Instant), they can be added when a
 * agreed business need and use case are identified..
 */
public enum FieldDataType {
    // Changing CONCEPT_CHRONOLOGY token to 1 so that reading a default 0 throws an error...
    CONCEPT_CHRONOLOGY((byte) 1, ConceptChronology.class),
    PATTERN_CHRONOLOGY((byte) 2, PatternChronology.class),
    SEMANTIC_CHRONOLOGY((byte) 3, SemanticChronology.class),

    CONCEPT_VERSION((byte) 4, ConceptVersion.class),
    PATTERN_VERSION((byte) 5, PatternVersion.class),
    SEMANTIC_VERSION((byte) 6, SemanticVersion.class),

    STAMP((byte) 7, Stamp.class),
    STRING((byte) 8, String.class),
    INTEGER((byte) 9, Integer.class),
    FLOAT((byte) 10, Float.class),
    BOOLEAN((byte) 11, Boolean.class),
    BYTE_ARRAY((byte) 12, byte[].class),
    OBJECT_ARRAY((byte) 13, Object[].class),
    DIGRAPH((byte) 14, DiGraph.class),
    INSTANT((byte) 15, Instant.class),
    CONCEPT((byte) 16, Concept.class),
    PATTERN((byte) 17, Pattern.class),
    SEMANTIC((byte) 18, Semantic.class),

    DITREE((byte) 19, DiTree.class),
    VERTEX((byte) 20, Vertex.class),
    COMPONENT_ID_LIST((byte) 21, IdList.class),
    COMPONENT_ID_SET((byte) 22, IdSet.class),
    PLANAR_POINT((byte) 23, PlanarPoint.class),
    SPATIAL_POINT((byte) 24, SpatialPoint.class),
    STAMP_VERSION((byte) 25, Stamp.class),

    FIELD_DEFINITION((byte) 26, FieldDefinition.class),

    // Identified thing needs to go last...
    IDENTIFIED_THING(Byte.MAX_VALUE, Component.class);


    public final byte token;
    public final Class<? extends Object> clazz;

    FieldDataType(byte token, Class<? extends Object> clazz) {
        this.token = token;
        this.clazz = clazz;
    }

    public static FieldDataType fromToken(byte token) {
        switch (token) {
            case 0:
                throw new IllegalStateException("Token 0 is not allowed");
            case 1:
                return CONCEPT_CHRONOLOGY;
            case 2:
                return PATTERN_CHRONOLOGY;
            case 3:
                return SEMANTIC_CHRONOLOGY;
            case 4:
                return CONCEPT_VERSION;
            case 5:
                return PATTERN_VERSION;
            case 6:
                return SEMANTIC_VERSION;
            case 7:
                return STAMP;
            case 8:
                return STRING;
            case 9:
                return INTEGER;
            case 10:
                return FLOAT;
            case 11:
                return BOOLEAN;
            case 12:
                return BYTE_ARRAY;
            case 13:
                return OBJECT_ARRAY;
            case 14:
                return DIGRAPH;
            case 15:
                return INSTANT;
            case 16:
                return CONCEPT;
            case 17:
                return PATTERN;
            case 18:
                return SEMANTIC;
            case 19:
                return DITREE;
            case 20:
                return VERTEX;
            case 21:
                return COMPONENT_ID_LIST;
            case 22:
                return COMPONENT_ID_SET;
            case 23:
                return PLANAR_POINT;
            case 24:
                return SPATIAL_POINT;
            case 25:
                return STAMP_VERSION;
            case 26:
                return FIELD_DEFINITION;

            // Identified thing needs to go last...
            case Byte.MAX_VALUE:
                return IDENTIFIED_THING;
            default:
                throw new UnsupportedOperationException("FieldDatatype.fromToken can't handle token: " +
                        token);
        }
    }

    public static FieldDataType getFieldDataType(Object obj) {
        for (FieldDataType fieldDataType : FieldDataType.values()) {
            if (fieldDataType.clazz.isAssignableFrom(obj.getClass())) {
                return fieldDataType;
            }
        }
        if (obj instanceof Double) {
            return FLOAT;
        }
        if (obj instanceof Long) {
            return INTEGER;
        }
        if (obj instanceof ByteArrayList) {
            return BYTE_ARRAY;
        }
        throw new UnsupportedOperationException("getFieldDataType can't handle: " +
                obj.getClass().getSimpleName() + "\n" + obj);

    }
}
