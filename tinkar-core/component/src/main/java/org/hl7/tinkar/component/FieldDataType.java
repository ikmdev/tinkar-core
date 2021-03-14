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
import java.util.UUID;

/**
 * Note that Double objects will be converted to Float objects by the serialization mechanisms.
 *
 * The underlying intent is to keep the implementation simple by using the common types,
 * with precision dictated by domain of use, and that long and double are more granular than
 * typically required, and they waste more memory/bandwidth.
 *
 * If there is compelling use for a more precise data type (such as Instant), they can be added when a
 * agreed business need and use case are identified..
 */
public enum FieldDataType {
    // Changing CONCEPT_CHRONOLOGY token to 1 so that reading a default 0 throws an error...
    CONCEPT_CHRONOLOGY((byte) 1, ConceptChronology.class, UUID.fromString("60965934-32a2-11eb-adc1-0242ac120002")),
    PATTERN_FOR_SEMANTIC_CHRONOLOGY((byte) 2, TypePatternForSemanticChronology.class, UUID.fromString("6eaa968e-32a2-11eb-adc1-0242ac120002")),
    SEMANTIC_CHRONOLOGY((byte) 3, SemanticChronology.class, UUID.fromString("7a01ea5a-32a2-11eb-adc1-0242ac120002")),

    CONCEPT_VERSION((byte) 4, ConceptVersion.class, UUID.fromString("fd3bd442-4578-11eb-b378-0242ac130002")),
    PATTERN_FOR_SEMANTIC_VERSION((byte) 5, TypePatternForSemanticVersion.class, UUID.fromString("044565be-4579-11eb-b378-0242ac130002")),
    SEMANTIC_VERSION((byte) 6, SemanticVersion.class, UUID.fromString("09a3328e-4579-11eb-b378-0242ac130002")),

    STAMP((byte) 7, Stamp.class, UUID.fromString("f37e9591-e3a1-419a-a674-e504ce58943b")),
    STRING((byte) 8, String.class, UUID.fromString("601135f2-2bad-11eb-adc1-0242ac120002")),
    INTEGER((byte) 9, Integer.class,  UUID.fromString("60113822-2bad-11eb-adc1-0242ac120002")),
    FLOAT((byte) 10, Float.class,   UUID.fromString("6011391c-2bad-11eb-adc1-0242ac120002")),
    BOOLEAN((byte) 11, Boolean.class, UUID.fromString("601139ee-2bad-11eb-adc1-0242ac120002")),
    BYTE_ARRAY((byte) 12, byte[].class, UUID.fromString("60113aac-2bad-11eb-adc1-0242ac120002")),
    OBJECT_ARRAY((byte) 13, Object[].class, UUID.fromString("60113b74-2bad-11eb-adc1-0242ac120002")),
    DIGRAPH((byte) 14, DiGraph.class, UUID.fromString("60113dfe-2bad-11eb-adc1-0242ac120002")),
    INSTANT((byte) 15, Instant.class, UUID.fromString("9cb1bd10-31b1-11eb-adc1-0242ac120002")),
    CONCEPT((byte) 16, Concept.class, UUID.fromString("6882871c-32a2-11eb-adc1-0242ac120002")),
    PATTERN_FOR_SEMANTIC((byte) 17, TypePatternForSemantic.class, UUID.fromString("74af5952-32a2-11eb-adc1-0242ac120002")),
    SEMANTIC((byte) 18, Semantic.class, UUID.fromString("7f21bbfa-32a2-11eb-adc1-0242ac120002")),

    DITREE((byte) 19, DiTree.class, UUID.fromString("32f64fc6-5371-11eb-ae93-0242ac130002")),
    VERTEX((byte) 20, Vertex.class, UUID.fromString("3e56c6b6-5371-11eb-ae93-0242ac130002")),
    COMPONENT_ID_LIST((byte) 21, IdList.class, UUID.fromString("e553d3f1-63e1-4292-a3a9-af646fe44292")),
    COMPONENT_ID_SET((byte) 22, IdSet.class, UUID.fromString("e283af51-2e8f-44fa-9bf1-89a99a7c7631")),
    PLANAR_POINT((byte) 23, PlanarPoint.class, UUID.fromString("c14762fe-6d5e-11eb-9439-0242ac130002")),
    SPATIAL_POINT((byte) 24, SpatialPoint.class, UUID.fromString("c965aab8-6d5e-11eb-9439-0242ac130002")),


    // Identified thing needs to go last...
    IDENTIFIED_THING(Byte.MAX_VALUE, Component.class, UUID.fromString("60113d36-2bad-11eb-adc1-0242ac120002"));

    public final byte token;
    public final Class<? extends Object> clazz;
    public final UUID conceptUuid;

    FieldDataType(byte token, Class<? extends Object> clazz, UUID conceptUuid) {
        this.token = token;
        this.clazz = clazz;
        this.conceptUuid = conceptUuid;
    }

    public static FieldDataType fromToken(byte token) {
        switch (token) {
            case 0: throw new IllegalStateException("Token 0 is not allowed");
            case 1: return CONCEPT_CHRONOLOGY;
            case 2: return PATTERN_FOR_SEMANTIC_CHRONOLOGY;
            case 3: return SEMANTIC_CHRONOLOGY;
            case 4: return CONCEPT_VERSION;
            case 5: return PATTERN_FOR_SEMANTIC_VERSION;
            case 6: return SEMANTIC_VERSION;
            case 7: return STAMP;
            case 8: return STRING;
            case 9: return INTEGER;
            case 10: return FLOAT;
            case 11: return BOOLEAN;
            case 12: return BYTE_ARRAY;
            case 13: return OBJECT_ARRAY;
            case 14: return DIGRAPH;
            case 15: return INSTANT;
            case 16: return CONCEPT;
            case 17: return PATTERN_FOR_SEMANTIC;
            case 18: return SEMANTIC;
            case 19: return DITREE;
            case 20: return VERTEX;
            case 21: return COMPONENT_ID_LIST;
            case 22: return COMPONENT_ID_SET;
            case 23: return PLANAR_POINT;
            case 24: return SPATIAL_POINT;

            // Identified thing needs to go last...
            case Byte.MAX_VALUE: return IDENTIFIED_THING;
            default:
                throw new UnsupportedOperationException("FieldDatatype.fromToken can't handle token: " +
                        token);
        }
    }

    public static FieldDataType getFieldDataType(Object obj) {
        for (FieldDataType  fieldDataType: FieldDataType.values()) {
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
                obj.getClass().getSimpleName() + "\n" +  obj);
    }
}
