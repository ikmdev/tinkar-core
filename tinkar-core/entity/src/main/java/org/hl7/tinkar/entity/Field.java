package org.hl7.tinkar.entity;

import org.hl7.tinkar.component.FieldDataType;
import org.hl7.tinkar.terms.ConceptFacade;
import org.hl7.tinkar.terms.EntityProxy;

public interface Field<T> {

    default ConceptFacade purpose() {
        return EntityProxy.Concept.make(purposeNid());
    }

    int purposeNid();

    default ConceptFacade meaning() {
        return EntityProxy.Concept.make(meaningNid());
    }

    int meaningNid();

    T value();

    FieldDataType fieldDataType();
    
    SemanticEntityVersion enclosingSemanticVersion();
}
