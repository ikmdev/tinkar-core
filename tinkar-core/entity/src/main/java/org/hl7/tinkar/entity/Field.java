package org.hl7.tinkar.entity;

import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.component.FieldDefinition;
import org.hl7.tinkar.terms.ConceptFacade;
import org.hl7.tinkar.terms.ConceptToDataType;
import org.hl7.tinkar.terms.EntityProxy;

public interface Field<T> extends FieldDefinition {

    T value();

    default FieldDataType fieldDataType() {
        return ConceptToDataType.convert(dataType());
    }

    default ConceptFacade dataType() {
        return EntityProxy.Concept.make(dataTypeNid());
    }

    default ConceptFacade purpose() {
        return EntityProxy.Concept.make(purposeNid());
    }

    default ConceptFacade meaning() {
        return EntityProxy.Concept.make(meaningNid());
    }

    int meaningNid();

    int purposeNid();

    int dataTypeNid();

}
