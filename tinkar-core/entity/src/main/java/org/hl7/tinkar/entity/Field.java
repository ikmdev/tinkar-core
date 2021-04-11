package org.hl7.tinkar.entity;

import org.hl7.tinkar.terms.ConceptFacade;
import org.hl7.tinkar.terms.ConceptProxy;

public interface Field<T>  {

    default ConceptFacade purpose() {
        return ConceptProxy.make(purposeNid());
    }

    int purposeNid();

    default ConceptFacade meaning() {
        return ConceptProxy.make(meaningNid());
    }

    int meaningNid();

    T value();
}
