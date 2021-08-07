package org.hl7.tinkar.terms;

import org.hl7.tinkar.component.FieldDataType;

public class ConceptToDataType {
    public static FieldDataType convert(ConceptFacade dataTypeConcept) {
        if (TinkarTerm.STRING.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.STRING;
        }
        if (TinkarTerm.COMPONENT_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.IDENTIFIED_THING;
        }
        if (TinkarTerm.COMPONENT_ID_SET_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.COMPONENT_ID_SET;
        }
        if (TinkarTerm.COMPONENT_ID_LIST_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.COMPONENT_ID_LIST;
        }
        if (TinkarTerm.DITREE_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.DITREE;
        }
        if (TinkarTerm.DIGRAPH_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.DIGRAPH;
        }
        if (TinkarTerm.CONCEPT_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.CONCEPT;
        }
        if (TinkarTerm.SEMANTIC_FIELD_TYPE.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.SEMANTIC;
        }
        if (TinkarTerm.INTEGER_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.INTEGER;
        }
        if (TinkarTerm.FLOAT_FIELD.publicId().equals(dataTypeConcept.publicId())) {
            return FieldDataType.FLOAT;
        }
        throw new UnsupportedOperationException("Can't handle: " + dataTypeConcept);
    }
}
