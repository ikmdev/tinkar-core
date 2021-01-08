package org.hl7.tinkar.lombok.dto;

import org.hl7.tinkar.component.*;
import org.hl7.tinkar.lombok.dto.digraph.DigraphDTO;

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
    CONCEPT_CHRONOLOGY((byte) 0, ConceptChronology.class, UUID.fromString("60965934-32a2-11eb-adc1-0242ac120002")),
    DEFINITION_FOR_SEMANTIC_CHRONOLOGY((byte) 1, DefinitionForSemanticChronology.class, UUID.fromString("6eaa968e-32a2-11eb-adc1-0242ac120002")),
    SEMANTIC_CHRONOLOGY((byte) 2, SemanticChronology.class, UUID.fromString("7a01ea5a-32a2-11eb-adc1-0242ac120002")),

    CONCEPT_VERSION((byte) 3, ConceptVersion.class, UUID.fromString("fd3bd442-4578-11eb-b378-0242ac130002")),
    DEFINITION_FOR_SEMANTIC_VERSION((byte) 4, DefinitionForSemanticVersion.class, UUID.fromString("044565be-4579-11eb-b378-0242ac130002")),
    SEMANTIC_VERSION((byte) 5, SemanticVersion.class, UUID.fromString("09a3328e-4579-11eb-b378-0242ac130002")),

    STAMP((byte) 6, Stamp.class, UUID.fromString("f37e9591-e3a1-419a-a674-e504ce58943b")),
    STRING((byte) 7, String.class, UUID.fromString("601135f2-2bad-11eb-adc1-0242ac120002")),
    INTEGER((byte) 8, Integer.class,  UUID.fromString("60113822-2bad-11eb-adc1-0242ac120002")),
    FLOAT((byte) 9, Float.class,   UUID.fromString("6011391c-2bad-11eb-adc1-0242ac120002")),
    BOOLEAN((byte) 10, Boolean.class, UUID.fromString("601139ee-2bad-11eb-adc1-0242ac120002")),
    BYTE_ARRAY((byte) 11, byte[].class, UUID.fromString("60113aac-2bad-11eb-adc1-0242ac120002")),
    OBJECT_ARRAY((byte) 12, Object[].class, UUID.fromString("60113b74-2bad-11eb-adc1-0242ac120002")),
    DIGRAPH((byte) 13, DigraphDTO.class, UUID.fromString("60113dfe-2bad-11eb-adc1-0242ac120002")),
    INSTANT((byte) 14, Instant.class, UUID.fromString("9cb1bd10-31b1-11eb-adc1-0242ac120002")),
    CONCEPT((byte) 15, Concept.class, UUID.fromString("6882871c-32a2-11eb-adc1-0242ac120002")),
    DEFINITION_FOR_SEMANTIC((byte) 16, DefinitionForSemantic.class, UUID.fromString("74af5952-32a2-11eb-adc1-0242ac120002")),
    SEMANTIC((byte) 17, Semantic.class, UUID.fromString("7f21bbfa-32a2-11eb-adc1-0242ac120002")),

    // Identified thing needs to go last...
    IDENTIFIED_THING(Byte.MAX_VALUE, ComponentDTO.class, UUID.fromString("60113d36-2bad-11eb-adc1-0242ac120002"));

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
            case 0: return CONCEPT_CHRONOLOGY;
            case 1: return DEFINITION_FOR_SEMANTIC_CHRONOLOGY;
            case 2: return SEMANTIC_CHRONOLOGY;
            case 3: return CONCEPT_VERSION;
            case 4: return DEFINITION_FOR_SEMANTIC_VERSION;
            case 5: return SEMANTIC_VERSION;
            case 6: return STAMP;
            case 7: return STRING;
            case 8: return INTEGER;
            case 9: return FLOAT;
            case 10: return BOOLEAN;
            case 11: return BYTE_ARRAY;
            case 12: return OBJECT_ARRAY;
            case 13: return DIGRAPH;
            case 14: return INSTANT;
            case 15: return CONCEPT;
            case 16: return DEFINITION_FOR_SEMANTIC;
            case 17: return SEMANTIC;
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
        throw new UnsupportedOperationException("getFieldDataType can't handle: " +
                obj.getClass().getSimpleName() + "\n" +  obj);
    }
}
